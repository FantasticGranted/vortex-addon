package com.vortex.modules;

import com.vortex.Vortex;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Set;

public class Aura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgTiming = settings.createGroup("Timing");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General

    private final Setting<AttackMode> attackMode = sgGeneral.add(new EnumSetting.Builder<AttackMode>()
        .name("attack-mode")
        .description("How to attack the target. Packet is the most instant and can hit through walls.")
        .defaultValue(AttackMode.Packet)
        .build()
    );

    private final Setting<Boolean> throughWalls = sgGeneral.add(new BoolSetting.Builder()
        .name("through-walls")
        .description("Attacks targets even when you cannot see them, as long as they are within the walls range.")
        .defaultValue(true)
        .build()
    );

    private final Setting<RotationMode> rotation = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
        .name("rotate")
        .description("When to rotate towards the target.")
        .defaultValue(RotationMode.Always)
        .build()
    );

    private final Setting<SwingMode> swing = sgGeneral.add(new EnumSetting.Builder<SwingMode>()
        .name("swing")
        .description("How to swing your arm when attacking.")
        .defaultValue(SwingMode.Client)
        .build()
    );

    private final Setting<AttackItems> attackWhenHolding = sgGeneral.add(new EnumSetting.Builder<AttackItems>()
        .name("attack-when-holding")
        .description("Only attacks an entity when a specified item is in your hand.")
        .defaultValue(AttackItems.Weapons)
        .build()
    );

    private final Setting<List<Item>> weapons = sgGeneral.add(new ItemListSetting.Builder()
        .name("selected-weapon-types")
        .description("Which types of weapons to attack with.")
        .defaultValue(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.TRIDENT)
        .filter(FILTER::contains)
        .visible(() -> attackWhenHolding.get() == AttackItems.Weapons)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Switches to an acceptable weapon when attacking the target.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Switches back to your previous slot when done attacking.")
        .defaultValue(false)
        .visible(autoSwitch::get)
        .build()
    );

    private final Setting<Integer> switchDelay = sgGeneral.add(new IntSetting.Builder()
        .name("switch-delay")
        .description("Ticks to wait before attacking after swapping weapons.")
        .defaultValue(0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<ShieldMode> shieldMode = sgGeneral.add(new EnumSetting.Builder<ShieldMode>()
        .name("shield-mode")
        .description("What to do when your target is blocking with a shield.")
        .defaultValue(ShieldMode.Break)
        .build()
    );

    private final Setting<Boolean> onlyOnClick = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-click")
        .description("Only attacks while holding left click.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> onlyOnLook = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-look")
        .description("Only attacks the entity you are looking at.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseOnUse = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-use")
        .description("Pauses while using an item or mining.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseOnLag = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-lag")
        .description("Pauses if the server is lagging.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<Module>> pauseModules = sgGeneral.add(new ModuleListSetting.Builder()
        .name("pause-on-modules")
        .description("Pauses while any of these modules are active.")
        .build()
    );

    // Targeting

    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to attack.")
        .onlyAttackable()
        .defaultValue(EntityType.PLAYER)
        .build()
    );

    private final Setting<SortPriority> priority = sgTargeting.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("How to filter targets within range.")
        .defaultValue(SortPriority.LowestDistance)
        .build()
    );

    private final Setting<Integer> maxTargets = sgTargeting.add(new IntSetting.Builder()
        .name("max-targets")
        .description("How many entities to attack at once.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 10)
        .visible(() -> !onlyOnLook.get())
        .build()
    );

    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("range")
        .description("The maximum range to attack visible targets.")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Double> wallsRange = sgTargeting.add(new DoubleSetting.Builder()
        .name("walls-range")
        .description("The maximum range to attack targets behind walls.")
        .defaultValue(6)
        .min(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-named")
        .description("Does not attack mobs with a name.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignorePassive = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-passive")
        .description("Only attacks sometimes-passive mobs if they are targeting you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreTamed = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-tamed")
        .description("Avoids attacking mobs you tamed.")
        .defaultValue(false)
        .build()
    );

    // Timing

    private final Setting<Boolean> customDelay = sgTiming.add(new BoolSetting.Builder()
        .name("custom-delay")
        .description("Use a custom delay instead of the vanilla attack cooldown.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> hitDelay = sgTiming.add(new IntSetting.Builder()
        .name("hit-delay")
        .description("Ticks in between hits (0 is the most insane).")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .visible(customDelay::get)
        .build()
    );

    private final Setting<Boolean> tpsSync = sgTiming.add(new BoolSetting.Builder()
        .name("tps-sync")
        .description("Scales the delay with the server TPS.")
        .defaultValue(true)
        .build()
    );

    // Render

    private final Setting<Boolean> renderTargets = sgRender.add(new BoolSetting.Builder()
        .name("render-targets")
        .description("Renders a box around targeted entities.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .defaultValue(ShapeMode.Both)
        .visible(renderTargets::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(0, 255, 220, 35))
        .visible(() -> renderTargets.get() && shapeMode.get().sides())
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(0, 255, 220))
        .visible(() -> renderTargets.get() && shapeMode.get().lines())
        .build()
    );

    private static final Set<Item> FILTER = Set.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.DIAMOND_PICKAXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE, Items.MACE, Items.DIAMOND_SPEAR, Items.TRIDENT);

    private final List<Entity> targets = new java.util.ArrayList<>();
    private int switchTimer, hitTimer;
    private boolean attacking, swapped;
    private int previousSlot = -1;

    public Aura() {
        super(Vortex.CATEGORY, "aura", "Insane kill aura - attacks every target in range, even through walls.");
    }

    @Override
    public void onActivate() {
        previousSlot = -1;
        swapped = false;
    }

    @Override
    public void onDeactivate() {
        targets.clear();
        stopAttacking();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameType.SPECTATOR) {
            stopAttacking();
            return;
        }

        if (pauseOnUse.get() && (mc.gameMode.isDestroying() || mc.player.isUsingItem())) {
            stopAttacking();
            return;
        }

        if (onlyOnClick.get() && !mc.options.keyAttack.isDown()) {
            stopAttacking();
            return;
        }

        if (TickRate.INSTANCE.getTimeSinceLastTick() >= 1f && pauseOnLag.get()) {
            stopAttacking();
            return;
        }

        for (Module module : pauseModules.get()) {
            if (module.isActive()) {
                stopAttacking();
                return;
            }
        }

        if (onlyOnLook.get()) {
            Entity targeted = mc.crosshairPickEntity;
            if (targeted == null || !entityCheck(targeted)) {
                stopAttacking();
                return;
            }
            targets.clear();
            targets.add(targeted);
        } else {
            targets.clear();
            TargetUtils.getList(targets, this::entityCheck, priority.get(), maxTargets.get());
        }

        if (targets.isEmpty()) {
            stopAttacking();
            return;
        }

        Entity primary = targets.getFirst();

        if (autoSwitch.get()) {
            FindItemResult weaponResult = new FindItemResult(mc.player.getInventory().getSelectedSlot(), -1);
            if (attackWhenHolding.get() == AttackItems.Weapons)
                weaponResult = InvUtils.find(this::acceptableWeapon, 0, 8);

            if (shouldShieldBreak()) {
                FindItemResult axeResult = InvUtils.find(itemStack -> itemStack.getItem() instanceof AxeItem, 0, 8);
                if (axeResult.found()) weaponResult = axeResult;
            }

            if (!swapped) {
                previousSlot = mc.player.getInventory().getSelectedSlot();
                swapped = true;
            }

            InvUtils.swap(weaponResult.slot(), false);
        }

        if (!acceptableWeapon(mc.player.getMainHandItem())) {
            stopAttacking();
            return;
        }

        attacking = true;
        if (rotation.get() == RotationMode.Always)
            Rotations.rotate(Rotations.getYaw(primary), Rotations.getPitch(primary, Target.Body));

        if (delayCheck()) targets.forEach(this::attack);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!renderTargets.get()) return;
        for (Entity target : targets) {
            AABB box = target.getBoundingBox();
            event.renderer.box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    private void stopAttacking() {
        if (!attacking) return;
        attacking = false;
        if (swapBack.get() && swapped) {
            InvUtils.swap(previousSlot, false);
            swapped = false;
        }
    }

    private boolean shouldShieldBreak() {
        for (Entity target : targets) {
            if (target instanceof Player player && player.isBlocking() && shieldMode.get() == ShieldMode.Break) {
                return true;
            }
        }
        return false;
    }

    private boolean entityCheck(Entity entity) {
        if (entity.equals(mc.player) || entity.equals(mc.getCameraEntity())) return false;
        if ((entity instanceof LivingEntity livingEntity && livingEntity.isDeadOrDying()) || !entity.isAlive())
            return false;

        if (!entities.get().contains(entity.getType())) return false;
        if (ignoreNamed.get() && entity.hasCustomName()) return false;

        boolean see = PlayerUtils.canSeeEntity(entity);
        if (see) {
            if (!PlayerUtils.isWithin(entity, range.get())) return false;
        } else if (!throughWalls.get() || !PlayerUtils.isWithin(entity, wallsRange.get())) {
            return false;
        }

        if (ignoreTamed.get()) {
            if (entity instanceof OwnableEntity tameable
                && tameable.getOwner() != null
                && tameable.getOwner().equals(mc.player)) return false;
        }

        if (ignorePassive.get()) {
            if (entity instanceof EnderMan enderman && !enderman.isCreepy()) return false;
            if ((entity instanceof Piglin || entity instanceof ZombifiedPiglin || entity instanceof Wolf) && !((Mob) entity).isAggressive())
                return false;
        }

        if (entity instanceof Player player) {
            if (player.getAbilities().instabuild) return false;
            if (!Friends.get().shouldAttack(player)) return false;
            if (shieldMode.get() == ShieldMode.Ignore && player.isBlocking()) return false;
        }

        return true;
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundSetCarriedItemPacket) switchTimer = switchDelay.get();
    }

    private boolean delayCheck() {
        if (switchTimer > 0) {
            switchTimer--;
            return false;
        }

        float delay = (customDelay.get()) ? hitDelay.get() : 0.5f;
        if (tpsSync.get()) delay /= (TickRate.INSTANCE.getTickRate() / 20);

        if (customDelay.get()) {
            if (hitTimer < delay) {
                hitTimer++;
                return false;
            }
            return true;
        }
        return mc.player.getAttackStrengthScale(delay) >= 1;
    }

    private void attack(Entity target) {
        if (rotation.get() == RotationMode.OnHit)
            Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target, Target.Body));

        if (attackMode.get() == AttackMode.Packet) {
            mc.player.connection.send(new ServerboundAttackPacket(target.getId()));
        } else {
            mc.gameMode.attack(mc.player, target);
        }

        swing(target);
        hitTimer = 0;
    }

    private void swing(Entity target) {
        InteractionHand hand = target instanceof Player ? InteractionHand.MAIN_HAND : InteractionHand.MAIN_HAND;
        switch (swing.get()) {
            case Client -> mc.player.swing(hand);
            case Packet -> {
                if (!mc.player.getMainHandItem().isEmpty()) mc.getConnection().send(new ServerboundSwingPacket(hand));
            }
            case Both -> {
                mc.player.swing(hand);
                mc.getConnection().send(new ServerboundSwingPacket(hand));
            }
            case None -> {
            }
        }
    }

    private boolean acceptableWeapon(ItemStack stack) {
        if (shouldShieldBreak()) return stack.getItem() instanceof AxeItem;
        if (attackWhenHolding.get() == AttackItems.All) return true;

        if (weapons.get().contains(Items.DIAMOND_SWORD) && stack.is(ItemTags.SWORDS)) return true;
        if (weapons.get().contains(Items.DIAMOND_AXE) && stack.is(ItemTags.AXES)) return true;
        if (weapons.get().contains(Items.DIAMOND_PICKAXE) && stack.is(ItemTags.PICKAXES)) return true;
        if (weapons.get().contains(Items.DIAMOND_SHOVEL) && stack.is(ItemTags.SHOVELS)) return true;
        if (weapons.get().contains(Items.DIAMOND_HOE) && stack.is(ItemTags.HOES)) return true;
        if (weapons.get().contains(Items.MACE) && stack.getItem() instanceof MaceItem) return true;
        if (weapons.get().contains(Items.DIAMOND_SPEAR) && stack.is(ItemTags.SPEARS)) return true;
        return weapons.get().contains(Items.TRIDENT) && stack.getItem() instanceof TridentItem;
    }

    @Override
    public String getInfoString() {
        if (!targets.isEmpty()) return EntityUtils.getName(targets.getFirst());
        return null;
    }

    public Entity getTarget() {
        return targets.isEmpty() ? null : targets.getFirst();
    }

    public boolean isAttacking() {
        return attacking;
    }

    private enum AttackMode {
        Normal,
        Packet
    }

    private enum RotationMode {
        Always,
        OnHit,
        None
    }

    private enum SwingMode {
        Client,
        Packet,
        Both,
        None
    }

    private enum AttackItems {
        Weapons,
        All
    }

    private enum ShieldMode {
        Ignore,
        Break,
        None
    }
}