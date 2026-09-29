package com.example.addon.modules;

import com.example.addon.QuinnAddon;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class SurroundPlus extends Module {
    private final SettingGroup sgGeneral =
        settings.getDefaultGroup();

    private final SettingGroup sgRender =
        settings.createGroup("Render");

    // ============================================================
    // GENERAL SETTINGS
    // ============================================================

    private final Setting<SurroundPlusBlock> surroundPlusBlock =
        sgGeneral.add(
            new EnumSetting.Builder<SurroundPlusBlock>()
                .name("surround-block")
                .description("Block used for Surround+.")
                .defaultValue(SurroundPlusBlock.Obsidian)
                .build()
        );

    private final Setting<Integer> surroundPlusPlaceDelay =
        sgGeneral.add(
            new IntSetting.Builder()
                .name("surround-place-delay")
                .description(
                    "Delay between Surround+ placement attempts."
                )
                .defaultValue(0)
                .min(0)
                .max(20)
                .sliderMin(0)
                .sliderMax(20)
                .build()
        );

    private final Setting<Boolean> antiFacePlace =
        sgGeneral.add(
            new BoolSetting.Builder()
                .name("anti-faceplace")
                .description(
                    "Places blocks above the Surround+ blocks."
                )
                .defaultValue(false)
                .build()
        );

    private final Setting<AntiFacePlaceBlock> antiFacePlaceBlock =
        sgGeneral.add(
            new EnumSetting.Builder<AntiFacePlaceBlock>()
                .name("anti-faceplace-block")
                .description(
                    "Block used for Anti-FacePlace."
                )
                .defaultValue(AntiFacePlaceBlock.Obsidian)
                .build()
        );

    private final Setting<Integer> antiFacePlacePlaceDelay =
        sgGeneral.add(
            new IntSetting.Builder()
                .name("anti-faceplace-place-delay")
                .description(
                    "Delay between Anti-FacePlace placement attempts."
                )
                .defaultValue(0)
                .min(0)
                .max(20)
                .sliderMin(0)
                .sliderMax(20)
                .build()
        );

    private final Setting<Boolean> breakCrystals =
        sgGeneral.add(
            new BoolSetting.Builder()
                .name("break-crystals")
                .description(
                    "Automatically attacks End Crystals blocking Surround+ placement."
                )
                .defaultValue(false)
                .build()
        );

    private final Setting<Boolean> disableOnYChange =
        sgGeneral.add(
            new BoolSetting.Builder()
                .name("disable-on-y-change")
                .description(
                    "Disable when your block Y level changes."
                )
                .defaultValue(true)
                .build()
        );

    private final Setting<Boolean> center =
        sgGeneral.add(
            new BoolSetting.Builder()
                .name("center")
                .description(
                    "Move to the center of the block you are standing in."
                )
                .defaultValue(false)
                .build()
        );

    private final Setting<Boolean> pauseWhileEating =
        sgGeneral.add(
            new BoolSetting.Builder()
                .name("pause-while-eating")
                .description(
                    "Pause placing while eating golden apples."
                )
                .defaultValue(true)
                .build()
        );

    private final Setting<SwitchMode> switchMode =
        sgGeneral.add(
            new EnumSetting.Builder<SwitchMode>()
                .name("switch-mode")
                .description(
                    "How the module changes the selected hotbar slot."
                )
                .defaultValue(SwitchMode.Silent)
                .build()
        );

    private final Setting<Boolean> rotate =
        sgGeneral.add(
            new BoolSetting.Builder()
                .name("rotate")
                .description(
                    "Rotate toward the block being placed."
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
                    "Render recently placed blocks."
                )
                .defaultValue(true)
                .build()
        );

    private final Setting<Integer> renderFade =
        sgRender.add(
            new IntSetting.Builder()
                .name("render-fade")
                .description(
                    "Render fade time in ticks."
                )
                .defaultValue(5)
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
                    "Render shape mode."
                )
                .defaultValue(ShapeMode.Both)
                .build()
        );

    private final Setting<SettingColor> surroundPlusSideColor =
        sgRender.add(
            new ColorSetting.Builder()
                .name("surround-side-color")
                .description(
                    "Surround+ side color."
                )
                .defaultValue(
                    new SettingColor(
                        255,
                        255,
                        255,
                        50
                    )
                )
                .build()
        );

    private final Setting<SettingColor> surroundPlusLineColor =
        sgRender.add(
            new ColorSetting.Builder()
                .name("surround-line-color")
                .description(
                    "Surround+ outline color."
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

    private final Setting<SettingColor> antiFacePlaceSideColor =
        sgRender.add(
            new ColorSetting.Builder()
                .name("anti-faceplace-side-color")
                .description(
                    "Anti-FacePlace side color."
                )
                .defaultValue(
                    new SettingColor(
                        255,
                        100,
                        100,
                        50
                    )
                )
                .build()
        );

    private final Setting<SettingColor> antiFacePlaceLineColor =
        sgRender.add(
            new ColorSetting.Builder()
                .name("anti-faceplace-line-color")
                .description(
                    "Anti-FacePlace outline."
                )
                .defaultValue(
                    new SettingColor(
                        255,
                        100,
                        100,
                        255
                    )
                )
                .build()
        );

    // ============================================================
    // STATE
    // ============================================================

    private BlockPos centerPos;

    private int startingY;

    private int surroundPlusDelayTimer;

    private int antiFacePlaceDelayTimer;

    private final List<RenderBlock> renderBlocks =
        new ArrayList<>();

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public SurroundPlus() {
        super(
            QuinnAddon.CATEGORY,
            "surround+",
            "Configurable Surround+ with Anti-FacePlace and optional crystal breaking."
        );
    }

    // ============================================================
    // ACTIVATE
    // ============================================================

    @Override
    public void onActivate() {
        if (
            mc.player == null ||
            mc.level == null
        ) {
            return;
        }

        startingY =
            mc.player.getBlockY();

        surroundPlusDelayTimer = 0;

        antiFacePlaceDelayTimer = 0;

        renderBlocks.clear();

        if (center.get()) {
            centerPlayer();
        } else {
            centerPos =
                mc.player
                    .blockPosition()
                    .immutable();
        }
    }

    // ============================================================
    // DEACTIVATE
    // ============================================================

    @Override
    public void onDeactivate() {
        centerPos = null;

        surroundPlusDelayTimer = 0;

        antiFacePlaceDelayTimer = 0;
    }

    // ============================================================
    // TICK
    // ============================================================

    @EventHandler
    private void onTick(
        TickEvent.Pre event
    ) {
        if (
            mc.player == null ||
            mc.level == null
        ) {
            return;
        }

        updateRenderBlocks();

        if (centerPos == null) {
            return;
        }

        // --------------------------------------------------------
        // Y LEVEL CHECK
        // --------------------------------------------------------

        if (
            disableOnYChange.get() &&
            mc.player.getBlockY() != startingY
        ) {
            toggle();
            return;
        }

        // --------------------------------------------------------
        // EATING CHECK
        // --------------------------------------------------------

        if (
            pauseWhileEating.get() &&
            isEating()
        ) {
            return;
        }

        // --------------------------------------------------------
        // TIMERS
        // --------------------------------------------------------

        if (surroundPlusDelayTimer > 0) {
            surroundPlusDelayTimer--;
        }

        if (antiFacePlaceDelayTimer > 0) {
            antiFacePlaceDelayTimer--;
        }

        // --------------------------------------------------------
        // SURROUND+
        // --------------------------------------------------------

        if (surroundPlusDelayTimer <= 0) {
            if (placeSurroundPlusBlocks()) {
                surroundPlusDelayTimer =
                    surroundPlusPlaceDelay.get();
            }
        }

        // --------------------------------------------------------
        // ANTI-FACEPLACE
        // --------------------------------------------------------

        if (
            antiFacePlace.get() &&
            antiFacePlaceDelayTimer <= 0
        ) {
            if (placeAntiFacePlaceBlocks()) {
                antiFacePlaceDelayTimer =
                    antiFacePlacePlaceDelay.get();
            }
        }
    }

    // ============================================================
    // EATING
    // ============================================================

    private boolean isEating() {
        if (mc.player == null) {
            return false;
        }

        if (!mc.player.isUsingItem()) {
            return false;
        }

        return mc.player
                .getUseItem()
                .is(Items.GOLDEN_APPLE)
            || mc.player
                .getUseItem()
                .is(
                    Items.ENCHANTED_GOLDEN_APPLE
                );
    }

    // ============================================================
    // CENTER
    // ============================================================

    private void centerPlayer() {
        if (mc.player == null) {
            return;
        }

        int blockX =
            mc.player.getBlockX();

        int blockY =
            mc.player.getBlockY();

        int blockZ =
            mc.player.getBlockZ();

        double targetX =
            blockX + 0.5;

        double targetZ =
            blockZ + 0.5;

        centerPos =
            new BlockPos(
                blockX,
                blockY,
                blockZ
            ).immutable();

        mc.player.setPos(
            targetX,
            mc.player.getY(),
            targetZ
        );
    }

    // ============================================================
    // SURROUND+ POSITIONS
    // ============================================================

    private List<BlockPos> getSurroundPlusPositions() {
        List<BlockPos> positions =
            new ArrayList<>();

        if (centerPos == null) {
            return positions;
        }

        positions.add(
            centerPos.north()
        );

        positions.add(
            centerPos.south()
        );

        positions.add(
            centerPos.east()
        );

        positions.add(
            centerPos.west()
        );

        return positions;
    }

    // ============================================================
    // ANTI-FACEPLACE POSITIONS
    // ============================================================

    private List<BlockPos> getAntiFacePlacePositions() {
        List<BlockPos> positions =
            new ArrayList<>();

        for (
            BlockPos pos :
            getSurroundPlusPositions()
        ) {
            positions.add(
                pos.above()
            );
        }

        return positions;
    }

    // ============================================================
    // PLACE SURROUND+
    // ============================================================

    private boolean placeSurroundPlusBlocks() {
        if (
            mc.level == null ||
            mc.player == null
        ) {
            return false;
        }

        List<BlockPos> missing =
            new ArrayList<>();

        for (
            BlockPos pos :
            getSurroundPlusPositions()
        ) {
            BlockState state =
                mc.level.getBlockState(pos);

            if (
                state.isAir() ||
                state.canBeReplaced()
            ) {
                missing.add(pos);
            }
        }

        if (missing.isEmpty()) {
            return false;
        }

        FindItemResult block =
            findSurroundPlusBlock();

        if (!block.found()) {
            return false;
        }

        boolean placed = false;

        for (BlockPos pos : missing) {

            /*
             * If an End Crystal is occupying the placement
             * position, break it first when enabled.
             */
            if (breakCrystals.get()) {
                breakCrystalAt(pos);
            }

            BlockState state =
                mc.level.getBlockState(pos);

            if (
                !state.isAir() &&
                !state.canBeReplaced()
            ) {
                continue;
            }

            if (
                placeWithRestore(
                    pos,
                    block,
                    false
                )
            ) {
                placed = true;
            }
        }

        return placed;
    }

    // ============================================================
    // PLACE ANTI-FACEPLACE
    // ============================================================

    private boolean placeAntiFacePlaceBlocks() {
        if (
            mc.level == null ||
            mc.player == null
        ) {
            return false;
        }

        if (!antiFacePlace.get()) {
            return false;
        }

        List<BlockPos> missing =
            new ArrayList<>();

        for (
            BlockPos pos :
            getAntiFacePlacePositions()
        ) {
            BlockState state =
                mc.level.getBlockState(pos);

            if (
                !state.isAir() &&
                !state.canBeReplaced()
            ) {
                continue;
            }

            BlockPos below =
                pos.below();

            if (!isSurroundPlusPosition(below)) {
                continue;
            }

            BlockState belowState =
                mc.level.getBlockState(below);

            if (belowState.isAir()) {
                continue;
            }

            missing.add(pos);
        }

        if (missing.isEmpty()) {
            return false;
        }

        FindItemResult block =
            findAntiFacePlaceBlock();

        if (!block.found()) {
            return false;
        }

        boolean placed = false;

        for (BlockPos pos : missing) {

            /*
             * Also handle crystals blocking the
             * Anti-FacePlace position.
             */
            if (breakCrystals.get()) {
                breakCrystalAt(pos);
            }

            BlockState state =
                mc.level.getBlockState(pos);

            if (
                !state.isAir() &&
                !state.canBeReplaced()
            ) {
                continue;
            }

            if (
                placeWithRestore(
                    pos,
                    block,
                    true
                )
            ) {
                placed = true;
            }
        }

        return placed;
    }

    // ============================================================
    // BREAK CRYSTAL
    // ============================================================

    private boolean breakCrystalAt(BlockPos pos) {
        if (
            mc.player == null ||
            mc.level == null ||
            mc.gameMode == null
        ) {
            return false;
        }

        /*
         * End Crystals are entities rather than blocks,
         * so we search the entity area around the target
         * block position.
         */
        AABB box =
            new AABB(pos);

        List<EndCrystal> crystals =
            mc.level.getEntitiesOfClass(
                EndCrystal.class,
                box,
                Entity::isAlive
            );

        if (crystals.isEmpty()) {
            return false;
        }

        boolean broken = false;

        for (
            EndCrystal crystal :
            crystals
        ) {
            if (!crystal.isAlive()) {
                continue;
            }

            mc.gameMode.attack(
                mc.player,
                crystal
            );

            mc.player.swing(
                net.minecraft.world.InteractionHand.MAIN_HAND
            );

            broken = true;
        }

        return broken;
    }

    // ============================================================
    // PLACE + RESTORE HOTBAR
    // ============================================================

    private boolean placeWithRestore(
        BlockPos pos,
        FindItemResult item,
        boolean antiFacePlace
    ) {
        if (
            mc.player == null ||
            mc.level == null
        ) {
            return false;
        }

        if (!item.found()) {
            return false;
        }

        BlockState state =
            mc.level.getBlockState(pos);

        if (
            !state.isAir() &&
            !state.canBeReplaced()
        ) {
            return false;
        }

        int oldSlot =
            mc.player
                .getInventory()
                .getSelectedSlot();

        int itemSlot =
            item.slot();

        boolean changedSlot =
            oldSlot != itemSlot;

        if (changedSlot) {
            if (
                switchMode.get() ==
                SwitchMode.Silent
            ) {
                InvUtils.swap(
                    itemSlot,
                    true
                );
            } else {
                InvUtils.swap(
                    itemSlot,
                    false
                );
            }
        }

        boolean placed = false;

        try {
            BlockState current =
                mc.level.getBlockState(pos);

            if (
                current.isAir() ||
                current.canBeReplaced()
            ) {
                placed =
                    BlockUtils.place(
                        pos,
                        item,
                        rotate.get(),
                        0,
                        true,
                        true,
                        true
                    );
            }
        } finally {
            if (changedSlot) {
                mc.player
                    .getInventory()
                    .setSelectedSlot(
                        oldSlot
                    );
            }
        }

        if (placed) {
            addRenderBlock(
                pos.immutable(),
                antiFacePlace
            );
        }

        return placed;
    }

    // ============================================================
    // POSITION CHECK
    // ============================================================

    private boolean isSurroundPlusPosition(
        BlockPos pos
    ) {
        for (
            BlockPos surroundPos :
            getSurroundPlusPositions()
        ) {
            if (
                surroundPos.equals(pos)
            ) {
                return true;
            }
        }

        return false;
    }

    // ============================================================
    // FIND SURROUND+ BLOCK
    // ============================================================

    private FindItemResult findSurroundPlusBlock() {
        return switch (
            surroundPlusBlock.get()
        ) {
            case EnderChest ->
                InvUtils.findInHotbar(
                    stack ->
                        stack.is(
                            Items.ENDER_CHEST
                        )
                );

            case Obsidian ->
                InvUtils.findInHotbar(
                    stack ->
                        stack.is(
                            Items.OBSIDIAN
                        )
                );

            case CryingObsidian ->
                InvUtils.findInHotbar(
                    stack ->
                        stack.is(
                            Items.CRYING_OBSIDIAN
                        )
                );

            case NetheriteBlock ->
                InvUtils.findInHotbar(
                    stack ->
                        stack.is(
                            Items.NETHERITE_BLOCK
                        )
                );

            case HeavyCore ->
                InvUtils.findInHotbar(
                    stack ->
                        stack.is(
                            Items.HEAVY_CORE
                        )
                );
        };
    }

    // ============================================================
    // FIND ANTI-FACEPLACE BLOCK
    // ============================================================

    private FindItemResult findAntiFacePlaceBlock() {
        return switch (
            antiFacePlaceBlock.get()
        ) {
            case EnderChest ->
                InvUtils.findInHotbar(
                    stack ->
                        stack.is(
                            Items.ENDER_CHEST
                        )
                );

            case Obsidian ->
                InvUtils.findInHotbar(
                    stack ->
                        stack.is(
                            Items.OBSIDIAN
                        )
                );

            case CryingObsidian ->
                InvUtils.findInHotbar(
                    stack ->
                        stack.is(
                            Items.CRYING_OBSIDIAN
                        )
                );

            case NetheriteBlock ->
                InvUtils.findInHotbar(
                    stack ->
                        stack.is(
                            Items.NETHERITE_BLOCK
                        )
                );

            case HeavyCore ->
                InvUtils.findInHotbar(
                    stack ->
                        stack.is(
                            Items.HEAVY_CORE
                        )
                );
        };
    }

    // ============================================================
    // RENDER
    // ============================================================

    private void addRenderBlock(
        BlockPos pos,
        boolean antiFacePlace
    ) {
        renderBlocks.add(
            new RenderBlock(
                pos,
                renderFade.get(),
                antiFacePlace
            )
        );
    }

    private void updateRenderBlocks() {
        for (
            int i = renderBlocks.size() - 1;
            i >= 0;
            i--
        ) {
            RenderBlock block =
                renderBlocks.get(i);

            block.ticksRemaining--;

            if (
                block.ticksRemaining <= 0
            ) {
                renderBlocks.remove(i);
            }
        }
    }

    @EventHandler
    private void onRender(
        Render3DEvent event
    ) {
        if (!render.get()) {
            return;
        }

        for (
            RenderBlock block :
            renderBlocks
        ) {
            float progress =
                (float) block.ticksRemaining /
                (float) renderFade.get();

            progress =
                Math.max(
                    0.0f,
                    Math.min(
                        1.0f,
                        progress
                    )
                );

            SettingColor side;
            SettingColor line;

            if (block.antiFacePlace) {
                side =
                    antiFacePlaceSideColor.get();

                line =
                    antiFacePlaceLineColor.get();
            } else {
                side =
                    surroundPlusSideColor.get();

                line =
                    surroundPlusLineColor.get();
            }

            SettingColor fadedSide =
                new SettingColor(
                    side.r,
                    side.g,
                    side.b,
                    (int) (
                        side.a * progress
                    )
                );

            SettingColor fadedLine =
                new SettingColor(
                    line.r,
                    line.g,
                    line.b,
                    (int) (
                        line.a * progress
                    )
                );

            event.renderer.box(
                block.pos,
                fadedSide,
                fadedLine,
                shapeMode.get(),
                0
            );
        }
    }

    // ============================================================
    // ENUMS
    // ============================================================

    public enum SurroundPlusBlock {
        EnderChest,
        Obsidian,
        CryingObsidian,
        NetheriteBlock,
        HeavyCore
    }

    public enum AntiFacePlaceBlock {
        EnderChest,
        Obsidian,
        CryingObsidian,
        NetheriteBlock,
        HeavyCore
    }

    public enum SwitchMode {
        Silent,
        Normal
    }

    // ============================================================
    // RENDER DATA
    // ============================================================

    private static class RenderBlock {
        private final BlockPos pos;

        private int ticksRemaining;

        private final boolean antiFacePlace;

        private RenderBlock(
            BlockPos pos,
            int ticksRemaining,
            boolean antiFacePlace
        ) {
            this.pos = pos;
            this.ticksRemaining =
                ticksRemaining;
            this.antiFacePlace =
                antiFacePlace;
        }
    }
}