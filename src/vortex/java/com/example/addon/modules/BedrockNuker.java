package com.example.addon.modules;

import com.example.addon.QuinnAddon;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class BedrockNuker extends Module {

    private final SettingGroup sgGeneral =
        settings.getDefaultGroup();

    private final SettingGroup sgRender =
        settings.createGroup("Render");

    // ============================================================
    // GENERAL SETTINGS
    // ============================================================

    private final Setting<Double> range =
        sgGeneral.add(
            new DoubleSetting.Builder()
                .name("range")
                .description(
                    "Maximum distance to target bedrock."
                )
                .defaultValue(6.0)
                .min(1.0)
                .max(6.0)
                .sliderMin(1.0)
                .sliderMax(6.0)
                .build()
        );

    private final Setting<SortMode> sortMode =
        sgGeneral.add(
            new EnumSetting.Builder<SortMode>()
                .name("sort-mode")
                .description(
                    "Determines which bedrock block is targeted."
                )
                .defaultValue(SortMode.Closest)
                .build()
        );

    private final Setting<MiningMode> miningMode =
        sgGeneral.add(
            new EnumSetting.Builder<MiningMode>()
                .name("mining-mode")
                .description(
                    "Determines which bedrock blocks can be targeted."
                )
                .defaultValue(MiningMode.All)
                .build()
        );

    private final Setting<Boolean> pauseWhileEat =
        sgGeneral.add(
            new BoolSetting.Builder()
                .name("pause-while-eat")
                .description(
                    "Pauses bedrock mining while using an enchanted golden apple."
                )
                .defaultValue(true)
                .build()
        );

    private final Setting<Boolean> resetOnRangeExit =
        sgGeneral.add(
            new BoolSetting.Builder()
                .name("reset-on-range-exit")
                .description(
                    "Resets the current mining target when it moves out of range."
                )
                .defaultValue(true)
                .build()
        );

    // ============================================================
    // RENDER SETTINGS
    // ============================================================

    private final Setting<Boolean> render =
        sgRender.add(
            new BoolSetting.Builder()
                .name("render")
                .description(
                    "Render the block currently being mined."
                )
                .defaultValue(true)
                .build()
        );

    private final Setting<Boolean> highlightBedrock =
        sgRender.add(
            new BoolSetting.Builder()
                .name("highlight-bedrock")
                .description(
                    "Highlights all bedrock that can be mined within the configured range."
                )
                .defaultValue(false)
                .build()
        );

    private final Setting<Integer> renderGrowTicks =
        sgRender.add(
            new IntSetting.Builder()
                .name("render-grow-ticks")
                .description(
                    "Ticks required for the render box to grow to full size."
                )
                .defaultValue(20)
                .min(1)
                .max(20)
                .sliderMin(1)
                .sliderMax(20)
                .build()
        );

    private final Setting<ShapeMode> shapeMode =
        sgRender.add(
            new EnumSetting.Builder<ShapeMode>()
                .name("shape-mode")
                .description(
                    "How blocks are rendered."
                )
                .defaultValue(ShapeMode.Both)
                .build()
        );

    private final Setting<SettingColor> sideColor =
        sgRender.add(
            new ColorSetting.Builder()
                .name("side-color")
                .description(
                    "Color of rendered block sides."
                )
                .defaultValue(
                    new SettingColor(
                        255,
                        255,
                        255,
                        40
                    )
                )
                .build()
        );

    private final Setting<SettingColor> lineColor =
        sgRender.add(
            new ColorSetting.Builder()
                .name("line-color")
                .description(
                    "Color of rendered block outlines."
                )
                .defaultValue(
                    new SettingColor(
                        255,
                        255,
                        255,
                        255
                    )
                )
                .build()
        );

    // ============================================================
    // STATE
    // ============================================================

    private BlockPos miningPos;

    private int renderTicks;

    private BlockPos lastRenderPos;

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public BedrockNuker() {
        super(
            QuinnAddon.CATEGORY,
            "bedrock-nuker",
            "Automatically mines nearby bedrock."
        );
    }

    // ============================================================
    // ACTIVATE
    // ============================================================

    @Override
    public void onActivate() {
        miningPos = null;
        renderTicks = 0;
        lastRenderPos = null;
    }

    // ============================================================
    // DEACTIVATE
    // ============================================================

    @Override
    public void onDeactivate() {
        stopMining();
    }

    // ============================================================
    // TICK
    // ============================================================

    @EventHandler
    private void onTick(TickEvent.Pre event) {

        if (
            mc.player == null ||
            mc.level == null
        ) {
            stopMining();
            return;
        }

        // --------------------------------------------------------
        // PAUSE WHILE USING ENCHANTED GOLDEN APPLE
        // --------------------------------------------------------

        if (
            pauseWhileEat.get() &&
            isUsingEnchantedGoldenApple()
        ) {
            return;
        }

        // --------------------------------------------------------
        // CHECK CURRENT TARGET
        // --------------------------------------------------------

        if (miningPos != null) {

            // Target must still be bedrock.
            if (!isBedrock(miningPos)) {
                stopMining();
            }

            // Reset target when it leaves range.
            else if (!inRange(miningPos)) {

                if (resetOnRangeExit.get()) {
                    stopMining();
                } else {
                    return;
                }
            }

            // Never target bottom-most world layer.
            else if (
                miningPos.getY() <=
                    mc.level.getMinY()
            ) {
                stopMining();
            }
        }

        // --------------------------------------------------------
        // FIND NEW TARGET
        // --------------------------------------------------------

        if (miningPos == null) {

            BlockPos target =
                findBedrock();

            if (target == null) {
                return;
            }

            startMining(target);
        }

        // --------------------------------------------------------
        // CONTINUE MINING
        // --------------------------------------------------------

        if (miningPos != null) {
            mineBlock();
        }
    }

    // ============================================================
    // START MINING
    // ============================================================

    private void startMining(BlockPos pos) {

        if (mc.gameMode == null) {
            return;
        }

        miningPos =
            pos.immutable();

        renderTicks = 0;

        lastRenderPos =
            miningPos.immutable();

        mc.gameMode.startDestroyBlock(
            miningPos,
            Direction.UP
        );
    }

    // ============================================================
    // MINE BLOCK
    // ============================================================

    private void mineBlock() {

        if (
            mc.player == null ||
            mc.level == null ||
            mc.gameMode == null ||
            miningPos == null
        ) {
            stopMining();
            return;
        }

        if (!isBedrock(miningPos)) {
            stopMining();
            return;
        }

        if (
            miningPos.getY() <=
                mc.level.getMinY()
        ) {
            stopMining();
            return;
        }

        if (!inRange(miningPos)) {
            stopMining();
            return;
        }

        mc.gameMode
            .continueDestroyBlock(
                miningPos,
                Direction.UP
            );

        mc.player.connection.send(
            new ServerboundSwingPacket(
                InteractionHand.MAIN_HAND
            )
        );

        if (
            renderTicks <
                renderGrowTicks.get()
        ) {
            renderTicks++;
        }
    }

    // ============================================================
    // FIND BEDROCK TO MINE
    // ============================================================

    private BlockPos findBedrock() {

        if (
            mc.player == null ||
            mc.level == null
        ) {
            return null;
        }

        BlockPos playerPos =
            mc.player.blockPosition();

        int radius =
            (int) Math.ceil(
                range.get()
            );

        double maxDistance =
            range.get() *
            range.get();

        BlockPos best = null;

        double bestDistance = 0.0;

        int bestY =
            Integer.MIN_VALUE;

        for (
            int x = -radius;
            x <= radius;
            x++
        ) {

            for (
                int y = -radius;
                y <= radius;
                y++
            ) {

                for (
                    int z = -radius;
                    z <= radius;
                    z++
                ) {

                    BlockPos pos =
                        playerPos.offset(
                            x,
                            y,
                            z
                        );

                    // ------------------------------------------------
                    // BEDROCK ONLY
                    // ------------------------------------------------

                    if (!isBedrock(pos)) {
                        continue;
                    }

                    // ------------------------------------------------
                    // NEVER TARGET BOTTOM LAYER
                    // ------------------------------------------------

                    if (
                        pos.getY() <=
                            mc.level.getMinY()
                    ) {
                        continue;
                    }

                    // ------------------------------------------------
                    // FLATTEN MODE
                    // ------------------------------------------------

                    if (
                        miningMode.get() ==
                            MiningMode.Flatten &&
                        pos.getY() <
                            mc.player.getBlockY()
                    ) {
                        continue;
                    }

                    // ------------------------------------------------
                    // RANGE
                    // ------------------------------------------------

                    double distance =
                        mc.player.distanceToSqr(
                            pos.getX() + 0.5,
                            pos.getY() + 0.5,
                            pos.getZ() + 0.5
                        );

                    if (
                        distance >
                            maxDistance
                    ) {
                        continue;
                    }

                    // ------------------------------------------------
                    // CLOSEST
                    // ------------------------------------------------

                    if (
                        sortMode.get() ==
                            SortMode.Closest
                    ) {

                        if (
                            best == null ||
                            distance <
                                bestDistance
                        ) {

                            best =
                                pos.immutable();

                            bestDistance =
                                distance;
                        }
                    }

                    // ------------------------------------------------
                    // FURTHEST
                    // ------------------------------------------------

                    else if (
                        sortMode.get() ==
                            SortMode.Furthest
                    ) {

                        if (
                            best == null ||
                            distance >
                                bestDistance
                        ) {

                            best =
                                pos.immutable();

                            bestDistance =
                                distance;
                        }
                    }

                    // ------------------------------------------------
                    // TOP-DOWN
                    // ------------------------------------------------

                    else if (
                        sortMode.get() ==
                            SortMode.TopDown
                    ) {

                        if (
                            best == null ||
                            pos.getY() >
                                bestY ||
                            (
                                pos.getY() ==
                                    bestY &&
                                distance <
                                    bestDistance
                            )
                        ) {

                            best =
                                pos.immutable();

                            bestY =
                                pos.getY();

                            bestDistance =
                                distance;
                        }
                    }
                }
            }
        }

        return best;
    }

    // ============================================================
    // FIND ALL HIGHLIGHTABLE BEDROCK
    // ============================================================

    private void renderHighlightableBedrock(
        Render3DEvent event
    ) {

        if (
            mc.player == null ||
            mc.level == null
        ) {
            return;
        }

        BlockPos playerPos =
            mc.player.blockPosition();

        int radius =
            (int) Math.ceil(
                range.get()
            );

        double maxDistance =
            range.get() *
            range.get();

        for (
            int x = -radius;
            x <= radius;
            x++
        ) {

            for (
                int y = -radius;
                y <= radius;
                y++
            ) {

                for (
                    int z = -radius;
                    z <= radius;
                    z++
                ) {

                    BlockPos pos =
                        playerPos.offset(
                            x,
                            y,
                            z
                        );

                    // ------------------------------------------------
                    // BEDROCK ONLY
                    // ------------------------------------------------

                    if (!isBedrock(pos)) {
                        continue;
                    }

                    // ------------------------------------------------
                    // NEVER HIGHLIGHT BOTTOM LAYER
                    // ------------------------------------------------

                    if (
                        pos.getY() <=
                            mc.level.getMinY()
                    ) {
                        continue;
                    }

                    // ------------------------------------------------
                    // FLATTEN MODE
                    // ------------------------------------------------

                    if (
                        miningMode.get() ==
                            MiningMode.Flatten &&
                        pos.getY() <
                            mc.player.getBlockY()
                    ) {
                        continue;
                    }

                    // ------------------------------------------------
                    // RANGE
                    // ------------------------------------------------

                    double distance =
                        mc.player.distanceToSqr(
                            pos.getX() + 0.5,
                            pos.getY() + 0.5,
                            pos.getZ() + 0.5
                        );

                    if (
                        distance >
                            maxDistance
                    ) {
                        continue;
                    }

                    // ------------------------------------------------
                    // DON'T DRAW NORMAL TARGET TWICE
                    // ------------------------------------------------

                    if (
                        miningPos != null &&
                        miningPos.equals(pos)
                    ) {
                        continue;
                    }

                    // ------------------------------------------------
                    // DRAW HIGHLIGHT
                    // ------------------------------------------------

                    event.renderer.box(
                        pos,
                        sideColor.get(),
                        lineColor.get(),
                        shapeMode.get(),
                        0
                    );
                }
            }
        }
    }

    // ============================================================
    // ENCHANTED GOLDEN APPLE CHECK
    // ============================================================

    private boolean isUsingEnchantedGoldenApple() {

        if (mc.player == null) {
            return false;
        }

        if (!mc.player.isUsingItem()) {
            return false;
        }

        return mc.player
            .getUseItem()
            .is(
                Items.ENCHANTED_GOLDEN_APPLE
            );
    }

    // ============================================================
    // BEDROCK CHECK
    // ============================================================

    private boolean isBedrock(BlockPos pos) {

        if (mc.level == null) {
            return false;
        }

        BlockState state =
            mc.level.getBlockState(pos);

        return state.is(
            Blocks.BEDROCK
        );
    }

    // ============================================================
    // RANGE CHECK
    // ============================================================

    private boolean inRange(BlockPos pos) {

        if (mc.player == null) {
            return false;
        }

        double maxDistance =
            range.get() *
            range.get();

        double distance =
            mc.player.distanceToSqr(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
            );

        return distance <=
            maxDistance;
    }

    // ============================================================
    // STOP MINING
    // ============================================================

    private void stopMining() {

        if (
            mc.gameMode != null
        ) {
            mc.gameMode
                .stopDestroyBlock();
        }

        miningPos = null;

        renderTicks = 0;

        lastRenderPos = null;
    }

    // ============================================================
    // RENDER
    // ============================================================

    @EventHandler
    private void onRender(
        Render3DEvent event
    ) {

        if (mc.level == null) {
            return;
        }

        // --------------------------------------------------------
        // HIGHLIGHT ALL POSSIBLE BEDROCK
        // --------------------------------------------------------

        if (highlightBedrock.get()) {
            renderHighlightableBedrock(
                event
            );
        }

        // --------------------------------------------------------
        // NORMAL CURRENT-TARGET RENDER
        // --------------------------------------------------------

        if (!render.get()) {
            return;
        }

        if (miningPos == null) {
            return;
        }

        if (!isBedrock(miningPos)) {
            return;
        }

        if (
            miningPos.getY() <=
                mc.level.getMinY()
        ) {
            return;
        }

        // --------------------------------------------------------
        // RESET IF TARGET CHANGED
        // --------------------------------------------------------

        if (
            lastRenderPos == null ||
            !lastRenderPos.equals(
                miningPos
            )
        ) {

            lastRenderPos =
                miningPos.immutable();

            renderTicks = 0;
        }

        // --------------------------------------------------------
        // GROWING ANIMATION
        // --------------------------------------------------------

        double progress =
            (double) renderTicks /
            renderGrowTicks.get();

        progress =
            Math.max(
                0.0,
                Math.min(
                    1.0,
                    progress
                )
            );

        double minSize = 0.05;

        double size =
            minSize +
            (1.0 - minSize) *
            progress;

        // --------------------------------------------------------
        // CENTER
        // --------------------------------------------------------

        double centerX =
            miningPos.getX() + 0.5;

        double centerY =
            miningPos.getY() + 0.5;

        double centerZ =
            miningPos.getZ() + 0.5;

        double half =
            size / 2.0;

        double minX =
            centerX - half;

        double minY =
            centerY - half;

        double minZ =
            centerZ - half;

        double maxX =
            centerX + half;

        double maxY =
            centerY + half;

        double maxZ =
            centerZ + half;

        event.renderer.box(
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ,
            sideColor.get(),
            lineColor.get(),
            shapeMode.get(),
            0
        );
    }

    // ============================================================
    // SORT MODES
    // ============================================================

    public enum SortMode {
        Closest,
        Furthest,
        TopDown
    }

    // ============================================================
    // MINING MODES
    // ============================================================

    public enum MiningMode {
        All,
        Flatten
    }
}