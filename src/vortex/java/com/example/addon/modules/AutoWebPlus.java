package com.example.addon.modules;

import com.example.addon.QuinnAddon;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AutoWebPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTarget = settings.createGroup("Targeting");

    private final Setting<Integer> delay = sgGeneral.add(
        new IntSetting.Builder()
            .name("delay")
            .description("Delay between normal web placement attempts in milliseconds.")
            .defaultValue(50)
            .min(0)
            .sliderMax(500)
            .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(
        new BoolSetting.Builder()
            .name("rotate")
            .description("Rotate toward the placement position.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> pauseUsingItem = sgGeneral.add(
        new BoolSetting.Builder()
            .name("pause-using-item")
            .description("Pause AutoWeb+ while using an item.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> doubles = sgGeneral.add(
        new BoolSetting.Builder()
            .name("doubles")
            .description("Attempts to place a second web above the target.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> massWeb = sgGeneral.add(
        new BoolSetting.Builder()
            .name("mass-web")
            .description("Places webs in a radius of 1 around the target.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> massWebDelay = sgGeneral.add(
        new IntSetting.Builder()
            .name("mass-web-delay")
            .description("Delay between each mass web placement in ticks.")
            .defaultValue(0)
            .min(0)
            .max(3)
            .sliderMin(0)
            .sliderMax(3)
            .build()
    );

    private final Setting<Boolean> airPlace = sgGeneral.add(
        new BoolSetting.Builder()
            .name("air-place")
            .description("Allows AutoWeb+ to place webs without a solid block underneath.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> predictMovement = sgGeneral.add(
        new BoolSetting.Builder()
            .name("predict-movement")
            .description("Predicts target movement.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Double> prediction = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("prediction")
            .description("Amount of movement prediction.")
            .defaultValue(1.0)
            .min(0.0)
            .sliderMax(3.0)
            .build()
    );

    private final Setting<Boolean> ignoreFriends = sgTarget.add(
        new BoolSetting.Builder()
            .name("ignore-friends")
            .description("Do not target players on your friends list.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> range = sgTarget.add(
        new DoubleSetting.Builder()
            .name("range")
            .description("Maximum distance to target players.")
            .defaultValue(4.5)
            .min(1.0)
            .sliderMin(1.0)
            .sliderMax(8.0)
            .build()
    );

    private long lastPlaceTime;

    private BlockPos lastPlacedPos;
    private long lastPlacedRenderTime;

    // Mass Web state.
    private final List<BlockPos> massWebPositions = new ArrayList<>();
    private int massWebIndex;
    private int massWebTickCounter;
    private BlockPos massWebCenter;

    public AutoWebPlus() {
        super(
            QuinnAddon.CATEGORY,
            "auto-web+",
            "Automatically places cobwebs at nearby enemy players."
        );
    }

    @Override
    public void onActivate() {
        lastPlaceTime = 0;
        lastPlacedPos = null;
        lastPlacedRenderTime = 0;

        resetMassWeb();
    }

    @Override
    public void onDeactivate() {
        resetMassWeb();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (pauseUsingItem.get() && mc.player.isUsingItem()) {
            return;
        }

        Player target = findTarget();

        if (target == null) {
            resetMassWeb();
            return;
        }

        FindItemResult web = InvUtils.findInHotbar(
            Items.COBWEB
        );

        if (!web.found()) {
            resetMassWeb();
            return;
        }

        BlockPos targetPos;

        if (predictMovement.get()) {
            targetPos = getPredictedPosition(target);
        } else {
            targetPos = getTargetPosition(target);
        }

        /*
         * Mass Web has its own placement system.
         * The normal delay setting does not affect individual
         * cobwebs inside a Mass Web.
         */
        if (massWeb.get()) {
            handleMassWeb(targetPos, web);
            return;
        }

        // Make sure no old Mass Web placement is running.
        resetMassWeb();

        if (System.currentTimeMillis() - lastPlaceTime < delay.get()) {
            return;
        }

        placeSingleWeb(targetPos, web);
    }

    private void placeSingleWeb(
        BlockPos targetPos,
        FindItemResult web
    ) {
        if (!canPlace(targetPos)) {
            return;
        }

        boolean success = BlockUtils.place(
            targetPos,
            web,
            rotate.get() ? 50 : 0,
            false
        );

        if (!success) {
            return;
        }

        lastPlacedPos = targetPos;
        lastPlacedRenderTime = System.currentTimeMillis();

        if (doubles.get()) {
            BlockPos secondPos = targetPos.above();

            if (canPlace(secondPos)) {
                boolean secondSuccess = BlockUtils.place(
                    secondPos,
                    web,
                    rotate.get() ? 50 : 0,
                    false
                );

                if (secondSuccess) {
                    lastPlacedPos = secondPos;
                    lastPlacedRenderTime =
                        System.currentTimeMillis();
                }
            }
        }

        lastPlaceTime = System.currentTimeMillis();
    }

    private void handleMassWeb(
        BlockPos center,
        FindItemResult web
    ) {
        /*
         * If this is a new target position, create a new
         * radius-1 Mass Web placement sequence.
         */
        if (massWebCenter == null || !massWebCenter.equals(center)) {
            startMassWeb(center);
        }

        if (massWebIndex >= massWebPositions.size()) {
            lastPlaceTime = System.currentTimeMillis();
            resetMassWeb();
            return;
        }

        /*
         * The Mass Web delay is measured in ticks.
         *
         * 0 = place the next web immediately.
         * 1 = wait 1 tick between webs.
         * 2 = wait 2 ticks between webs.
         * 3 = wait 3 ticks between webs.
         */
        if (massWebTickCounter < massWebDelay.get()) {
            massWebTickCounter++;
            return;
        }

        BlockPos pos = massWebPositions.get(massWebIndex);

        /*
         * If a position has become unavailable since the sequence
         * started, skip it instead of stopping the entire Mass Web.
         */
        if (canPlace(pos)) {
            boolean success = BlockUtils.place(
                pos,
                web,
                rotate.get() ? 50 : 0,
                false
            );

            if (success) {
                lastPlacedPos = pos;
                lastPlacedRenderTime =
                    System.currentTimeMillis();
            }
        }

        massWebIndex++;
        massWebTickCounter = 0;

        /*
         * Finished placing the entire Mass Web.
         */
        if (massWebIndex >= massWebPositions.size()) {
            lastPlaceTime = System.currentTimeMillis();
            resetMassWeb();
        }
    }

    private void startMassWeb(BlockPos center) {
        massWebPositions.clear();
        massWebIndex = 0;
        massWebTickCounter = 0;
        massWebCenter = center;

        /*
         * Radius 1:
         *
         *       [X]
         *   [X] [X] [X]
         *       [X]
         *
         * y = 0 is the target's feet.
         *
         * y = 1 gives the upper layer as well.
         */
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {

                    // Circular horizontal radius of 1.
                    if (x * x + z * z > 1) {
                        continue;
                    }

                    massWebPositions.add(
                        center.offset(x, y, z)
                    );
                }
            }
        }
    }

    private void resetMassWeb() {
        massWebPositions.clear();
        massWebIndex = 0;
        massWebTickCounter = 0;
        massWebCenter = null;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (lastPlacedPos == null) {
            return;
        }

        long elapsed =
            System.currentTimeMillis()
                - lastPlacedRenderTime;

        if (elapsed >= 1000) {
            lastPlacedPos = null;
            return;
        }

        double progress =
            elapsed / 1000.0;

        int alpha =
            (int) (255 * (1.0 - progress));

        Color sideColor = new Color(
            255,
            255,
            255,
            alpha / 3
        );

        Color lineColor = new Color(
            255,
            255,
            255,
            alpha
        );

        event.renderer.box(
            lastPlacedPos,
            sideColor,
            lineColor,
            ShapeMode.Both,
            0
        );
    }

    private Player findTarget() {
        List<Player> targets =
            new ArrayList<>();

        for (Player player :
            mc.level.players()) {

            if (player == mc.player) {
                continue;
            }

            if (!player.isAlive()) {
                continue;
            }

            if (ignoreFriends.get()
                && Friends.get().isFriend(player)) {
                continue;
            }

            if (mc.player.distanceTo(player)
                > range.get()) {
                continue;
            }

            targets.add(player);
        }

        if (targets.isEmpty()) {
            return null;
        }

        targets.sort(
            Comparator.comparingDouble(
                player ->
                    mc.player.distanceToSqr(player)
            )
        );

        return targets.get(0);
    }

    private BlockPos getTargetPosition(
        Player target
    ) {
        return BlockPos.containing(
            target.getX(),
            target.getY(),
            target.getZ()
        );
    }

    private BlockPos getPredictedPosition(
        Player target
    ) {
        Vec3 velocity =
            target.getDeltaMovement();

        double x =
            target.getX()
                + velocity.x * prediction.get();

        double y =
            target.getY()
                + velocity.y * prediction.get();

        double z =
            target.getZ()
                + velocity.z * prediction.get();

        return BlockPos.containing(
            x,
            y,
            z
        );
    }

    private boolean canPlace(BlockPos pos) {
        if (mc.level == null) {
            return false;
        }

        if (!mc.level
            .getBlockState(pos)
            .canBeReplaced()) {
            return false;
        }

        /*
         * Air Place allows AutoWeb+ to attempt placement
         * without requiring a solid block underneath.
         */
        if (airPlace.get()) {
            return true;
        }

        BlockPos below = pos.below();

        return mc.level
            .getBlockState(below)
            .isSolidRender();
    }
}