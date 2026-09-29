package com.example.addon.modules;

import com.example.addon.QuinnAddon;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class Speed extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // ============================================================
    // SPEED SETTINGS
    // ============================================================

    private final Setting<Double> groundSpeed = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("ground-speed")
            .description("Exact movement speed on normal ground in blocks per second.")
            .defaultValue(5.6)
            .min(0.1)
            .max(5.8)
            .sliderMin(0.1)
            .sliderMax(5.8)
            .decimalPlaces(1)
            .build()
    );

    private final Setting<Double> airSpeed = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("air-speed")
            .description("Exact movement speed in the air in blocks per second.")
            .defaultValue(5.6)
            .min(0.1)
            .max(6.6)
            .sliderMin(0.1)
            .sliderMax(6.6)
            .decimalPlaces(1)
            .build()
    );

    private final Setting<Double> lavaSpeed = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("lava-speed")
            .description("Exact movement speed in lava in blocks per second.")
            .defaultValue(1.6)
            .min(0.1)
            .max(1.6)
            .sliderMin(0.1)
            .sliderMax(1.6)
            .decimalPlaces(1)
            .build()
    );

    private final Setting<Double> cobwebSpeed = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("cobweb-speed")
            .description("Exact movement speed inside cobwebs in blocks per second.")
            .defaultValue(4.5)
            .min(0.1)
            .max(4.5)
            .sliderMin(0.1)
            .sliderMax(4.5)
            .decimalPlaces(1)
            .build()
    );

    private final Setting<Double> sneakSpeed = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("sneak-speed")
            .description("Exact movement speed while sneaking in blocks per second.")
            .defaultValue(4.1)
            .min(0.1)
            .max(4.1)
            .sliderMin(0.1)
            .sliderMax(4.1)
            .decimalPlaces(1)
            .build()
    );

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public Speed() {
        super(
            QuinnAddon.CATEGORY,
            "speed",
            "Sets the player's horizontal movement speed."
        );
    }

    // ============================================================
    // TICK
    // ============================================================

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        if (!hasMovementInput()) return;

        /*
         * Get the exact speed that should be used.
         *
         * A negative value means the module should completely
         * leave the player's movement alone.
         *
         * This is used for:
         * - Water
         * - Soul Sand
         */
        double speed = getCurrentSpeed();

        if (speed < 0.0) {
            return;
        }

        /*
         * When eating, drinking, or otherwise using an item,
         * reduce the currently selected speed by exactly
         * 1 block per second.
         *
         * Example:
         *
         * 5.6 -> 4.6 BPS
         * 4.1 -> 3.1 BPS
         * 2.0 -> 1.0 BPS
         */
        if (mc.player.isUsingItem()) {
            speed = Math.max(0.0, speed - 1.0);
        }

        setExactHorizontalSpeed(speed);
    }

    // ============================================================
    // CURRENT SPEED
    // ============================================================

    private double getCurrentSpeed() {
        /*
         * WATER
         *
         * The module does not modify movement in water.
         */
        if (mc.player.isInWater()) {
            return -1.0;
        }

        /*
         * COBWEB
         *
         * Cobweb takes priority over normal ground/air movement.
         */
        if (isInCobweb()) {
            return cobwebSpeed.get();
        }

        /*
         * LAVA
         */
        if (mc.player.isInLava()) {
            return lavaSpeed.get();
        }

        /*
         * SNEAKING
         *
         * Sneaking takes priority over normal ground speed.
         * This only applies when the player is actually sneaking.
         */
        if (mc.player.isShiftKeyDown()) {
            /*
             * Soul Sand must still completely disable the module.
             */
            if (mc.player.onGround() && isOnSoulSand()) {
                return -1.0;
            }

            return sneakSpeed.get();
        }

        /*
         * GROUND
         *
         * Soul Sand is specifically excluded.
         *
         * Soul Soil is NOT excluded and therefore uses
         * the normal ground-speed slider.
         */
        if (mc.player.onGround()) {
            if (isOnSoulSand()) {
                return -1.0;
            }

            return groundSpeed.get();
        }

        /*
         * AIR
         */
        return airSpeed.get();
    }

    // ============================================================
    // MOVEMENT INPUT
    // ============================================================

    private boolean hasMovementInput() {
        if (mc.player == null) return false;

        Vec2 input = mc.player.input.getMoveVector();

        return input.x != 0.0f || input.y != 0.0f;
    }

    // ============================================================
    // EXACT HORIZONTAL SPEED
    // ============================================================

    private void setExactHorizontalSpeed(double blocksPerSecond) {
        if (mc.player == null) return;

        Vec2 input = mc.player.input.getMoveVector();

        double sideways = input.x;
        double forward = input.y;

        /*
         * Calculate the magnitude of the player's movement input.
         */
        double magnitude = Math.sqrt(
            sideways * sideways + forward * forward
        );

        if (magnitude < 0.0001) {
            return;
        }

        /*
         * Normalize the input.
         *
         * This prevents diagonal movement from being faster
         * than straight movement.
         */
        sideways /= magnitude;
        forward /= magnitude;

        /*
         * Convert blocks per second to blocks per tick.
         *
         * Minecraft runs at 20 ticks per second.
         */
        double speedPerTick = blocksPerSecond / 20.0;

        /*
         * Convert the player's local movement input into
         * world-space movement using the player's yaw.
         */
        float yaw = mc.player.getYRot();

        double yawRadians = Math.toRadians(yaw);

        double sin = Math.sin(yawRadians);
        double cos = Math.cos(yawRadians);

        double motionX =
            (sideways * cos - forward * sin)
                * speedPerTick;

        double motionZ =
            (forward * cos + sideways * sin)
                * speedPerTick;

        Vec3 velocity = mc.player.getDeltaMovement();

        /*
         * Replace the horizontal velocity.
         *
         * This means the slider represents the TOTAL
         * horizontal speed rather than adding speed to
         * Minecraft's existing movement.
         */
        mc.player.setDeltaMovement(
            motionX,
            velocity.y,
            motionZ
        );
    }

    // ============================================================
    // SOUL SAND
    // ============================================================

    private boolean isOnSoulSand() {
        if (mc.player == null || mc.level == null) {
            return false;
        }

        /*
         * Check ONLY the block directly underneath the player.
         *
         * Soul Soil is intentionally not included.
         */
        BlockPos blockPos = mc.player.blockPosition().below();

        BlockState state = mc.level.getBlockState(blockPos);

        return state.is(Blocks.SOUL_SAND);
    }

    // ============================================================
    // COBWEB DETECTION
    // ============================================================

    private boolean isInCobweb() {
        if (mc.player == null || mc.level == null) {
            return false;
        }

        AABB box = mc.player.getBoundingBox();

        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);

        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(box.maxY);

        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);

                    BlockState state =
                        mc.level.getBlockState(pos);

                    if (state.is(Blocks.COBWEB)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
