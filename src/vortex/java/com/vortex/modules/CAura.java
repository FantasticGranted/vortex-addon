package com.vortex.modules;

import com.vortex.Vortex;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSwitch = settings.createGroup("Switch");
    private final SettingGroup sgPlace = settings.createGroup("Place");
    private final SettingGroup sgBreak = settings.createGroup("Break");
    private final SettingGroup sgPause = settings.createGroup("Pause");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Range in which to target players.")
        .defaultValue(10)
        .min(0)
        .sliderMax(16)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to attack.")
        .onlyAttackable()
        .defaultValue(EntityType.PLAYER)
        .build()
    );

    private final Setting<Boolean> ignoreNakeds = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-nakeds")
        .description("Ignore players with no items.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> predictMovement = sgGeneral.add(new BoolSetting.Builder()
        .name("predict-movement")
        .description("Predicts target movement.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> minDamage = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-damage")
        .description("Minimum damage the crystal needs to deal to your target.")
        .defaultValue(6)
        .min(0)
        .build()
    );

    private final Setting<Double> maxDamage = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-damage")
        .description("Maximum damage crystals can deal to yourself.")
        .defaultValue(6)
        .range(0, 36)
        .sliderMax(36)
        .build()
    );

    private final Setting<Boolean> antiSuicide = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-suicide")
        .description("Will not place and break crystals if they will kill you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates server-side towards the crystals being hit/placed.")
        .defaultValue(true)
        .build()
    );

    // Switch

    private final Setting<AutoSwitchMode> autoSwitch = sgSwitch.add(new EnumSetting.Builder<AutoSwitchMode>()
        .name("auto-switch")
        .description("Switches to crystals in your hotbar once a target is found.")
        .defaultValue(AutoSwitchMode.Normal)
        .build()
    );

    private final Setting<Integer> switchDelay = sgSwitch.add(new IntSetting.Builder()
        .name("switch-delay")
        .description("The delay in ticks to wait to break a crystal after switching hotbar slot.")
        .defaultValue(0)
        .min(0)
        .build()
    );

    private final Setting<Boolean> noGapSwitch = sgSwitch.add(new BoolSetting.Builder()
        .name("no-gap-switch")
        .description("Won't auto switch if you're holding a gapple.")
        .defaultValue(true)
        .visible(() -> autoSwitch.get() == AutoSwitchMode.Normal)
        .build()
    );

    private final Setting<Boolean> antiWeakness = sgSwitch.add(new BoolSetting.Builder()
        .name("anti-weakness")
        .description("Switches to tools with so you can break crystals with the weakness effect.")
        .defaultValue(true)
        .build()
    );

    // Place

    private final Setting<Boolean> doPlace = sgPlace.add(new BoolSetting.Builder()
        .name("place")
        .description("If the CA should place crystals.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> placeDelay = sgPlace.add(new IntSetting.Builder()
        .name("place-delay")
        .description("The delay in ticks to wait to place a crystal after it's exploded.")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Double> placeRange = sgPlace.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("Range in which to place crystals.")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> placeWallsRange = sgPlace.add(new DoubleSetting.Builder()
        .name("walls-range")
        .description("Range in which to place crystals when behind blocks.")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    // Break

    private final Setting<Boolean> doBreak = sgBreak.add(new BoolSetting.Builder()
        .name("break")
        .description("If the CA should break crystals.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> breakDelay = sgBreak.add(new IntSetting.Builder()
        .name("break-delay")
        .description("The delay in ticks to wait to break a crystal after it's placed.")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> smartDelay = sgBreak.add(new BoolSetting.Builder()
        .name("smart-delay")
        .description("Only breaks crystals when the target can receive damage.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> breakRange = sgBreak.add(new DoubleSetting.Builder()
        .name("break-range")
        .description("Range in which to break crystals.")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> breakWallsRange = sgBreak.add(new DoubleSetting.Builder()
        .name("walls-range")
        .description("Range in which to break crystals when behind blocks.")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> onlyBreakOwn = sgBreak.add(new BoolSetting.Builder()
        .name("only-own")
        .description("Only breaks own crystals.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> breakAttempts = sgBreak.add(new IntSetting.Builder()
        .name("break-attempts")
        .description("How many times to hit a crystal before stopping to target it.")
        .defaultValue(2)
        .sliderMin(1)
        .sliderMax(5)
        .build()
    );

    private final Setting<Integer> ticksExisted = sgBreak.add(new IntSetting.Builder()
        .name("ticks-existed")
        .description("Amount of ticks a crystal needs to have lived for it to be attacked by CAura.")
        .defaultValue(0)
        .min(0)
        .build()
    );

    private final Setting<Integer> attackFrequency = sgBreak.add(new IntSetting.Builder()
        .name("attack-frequency")
        .description("Maximum hits to do per second.")
        .defaultValue(25)
        .min(1)
        .sliderRange(1, 30)
        .build()
    );

    private final Setting<Boolean> fastBreak = sgBreak.add(new BoolSetting.Builder()
        .name("fast-break")
        .description("Ignores break delay and tries to break the crystal as soon as it's spawned in the world.")
        .defaultValue(true)
        .build()
    );

    // Pause

    private final Setting<Boolean> pauseOnLag = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-lag")
        .description("Whether to pause if the server is not responding.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> pauseHealth = sgPause.add(new DoubleSetting.Builder()
        .name("pause-health")
        .description("Pauses when you go below a certain health.")
        .defaultValue(5)
        .range(0, 36)
        .sliderRange(0, 36)
        .build()
    );

    // Render

    private final Setting<SwingMode> swingMode = sgRender.add(new EnumSetting.Builder<SwingMode>()
        .name("swing-mode")
        .description("How to swing when placing.")
        .defaultValue(SwingMode.Both)
        .build()
    );

    private final Setting<Boolean> renderPlace = sgRender.add(new BoolSetting.Builder()
        .name("render-place")
        .description("Renders a block overlay over the block the crystals are being placed on.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> placeRenderTime = sgRender.add(new IntSetting.Builder()
        .name("place-time")
        .description("How long to render placements.")
        .defaultValue(10)
        .min(0)
        .sliderMax(20)
        .visible(renderPlace::get)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The side color of the block overlay.")
        .defaultValue(new SettingColor(255, 0, 220, 45))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The line color of the block overlay.")
        .defaultValue(new SettingColor(255, 0, 220))
        .build()
    );

    // Fields

    private final List<LivingEntity> targets = new ArrayList<>();
    private final Set<Integer> placedCrystals = new HashSet<>();

    private Item mainItem, offItem;
    private int breakTimer, placeTimer, switchTimer, ticksPassed, attacks;
    private boolean placing;
    private final BlockPos.MutableBlockPos placingCrystalBlockPos = new BlockPos.MutableBlockPos();

    private final Set<Integer> removed = new HashSet<>();
    private final Set<Integer> attemptedBreaks = new HashSet<>();

    private int placeRenderTimer;
    private final BlockPos.MutableBlockPos placeRenderPos = new BlockPos.MutableBlockPos();

    public CAura() {
        super(Vortex.CATEGORY, "c-aura", "Insane crystal aura - automatically places and attacks crystals.");
    }

    @Override
    public void onActivate() {
        breakTimer = 0;
        placeTimer = 0;
        ticksPassed = 0;
        switchTimer = 0;
        attacks = 0;
        placing = false;
        placeRenderTimer = 0;
        targets.clear();
        placedCrystals.clear();
        removed.clear();
        attemptedBreaks.clear();
    }

    @Override
    public void onDeactivate() {
        targets.clear();
        placedCrystals.clear();
        removed.clear();
        attemptedBreaks.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player.isUsingItem()) return;

        if (breakTimer > 0) breakTimer--;
        if (placeTimer > 0) placeTimer--;
        if (switchTimer > 0) switchTimer--;
        if (placeRenderTimer > 0) placeRenderTimer--;

        if (ticksPassed < 20) ticksPassed++;
        else {
            ticksPassed = 0;
            attacks = 0;
        }

        mainItem = mc.player.getMainHandItem().getItem();
        offItem = mc.player.getOffhandItem().getItem();

        findTargets();
        if (targets.isEmpty()) return;

        if (shouldPause()) return;

        if (doBreak.get()) doBreak();
        if (doPlace.get()) doPlace();
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!(event.entity instanceof EndCrystal)) return;

        if (placing && event.entity.blockPosition().equals(placingCrystalBlockPos)) {
            placing = false;
            placedCrystals.add(event.entity.getId());
        }

        if (fastBreak.get() && attacks < attackFrequency.get() && !shouldPause()) {
            float damage = getBreakDamage(event.entity, true);
            if (damage > minDamage.get()) doBreak(event.entity);
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundSetCarriedItemPacket) switchTimer = switchDelay.get();
    }

    // Break

    private void doBreak() {
        if (breakTimer > 0 || switchTimer > 0 || attacks >= attackFrequency.get()) return;

        float bestDamage = 0;
        Entity crystal = null;

        for (Entity entity : mc.level.entitiesForRendering()) {
            float damage = getBreakDamage(entity, true);
            if (damage > bestDamage) {
                bestDamage = damage;
                crystal = entity;
            }
        }

        if (crystal != null) doBreak(crystal);
    }

    private float getBreakDamage(Entity entity, boolean checkCrystalAge) {
        if (!(entity instanceof EndCrystal)) return 0;

        if (onlyBreakOwn.get() && !placedCrystals.contains(entity.getId())) return 0;
        if (removed.contains(entity.getId())) return 0;
        if (attemptedBreaks.contains(entity.getId())) return 0;
        if (checkCrystalAge && entity.tickCount < ticksExisted.get()) return 0;
        if (isOutOfRange(entity, false)) return 0;

        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        blockPos.set(entity.blockPosition()).move(0, -1, 0);
        float selfDamage = DamageUtils.crystalDamage(mc.player, entity.position(), predictMovement.get(), blockPos);
        if (selfDamage > maxDamage.get() || (antiSuicide.get() && selfDamage >= EntityUtils.getTotalHealth(mc.player))) return 0;

        float damage = getDamageToTargets(entity.position(), false);
        if (damage < minDamage.get()) return 0;

        return damage;
    }

    private void doBreak(Entity crystal) {
        if (antiWeakness.get()) {
            MobEffectInstance weakness = mc.player.getEffect(MobEffects.WEAKNESS);
            MobEffectInstance strength = mc.player.getEffect(MobEffects.STRENGTH);

            if (weakness != null && (strength == null || strength.getAmplifier() <= weakness.getAmplifier())) {
                if (!isValidWeaknessItem(mc.player.getMainHandItem(), crystal)) {
                    if (!InvUtils.swap(InvUtils.findInHotbar(stack -> isValidWeaknessItem(stack, crystal)).slot(), false)) return;
                    switchTimer = 1;
                    return;
                }
            }
        }

        if (rotate.get()) {
            double yaw = Rotations.getYaw(crystal);
            double pitch = Rotations.getPitch(crystal, Target.Feet);
            Rotations.rotate(yaw, pitch, 50, () -> attackCrystal(crystal));
        } else {
            attackCrystal(crystal);
        }

        removed.add(crystal.getId());
        attemptedBreaks.add(crystal.getId());
        breakTimer = breakDelay.get();
    }

    private boolean isValidWeaknessItem(ItemStack itemStack, Entity crystal) {
        return DamageUtils.getAttackDamage(mc.player, crystal, itemStack) > 0;
    }

    private void attackCrystal(Entity entity) {
        mc.player.connection.send(new ServerboundAttackPacket(entity.getId()));

        InteractionHand hand = InvUtils.findInHotbar(Items.END_CRYSTAL).getHand();
        if (hand == null) hand = InteractionHand.MAIN_HAND;

        if (swingMode.get().client()) mc.player.swing(hand);
        if (swingMode.get().packet()) mc.getConnection().send(new ServerboundSwingPacket(hand));

        attacks++;
    }

    // Place

    private void doPlace() {
        if (placeTimer > 0) return;

        if (!InvUtils.testInHotbar(Items.END_CRYSTAL)) return;

        if (autoSwitch.get() != AutoSwitchMode.None) {
            if (noGapSwitch.get() && autoSwitch.get() == AutoSwitchMode.Normal && offItem != Items.END_CRYSTAL) {
                if (mainItem == Items.ENCHANTED_GOLDEN_APPLE
                    || offItem == Items.ENCHANTED_GOLDEN_APPLE
                    || mainItem == Items.GOLDEN_APPLE
                    || offItem == Items.GOLDEN_APPLE) return;
            }
        } else if (mainItem != Items.END_CRYSTAL && offItem != Items.END_CRYSTAL) return;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (getBreakDamage(entity, false) > 0) return;
        }

        BlockPos.MutableBlockPos bestBlockPos = new BlockPos.MutableBlockPos();
        float bestDamage = 0;

        for (LivingEntity target : targets) {
            int r = (int) Math.ceil(placeRange.get());
            int centerX = (int) Math.floor(target.getX());
            int centerY = (int) Math.floor(target.getY());
            int centerZ = (int) Math.floor(target.getZ());

            for (int x = centerX - r; x <= centerX + r; x++) {
                for (int y = centerY - 2; y <= centerY + 1; y++) {
                    for (int z = centerZ - r; z <= centerZ + r; z++) {
                        BlockPos blockPos = new BlockPos(x, y, z);

                        boolean hasBlock = mc.level.getBlockState(blockPos).is(Blocks.BEDROCK) || mc.level.getBlockState(blockPos).is(Blocks.OBSIDIAN);
                        if (!hasBlock) continue;

                        BlockPos airPos = blockPos.above();
                        if (!mc.level.getBlockState(airPos).isAir()) continue;

                        if (isOutOfRange(blockPos.above(), true)) continue;

                        Vec3 vec3d = new Vec3(x + 0.5, y + 1, z + 0.5);
                        float selfDamage = DamageUtils.crystalDamage(mc.player, vec3d, predictMovement.get(), blockPos);
                        if (selfDamage > maxDamage.get() || (antiSuicide.get() && selfDamage >= EntityUtils.getTotalHealth(mc.player))) continue;

                        float damage = getDamageToTargets(vec3d, false);
                        if (damage < minDamage.get()) continue;

                        AABB box = new AABB(x, y + 1, z, x + 1, y + 2, z + 1);
                        if (EntityUtils.intersectsWithEntity(box, e -> !e.isSpectator() && !removed.contains(e.getId()))) continue;

                        if (damage > bestDamage) {
                            bestDamage = damage;
                            bestBlockPos.set(x, y, z);
                        }
                    }
                }
            }
        }

        if (bestDamage == 0 || bestBlockPos.getX() == 0 && bestBlockPos.getY() == 0 && bestBlockPos.getZ() == 0) return;

        BlockHitResult result = getPlaceInfo(bestBlockPos);
        Vec3 vec3d = new Vec3(
            result.getBlockPos().getX() + 0.5 + result.getDirection().getUnitVec3i().getX() * 1.0 / 2.0,
            result.getBlockPos().getY() + 0.5 + result.getDirection().getUnitVec3i().getY() * 1.0 / 2.0,
            result.getBlockPos().getZ() + 0.5 + result.getDirection().getUnitVec3i().getZ() * 1.0 / 2.0
        );

        if (rotate.get()) {
            double yaw = Rotations.getYaw(vec3d);
            double pitch = Rotations.getPitch(vec3d);
            Rotations.rotate(yaw, pitch, 50, () -> placeCrystal(result));
        } else {
            placeCrystal(result);
        }

        placeTimer += placeDelay.get();
    }

    private BlockHitResult getPlaceInfo(BlockPos blockPos) {
        Vec3 playerEye = mc.player.getEyePosition();

        for (Direction side : Direction.values()) {
            Vec3 end = new Vec3(
                blockPos.getX() + 0.5 + side.getUnitVec3i().getX() * 0.5,
                blockPos.getY() + 0.5 + side.getUnitVec3i().getY() * 0.5,
                blockPos.getZ() + 0.5 + side.getUnitVec3i().getZ() * 0.5
            );

            BlockHitResult result = mc.level.clip(new ClipContext(playerEye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));

            if (result != null && result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(blockPos)) {
                return result;
            }
        }

        Direction side = blockPos.getY() > playerEye.y ? Direction.DOWN : Direction.UP;
        return new BlockHitResult(playerEye, side, blockPos, false);
    }

    private void placeCrystal(BlockHitResult result) {
        FindItemResult item = InvUtils.findInHotbar(Items.END_CRYSTAL);
        if (!item.found()) return;

        int prevSlot = mc.player.getInventory().getSelectedSlot();

        if (autoSwitch.get() != AutoSwitchMode.None && !item.isOffhand()) InvUtils.swap(item.slot(), false);

        InteractionHand hand = item.getHand();
        if (hand == null) return;

        mc.getConnection().send(new ServerboundUseItemOnPacket(hand, result, 0));

        if (swingMode.get().client()) mc.player.swing(hand);
        if (swingMode.get().packet()) mc.getConnection().send(new ServerboundSwingPacket(hand));

        placing = true;
        placingCrystalBlockPos.set(result.getBlockPos()).move(0, 1, 0);

        placeRenderPos.set(result.getBlockPos());
        placeRenderTimer = placeRenderTime.get();

        if (autoSwitch.get() == AutoSwitchMode.Silent) InvUtils.swap(prevSlot, false);
    }

    // Others

    private boolean shouldPause() {
        if (pauseOnLag.get() && TickRate.INSTANCE.getTimeSinceLastTick() >= 1.0f) return true;
        return EntityUtils.getTotalHealth(mc.player) <= pauseHealth.get();
    }

    private boolean isOutOfRange(Object target, boolean place) {
        BlockPos pos;
        Vec3 center;
        if (target instanceof Entity entity) {
            pos = entity.blockPosition().below();
            center = entity.position();
        } else {
            BlockPos blockPos = (BlockPos) target;
            pos = blockPos.below();
            center = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
        }

        Vec3 playerEye = mc.player.getEyePosition();
        BlockHitResult result = mc.level.clip(new ClipContext(playerEye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));

        boolean behindWall = result == null || !result.getBlockPos().equals(pos);
        return !PlayerUtils.isWithin(center, (place ? placeWallsRange : breakWallsRange).get());
    }

    private float getDamageToTargets(Vec3 vec3d, boolean breaking) {
        float damage = 0;
        for (LivingEntity target : targets) {
            if (smartDelay.get() && breaking && target.hurtTime > 0) continue;
            damage += DamageUtils.crystalDamage(target, vec3d, predictMovement.get(), new BlockPos.MutableBlockPos().set(vec3d.x, vec3d.y - 1, vec3d.z));
        }
        return damage;
    }

    private void findTargets() {
        targets.clear();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity livingEntity)) continue;

            if (livingEntity instanceof Player player) {
                if (player.getAbilities().instabuild || livingEntity == mc.player) continue;
                if (!player.isAlive() || !Friends.get().shouldAttack(player)) continue;

                if (ignoreNakeds.get()) {
                    if (player.getOffhandItem().isEmpty()
                        && player.getMainHandItem().isEmpty()
                        && player.getItemBySlot(EquipmentSlot.FEET).isEmpty()
                        && player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                        && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                        && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) continue;
                }
            }

            if (!entities.get().contains(livingEntity.getType())) continue;
            if (livingEntity.distanceToSqr(mc.player) > targetRange.get() * targetRange.get()) continue;

            targets.add(livingEntity);
        }
    }

    // Rendering

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!renderPlace.get() || placeRenderTimer <= 0) return;
        event.renderer.box(placeRenderPos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    public enum AutoSwitchMode {
        Normal,
        Silent,
        None
    }

    public enum SwingMode {
        Client,
        Packet,
        Both,
        None;

        public boolean client() {
            return this == Client || this == Both;
        }

        public boolean packet() {
            return this == Packet || this == Both;
        }
    }
}