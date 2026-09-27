package com.vortex.modules;

import com.vortex.Vortex;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class ElytraJet extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("The flight mode to use.")
        .defaultValue(Mode.Boost)
        .build()
    );

    private final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("How fast you go forward and backward.")
        .defaultValue(1)
        .min(0)
        .build()
    );

    private final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("How fast you go up and down.")
        .defaultValue(1)
        .min(0)
        .build()
    );

    private final Setting<Boolean> autoTakeoff = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-takeoff")
        .description("Hold jump to automatically take off with your elytra.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useFireworks = sgGeneral.add(new BoolSetting.Builder()
        .name("use-fireworks")
        .description("Automatically uses firework rockets to boost.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Firework)
        .build()
    );

    private final Setting<Double> fireworkDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("firework-delay")
        .description("Seconds in between firework boosts.")
        .defaultValue(0.5)
        .min(0)
        .sliderMax(5)
        .visible(useFireworks::get)
        .build()
    );

    private final Setting<Boolean> lockPitch = sgGeneral.add(new BoolSetting.Builder()
        .name("lock-pitch")
        .description("Locks your pitch while flying.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> pitch = sgGeneral.add(new IntSetting.Builder()
        .name("pitch")
        .description("The pitch to lock at.")
        .defaultValue(-10)
        .min(-90)
        .max(90)
        .sliderMin(-90)
        .sliderMax(90)
        .visible(lockPitch::get)
        .build()
    );

    private boolean lastJumpPressed;
    private int jumpTimer;
    private int ticksLeft;

    public ElytraJet() {
        super(Vortex.CATEGORY, "elytra-jet", "Insane elytra flight with boost, control and automatic firework boosting.");
    }

    @Override
    public void onActivate() {
        lastJumpPressed = false;
        jumpTimer = 0;
        ticksLeft = 0;
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) {
            mc.player.getAbilities().flying = false;
            mc.player.getAbilities().mayfly = false;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || !mc.player.isAlive()) return;

        if (mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) return;

        if (autoTakeoff.get()) autoTakeoff();

        if (lockPitch.get() && mc.player.isFallFlying()) mc.player.setXRot(pitch.get());

        if (!mc.player.isFallFlying()) return;

        switch (mode.get()) {
            case Boost -> doBoost();
            case Control -> doControl();
            case Firework -> doFirework();
        }
    }

    private void autoTakeoff() {
        boolean jumpPressed = mc.options.keyJump.isDown();

        if (jumpPressed && !lastJumpPressed && !mc.player.isFallFlying()) {
            jumpTimer = 0;
            if (mc.player.onGround()) {
                mc.player.setSprinting(true);
                mc.player.jumpFromGround();
                mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            }
        }

        if (mc.player.onGround()) {
            if (jumpPressed && !mc.player.isFallFlying()) {
                if (jumpTimer >= 6) {
                    jumpTimer = 0;
                    mc.player.setJumping(false);
                    mc.player.setSprinting(true);
                    mc.player.jumpFromGround();
                    mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                }
                jumpTimer++;
            } else if (!jumpPressed) {
                jumpTimer = 0;
            }
        }

        lastJumpPressed = jumpPressed;
    }

    private void doBoost() {
        if (!mc.options.keyUp.isDown()) return;

        double yawRad = Math.toRadians(mc.player.getYRot());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);

        Vec3 velocity = mc.player.getDeltaMovement();

        double targetX = forwardX * horizontalSpeed.get();
        double targetZ = forwardZ * horizontalSpeed.get();
        double targetY = 0;

        if (mc.options.keyJump.isDown()) targetY = verticalSpeed.get();
        else if (mc.options.keyShift.isDown()) targetY = -verticalSpeed.get();

        mc.player.setDeltaMovement(targetX, targetY, targetZ);
    }

    private void doControl() {
        Vec3 vec3d = new Vec3(0, 0, 0);

        if (mc.options.keyUp.isDown()) {
            vec3d = vec3d.add(0, 0, horizontalSpeed.get());
            vec3d = vec3d.yRot(-(float) Math.toRadians(mc.player.getYRot()));
        } else if (mc.options.keyDown.isDown()) {
            vec3d = vec3d.add(0, 0, horizontalSpeed.get());
            vec3d = vec3d.yRot((float) Math.toRadians(mc.player.getYRot()));
        }

        if (mc.options.keyJump.isDown()) {
            vec3d = vec3d.add(0, verticalSpeed.get(), 0);
        } else if (mc.options.keyShift.isDown()) {
            vec3d = vec3d.add(0, -verticalSpeed.get(), 0);
        }

        if (mc.player.fallDistance > 0.2 && !mc.options.keyShift.isDown()) {
            mc.player.setDeltaMovement(vec3d);
            mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }
    }

    private void doFirework() {
        if (!useFireworks.get() || !mc.options.keyUp.isDown()) return;

        if (ticksLeft <= 0) {
            ticksLeft = (int) (fireworkDelay.get() * 20);

            FindItemResult itemResult = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
            if (!itemResult.found()) return;

            if (itemResult.isOffhand()) {
                mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
                mc.player.swing(InteractionHand.OFF_HAND);
            } else {
                InvUtils.swap(itemResult.slot(), true);

                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                mc.player.swing(InteractionHand.MAIN_HAND);

                InvUtils.swapBack();
            }

            return;
        }

        ticksLeft--;
    }

    private enum Mode {
        Boost,
        Control,
        Firework
    }
}