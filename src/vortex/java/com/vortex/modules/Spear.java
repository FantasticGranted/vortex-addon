package com.vortex.modules;

import com.vortex.Vortex;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class Spear extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General

    private final Setting<Boolean> packetAim = sgGeneral.add(new BoolSetting.Builder()
        .name("packet-aim")
        .description("Sends rotation packets directly to the server instead of setting client-side angles.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> seek = sgGeneral.add(new BoolSetting.Builder()
        .name("seek-target")
        .description("Waits until you have crosshair on a target to lock on.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyItem = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-holding")
        .description("Only aimbot if one of the selected items is in your main hand.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("Items that activate aimbot when held.")
        .visible(onlyItem::get)
        .build()
    );

    // Targeting

    private final Setting<Boolean> targetPlayersOnly = sgTargeting.add(new BoolSetting.Builder()
        .name("target-players-only")
        .description("Only target player entities. False targets all living entities.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to target.")
        .onlyAttackable()
        .defaultValue(EntityType.PLAYER)
        .visible(() -> !targetPlayersOnly.get())
        .build()
    );

    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("range")
        .description("The maximum range to target entities.")
        .defaultValue(256)
        .min(0)
        .sliderMax(512)
        .build()
    );

    private final Setting<Boolean> predict = sgTargeting.add(new BoolSetting.Builder()
        .name("predict-movement")
        .description("Leads moving targets by velocity over ping; lead shrinks while they turn.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> predictTicks = sgTargeting.add(new IntSetting.Builder()
        .name("prediction-ticks")
        .description("Extra ticks of lead on top of ping compensation.")
        .defaultValue(2)
        .min(0)
        .max(10)
        .visible(predict::get)
        .build()
    );

    private final Setting<Boolean> ignoreNakeds = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-nakeds")
        .description("Ignores players with no armor or items.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> throughWalls = sgTargeting.add(new BoolSetting.Builder()
        .name("through-walls")
        .description("Keep targets without line of sight.")
        .defaultValue(false)
        .build()
    );

    // Render

    private final Setting<Boolean> renderTarget = sgRender.add(new BoolSetting.Builder()
        .name("render-target")
        .description("Renders a box around the target.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the box is rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(renderTarget::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(0, 255, 220, 35))
        .visible(() -> renderTarget.get() && shapeMode.get().sides())
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(0, 255, 220))
        .visible(() -> renderTarget.get() && shapeMode.get().lines())
        .build()
    );

    private Entity crosshairTarget;
    private double velX;
    private double velY;
    private double velZ;
    private double prevVelX;
    private double prevVelZ;
    private double velCos = 1.0;
    private boolean velInit;
    private int stableFor;
    private Entity trackedEntity;

    public Spear() {
        super(Vortex.CATEGORY, "spear", "Locks onto the targeted entity while the module is on.");
    }

    @Override
    public void onActivate() {
        crosshairTarget = findTarget();
    }

    @Override
    public void onDeactivate() {
        crosshairTarget = null;
    }

    @EventHandler
    private void onTick(meteordevelopment.meteorclient.events.world.TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.isDeadOrDying()) { crosshairTarget = null; return; }
        if (mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return;

        if (onlyItem.get() && !items.get().contains(mc.player.getMainHandItem().getItem())) return;

        if (seek.get() && crosshairTarget == null) crosshairTarget = findTarget();
        if (crosshairTarget != null && !crosshairTarget.isAlive()) crosshairTarget = null;
        if (crosshairTarget == null || !(crosshairTarget instanceof LivingEntity)) {
            trackedEntity = null;
            return;
        }
        if (targetPlayersOnly.get() && !(crosshairTarget instanceof Player)) return;

        trackVelocity(crosshairTarget);
        Vec3 playerPos = mc.player.getEyePosition();
        Vec3 targetPos = crosshairTarget.getBoundingBox().getCenter();
        if (predict.get()) {
            double lead = ((double)predictTicks.get() + latencyTicks()) * leadMul();
            targetPos = targetPos.add(velX * lead, velY * lead, velZ * lead);
        }
        Vec3 toTarget = targetPos.subtract(playerPos).normalize();

        float yaw = (float) (Math.toDegrees(Math.atan2(toTarget.z, toTarget.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.asin(toTarget.y));

        if (packetAim.get()) {
            mc.getConnection().getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision));
        } else {
            mc.player.setYRot(yaw);
            mc.player.setYHeadRot(yaw);
            mc.player.setXRot(pitch);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!renderTarget.get() || crosshairTarget == null) return;
        AABB box = crosshairTarget.getBoundingBox();
        event.renderer.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    private double latencyTicks() {
        if (mc.getConnection() == null || mc.player == null) return 2.0;
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info != null ? info.getLatency() / 50.0 : 2.0;
    }

    private void trackVelocity(Entity e) {
        double dx;
        double dy;
        double dz;
        double m1;
        double m2;
        if (trackedEntity != e) {
            trackedEntity = e;
            velInit = false;
        }
        dx = e.getX() - e.xOld;
        dy = e.getY() - e.yOld;
        dz = e.getZ() - e.zOld;
        if (!velInit) {
            velX = dx;
            velY = dy;
            velZ = dz;
            prevVelX = dx;
            prevVelZ = dz;
            velCos = 1.0;
            velInit = true;
            stableFor = 5;
            return;
        }
        prevVelX = velX;
        prevVelZ = velZ;
        velX = (velX + dx) * 0.5;
        velY = (velY + dy) * 0.5;
        velZ = (velZ + dz) * 0.5;
        m1 = Math.sqrt(prevVelX * prevVelX + prevVelZ * prevVelZ);
        m2 = Math.sqrt(dx * dx + dz * dz);
        if (m1 > 0.05 && m2 > 0.05) {
            velCos = (prevVelX * dx + prevVelZ * dz) / (m1 * m2);
            if (velCos > 0.96) {
                ++stableFor;
            } else if (velCos > 0.5) {
                if (stableFor > 1) {
                    stableFor = 1;
                }
            } else {
                stableFor = 0;
            }
        } else {
            velCos = 1.0;
            if (stableFor < 5) {
                ++stableFor;
            }
        }
    }

    private double leadMul() {
        return velCos > 0.96 ? 1.0 : (velCos > 0.5 ? 0.6 : 0.35);
    }

    private Entity findTarget() {
        if (mc.player == null || mc.level == null) return null;

        double maxRange = range.get();
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookVec = mc.player.getViewVector(1.0f);

        HitResult blockHit = mc.level.clip(new ClipContext(eyePos,
            eyePos.add(lookVec.scale(maxRange)), ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, mc.player));
        double rayLength = blockHit.getType() == HitResult.Type.MISS || throughWalls.get() ? maxRange :
            eyePos.distanceTo(blockHit.getLocation());

        List<Entity> candidates = mc.level.getEntities(mc.player,
            mc.player.getBoundingBox().expandTowards(lookVec.scale(rayLength)),
            e -> e instanceof LivingEntity && e.isAlive() && e != mc.player);

        candidates.sort(Comparator.comparingDouble(e ->
            eyePos.distanceToSqr(e.getBoundingBox().getCenter())));

        double coneAngle = 0.999;
        for (Entity e : candidates) {
            double dist = eyePos.distanceTo(e.getBoundingBox().getCenter());
            if (dist > maxRange) break;

            if (targetPlayersOnly.get() && !(e instanceof Player)) continue;
            if (!targetPlayersOnly.get() && !entities.get().contains(e.getType())) continue;

            if (e instanceof Player player) {
                if (player.getAbilities().instabuild) continue;
                if (!Friends.get().shouldAttack(player)) continue;
                if (ignoreNakeds.get()
                    && player.getOffhandItem().isEmpty()
                    && player.getMainHandItem().isEmpty()
                    && player.getItemBySlot(EquipmentSlot.FEET).isEmpty()
                    && player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                    && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                    && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) continue;
            }

            if (!throughWalls.get() && !canSeeTarget(e)) continue;

            Vec3 toEntity = e.getBoundingBox().getCenter().subtract(eyePos).normalize();
            if (lookVec.dot(toEntity) > coneAngle) {
                return e;
            }
        }
        return null;
    }

    private boolean canSeeTarget(Entity target) {
        if (mc.player == null || mc.level == null) return false;

        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 targetCenter = target.getBoundingBox().getCenter();

        HitResult result = mc.level.clip(new ClipContext(
            eyePos,
            targetCenter,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            mc.player
        ));

        if (result.getType() == HitResult.Type.MISS) return true;
        return eyePos.distanceTo(result.getLocation()) >= eyePos.distanceTo(targetCenter) - 0.5;
    }

    @Override
    public String getInfoString() {
        if (crosshairTarget != null) {
            String name = crosshairTarget instanceof Player ? crosshairTarget.getName().getString() : crosshairTarget.getType().toShortString();
            return name;
        }
        return null;
    }

    public Entity getTarget() {
        return crosshairTarget;
    }

    public boolean hasTarget() {
        return crosshairTarget != null;
    }
}
