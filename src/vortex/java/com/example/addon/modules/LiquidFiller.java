package com.example.addon.modules;

import com.example.addon.QuinnAddon;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class LiquidFiller extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("Filter");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // =========================
    // General
    // =========================

    private final Setting<LiquidMode> liquidMode = sgGeneral.add(
        new EnumSetting.Builder<LiquidMode>()
            .name("liquid")
            .description("Which liquids to fill.")
            .defaultValue(LiquidMode.Both)
            .build()
    );

    private final Setting<Double> range = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("range")
            .description("Maximum distance from the player to fill liquids.")
            .defaultValue(4.5)
            .min(1.0)
            .sliderMax(8.0)
            .decimalPlaces(1)
            .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(
        new IntSetting.Builder()
            .name("delay")
            .description("Delay between placement cycles, in ticks.")
            .defaultValue(0)
            .min(0)
            .max(20)
            .sliderMax(20)
            .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(
        new IntSetting.Builder()
            .name("blocks-per-tick")
            .description("Maximum number of blocks to place per placement cycle.")
            .defaultValue(1)
            .min(1)
            .max(5)
            .sliderMax(5)
            .build()
    );

    private final Setting<SortMode> sortMode = sgGeneral.add(
        new EnumSetting.Builder<SortMode>()
            .name("sort")
            .description("How liquid blocks are prioritized.")
            .defaultValue(SortMode.Closest)
            .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(
        new BoolSetting.Builder()
            .name("rotate")
            .description("Rotate toward the target when placing.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> sourceOnly = sgGeneral.add(
        new BoolSetting.Builder()
            .name("source-only")
            .description("Only fill source liquid blocks.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> pauseWhileEating = sgGeneral.add(
        new BoolSetting.Builder()
            .name("pause-while-eating")
            .description("Pause placing blocks while eating or drinking.")
            .defaultValue(true)
            .build()
    );

    // =========================
    // Filter
    // =========================

    private final Setting<FilterMode> filterMode = sgFilter.add(
        new EnumSetting.Builder<FilterMode>()
            .name("filter-mode")
            .description("Choose whether the configured blocks are whitelisted or blacklisted.")
            .defaultValue(FilterMode.Whitelist)
            .build()
    );

    private final Setting<List<Block>> blocks = sgFilter.add(
        new BlockListSetting.Builder()
            .name("blocks")
            .description("Blocks used by the whitelist or blacklist.")
            .defaultValue(
                Blocks.NETHERRACK,
                Blocks.GRASS_BLOCK,
                Blocks.STONE,
                Blocks.COBBLESTONE,
                Blocks.SPONGE
            )
            .build()
    );

    // =========================
    // Render
    // =========================

    private final Setting<Boolean> render = sgRender.add(
        new BoolSetting.Builder()
            .name("render")
            .description("Render a bounding box around blocks placed by Liquid Filler.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> renderTime = sgRender.add(
        new IntSetting.Builder()
            .name("render-time")
            .description("How long placed blocks remain rendered, in ticks.")
            .defaultValue(10)
            .min(1)
            .max(40)
            .sliderMax(40)
            .visible(render::get)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the bounding box is rendered.")
            .defaultValue(ShapeMode.Both)
            .visible(render::get)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(
        new ColorSetting.Builder()
            .name("side-color")
            .description("Color of the inside of the bounding box.")
            .defaultValue(new SettingColor(40, 120, 255, 35))
            .visible(() -> render.get() && shapeMode.get().sides())
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(
        new ColorSetting.Builder()
            .name("line-color")
            .description("Color of the bounding box outline.")
            .defaultValue(new SettingColor(40, 160, 255, 255))
            .visible(() -> render.get() && shapeMode.get().lines())
            .build()
    );

    // =========================
    // Internal state
    // =========================

    private final List<PlacedBlock> placedBlocks = new ArrayList<>();

    private int tickCounter;

    public LiquidFiller() {
        super(
            QuinnAddon.CATEGORY,
            "liquid-filler",
            "Automatically fills water and lava blocks within range."
        );
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        placedBlocks.clear();
    }

    @Override
    public void onDeactivate() {
        placedBlocks.clear();
    }

    // =========================
    // Tick
    // =========================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        updateRenderPositions();

        if (pauseWhileEating.get() && isEatingOrDrinking()) {
            return;
        }

        if (tickCounter < delay.get()) {
            tickCounter++;
            return;
        }

        tickCounter = 0;

        /*
         * Find a block that is allowed by the
         * whitelist/blacklist.
         */
        FindItemResult blockItem = findPlaceableBlock();

        if (!blockItem.found()) {
            return;
        }

        List<BlockPos> targets = findTargets();

        if (targets.isEmpty()) {
            return;
        }

        int placed = 0;

        for (BlockPos pos : targets) {
            if (placed >= blocksPerTick.get()) {
                break;
            }

            if (!isValidTarget(pos)) {
                continue;
            }

            if (BlockUtils.place(
                pos,
                blockItem,
                rotate.get(),
                0,
                true,
                true,
                true
            )) {
                placedBlocks.add(
                    new PlacedBlock(
                        pos,
                        renderTime.get()
                    )
                );

                placed++;
            }
        }
    }

    // =========================
    // Eating / drinking
    // =========================

    private boolean isEatingOrDrinking() {
        if (mc.player == null || !mc.player.isUsingItem()) {
            return false;
        }

        ItemStack stack = mc.player.getUseItem();

        if (stack.isEmpty()) {
            return false;
        }

        return stack.get(DataComponents.FOOD) != null
            || stack.get(DataComponents.CONSUMABLE) != null;
    }

    // =========================
    // Find block to place
    // =========================

    private FindItemResult findPlaceableBlock() {
        return InvUtils.findInHotbar(stack ->
            !stack.isEmpty()
                && stack.getItem() instanceof BlockItem
                && isAllowedByBlockFilter(
                    ((BlockItem) stack.getItem()).getBlock()
                )
        );
    }

    // =========================
    // Find targets
    // =========================

    private List<BlockPos> findTargets() {
        List<BlockPos> targets = new ArrayList<>();

        BlockPos playerPos = mc.player.blockPosition();

        int radius = (int) Math.ceil(range.get());

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);

                    if (!isValidTarget(pos)) {
                        continue;
                    }

                    if (!PlayerUtils.isWithin(
                        pos.getCenter(),
                        range.get()
                    )) {
                        continue;
                    }

                    targets.add(pos);
                }
            }
        }

        sortTargets(targets);

        return targets;
    }

    // =========================
    // Target validation
    // =========================

    private boolean isValidTarget(BlockPos pos) {
        if (mc.level == null) {
            return false;
        }

        BlockState state = mc.level.getBlockState(pos);

        if (!isLiquid(state)) {
            return false;
        }

        if (!isCorrectLiquid(state)) {
            return false;
        }

        if (sourceOnly.get() && !isSourceLiquid(state)) {
            return false;
        }

        /*
         * IMPORTANT:
         *
         * The whitelist/blacklist is NOT checked here.
         * This block is the liquid being replaced, not
         * the block that we are placing.
         *
         * The filter is checked in findPlaceableBlock()
         * against the actual BlockItem being placed.
         */

        if (!state.canBeReplaced()) {
            return false;
        }

        return true;
    }

    private boolean isLiquid(BlockState state) {
        return !state.getFluidState().isEmpty();
    }

    private boolean isCorrectLiquid(BlockState state) {
        boolean water =
            state.getFluidState().is(Fluids.WATER)
                || state.getFluidState().is(Fluids.FLOWING_WATER);

        boolean lava =
            state.getFluidState().is(Fluids.LAVA)
                || state.getFluidState().is(Fluids.FLOWING_LAVA);

        return switch (liquidMode.get()) {
            case Water -> water;
            case Lava -> lava;
            case Both -> water || lava;
        };
    }

    private boolean isSourceLiquid(BlockState state) {
        return state.getFluidState().isSource();
    }

    // =========================
    // Whitelist / blacklist
    // =========================

    private boolean isAllowedByBlockFilter(Block block) {
        boolean contains = blocks.get().contains(block);

        return switch (filterMode.get()) {
            /*
             * Only blocks in the list can be used.
             */
            case Whitelist -> contains;

            /*
             * Blocks in the list cannot be used.
             */
            case Blacklist -> !contains;
        };
    }

    // =========================
    // Sorting
    // =========================

    private void sortTargets(List<BlockPos> targets) {
        switch (sortMode.get()) {
            case Closest -> targets.sort(
                Comparator.comparingDouble(
                    this::distanceToPlayer
                )
            );

            case Furthest -> targets.sort(
                Comparator.comparingDouble(
                    this::distanceToPlayer
                ).reversed()
            );

            case TopDown -> targets.sort(
                Comparator.comparingInt((BlockPos p) -> p.getY())
                    .reversed()
                    .thenComparingDouble(this::distanceToPlayer)
            );

            case BottomUp -> targets.sort(
                Comparator.comparingInt((BlockPos p) -> p.getY())
                    .thenComparingDouble(this::distanceToPlayer)
            );
        }
    }

    private double distanceToPlayer(BlockPos pos) {
        return mc.player.getEyePosition().distanceToSqr(
            pos.getCenter()
        );
    }

    // =========================
    // Rendering
    // =========================

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) {
            return;
        }

        if (placedBlocks.isEmpty()) {
            return;
        }

        for (PlacedBlock placed : placedBlocks) {
            event.renderer.box(
                placed.pos,
                sideColor.get(),
                lineColor.get(),
                shapeMode.get(),
                0
            );
        }
    }

    private void updateRenderPositions() {
        if (placedBlocks.isEmpty()) {
            return;
        }

        for (int i = placedBlocks.size() - 1; i >= 0; i--) {
            PlacedBlock placed = placedBlocks.get(i);

            placed.ticksRemaining--;

            if (placed.ticksRemaining <= 0) {
                placedBlocks.remove(i);
            }
        }
    }

    // =========================
    // Rendered block
    // =========================

    private static class PlacedBlock {
        private final BlockPos pos;
        private int ticksRemaining;

        private PlacedBlock(BlockPos pos, int ticksRemaining) {
            this.pos = pos.immutable();
            this.ticksRemaining = ticksRemaining;
        }
    }

    // =========================
    // Enums
    // =========================

    public enum LiquidMode {
        Water,
        Lava,
        Both
    }

    public enum SortMode {
        Closest,
        Furthest,
        TopDown,
        BottomUp
    }

    public enum FilterMode {
        Whitelist,
        Blacklist
    }
}
