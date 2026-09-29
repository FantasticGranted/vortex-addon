package com.example.addon.modules;

import com.example.addon.QuinnAddon;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MassInstaMine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<ListMode> listMode = sgGeneral.add(
        new EnumSetting.Builder<ListMode>()
            .name("list-mode")
            .description("Whether to blacklist or whitelist blocks.")
            .defaultValue(ListMode.Blacklist)
            .build()
    );

    private final Setting<List<Block>> blocksToSkip = sgGeneral.add(
        new BlockListSetting.Builder()
            .name("blocks-to-skip")
            .description("Skips instamining these blocks.")
            .build()
    );

    private final Setting<List<Block>> blocksToBreakList = sgGeneral.add(
        new BlockListSetting.Builder()
            .name("blocks-to-break")
            .description("Only instamines these blocks in whitelist mode.")
            .build()
    );

    private final Setting<Integer> radius = sgGeneral.add(
        new IntSetting.Builder()
            .name("mine-radius")
            .description("Radius around the player to mine blocks.")
            .defaultValue(4)
            .min(1)
            .max(6)
            .sliderRange(1, 6)
            .build()
    );

    private final Setting<Integer> height = sgGeneral.add(
        new IntSetting.Builder()
            .name("mine-height")
            .description("Height range above and below the player.")
            .defaultValue(2)
            .min(1)
            .max(6)
            .sliderRange(1, 6)
            .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(
        new BoolSetting.Builder()
            .name("swing-hand")
            .description("Swings the hand when instamining.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(
        new BoolSetting.Builder()
            .name("rotate")
            .description("Rotates toward the mined block.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> dontMineBelowFeet = sgGeneral.add(
        new BoolSetting.Builder()
            .name("dont-mine-below-feet")
            .description("Prevents mining blocks below your feet.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> render = sgRender.add(
        new BoolSetting.Builder()
            .name("render")
            .description("Renders blocks being mined.")
            .defaultValue(true)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(
        new ColorSetting.Builder()
            .name("side-color")
            .defaultValue(new SettingColor(225, 25, 25, 45))
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(
        new ColorSetting.Builder()
            .name("line-color")
            .defaultValue(new SettingColor(225, 25, 25, 255))
            .build()
    );

    private final List<BlockPos> blocksToBreak = new ArrayList<>();

    private Direction direction = Direction.UP;
    private boolean shouldMine;
    private boolean hasSentBurst;
    private int mineTimer;
    private BlockPos lastMinedPos;

    public MassInstaMine() {
        super(
            QuinnAddon.CATEGORY,
            "mass-insta-mine",
            "Mines nearby blocks when you start mining a block. Credits to H_ux, Discord h.u.x."
        );
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    private void reset() {
        blocksToBreak.clear();

        direction = Direction.UP;
        shouldMine = false;
        hasSentBurst = false;
        mineTimer = 0;
        lastMinedPos = null;
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (mc.player == null || mc.level == null) return;

        if (!(event.packet instanceof ServerboundPlayerActionPacket packet)) return;

        if (packet.getAction() != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            return;
        }

        ItemStack stack = mc.player.getMainHandItem();

        if (!isTool(stack)) return;

        direction = packet.getDirection();
        lastMinedPos = packet.getPos();

        if (!shouldMine) {
            shouldMine = true;
            hasSentBurst = false;
            mineTimer = 0;

            findBlocksToMine();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }

        if (!shouldMine) return;

        blocksToBreak.removeIf(pos ->
            mc.level.getBlockState(pos).isAir()
                || !shouldBreak(pos)
        );

        if (blocksToBreak.isEmpty()) {
            shouldMine = false;
            hasSentBurst = false;
            return;
        }

        if (!hasSentBurst) {
            hasSentBurst = true;

            sendBreakPackets();

            mineTimer = 5;
        }

        if (mineTimer > 0) {
            mineTimer--;

            if (mineTimer == 0) {
                shouldMine = false;
                hasSentBurst = false;
                blocksToBreak.clear();
            }
        }
    }

    private void findBlocksToMine() {
        blocksToBreak.clear();

        if (mc.player == null || mc.level == null) return;

        BlockPos center = mc.player.blockPosition();

        int r = radius.get();
        int h = height.get();

        for (int x = -r; x <= r; x++) {
            for (int y = -h; y <= h; y++) {
                for (int z = -r; z <= r; z++) {

                    BlockPos pos = center.offset(x, y, z);

                    if (dontMineBelowFeet.get()
                        && pos.getY() < center.getY()) {
                        continue;
                    }

                    if (!shouldBreak(pos)) continue;

                    blocksToBreak.add(pos);
                }
            }
        }

        blocksToBreak.sort(
            Comparator.comparingDouble(
                pos -> pos.distSqr(center)
            )
        );
    }

    private boolean shouldBreak(BlockPos pos) {
        if (mc.level == null) return false;

        BlockState state = mc.level.getBlockState(pos);

        if (state.isAir()) return false;

        if (!BlockUtils.canBreak(pos)) return false;

        Block block = state.getBlock();

        if (listMode.get() == ListMode.Blacklist) {
            return !blocksToSkip.get().contains(block);
        }

        return blocksToBreakList.get().contains(block);
    }

    private void sendBreakPackets() {
        if (mc.getConnection() == null) return;

        Direction dir = direction == null
            ? Direction.UP
            : direction;

        for (BlockPos pos : blocksToBreak) {
            if (mc.level == null) return;

            if (mc.level.getBlockState(pos).isAir()) {
                continue;
            }

            if (!shouldBreak(pos)) {
                continue;
            }

            if (rotate.get()) {
                Rotations.rotate(
                    Rotations.getYaw(pos),
                    Rotations.getPitch(pos)
                );
            }

            mc.getConnection().send(
                new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    pos,
                    dir
                )
            );

            if (swing.get()) {
                mc.getConnection().send(
                    new ServerboundSwingPacket(InteractionHand.MAIN_HAND)
                );

                if (mc.player != null) {
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }
            }

            mc.getConnection().send(
                new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    pos,
                    dir
                )
            );
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;

        for (BlockPos pos : blocksToBreak) {
            event.renderer.box(
                pos,
                sideColor.get(),
                lineColor.get(),
                shapeMode.get(),
                0
            );
        }
    }

    public static boolean isTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        return stack.is(Items.WOODEN_PICKAXE)
            || stack.is(Items.STONE_PICKAXE)
            || stack.is(Items.IRON_PICKAXE)
            || stack.is(Items.GOLDEN_PICKAXE)
            || stack.is(Items.DIAMOND_PICKAXE)
            || stack.is(Items.NETHERITE_PICKAXE)

            || stack.is(Items.WOODEN_AXE)
            || stack.is(Items.STONE_AXE)
            || stack.is(Items.IRON_AXE)
            || stack.is(Items.GOLDEN_AXE)
            || stack.is(Items.DIAMOND_AXE)
            || stack.is(Items.NETHERITE_AXE)

            || stack.is(Items.WOODEN_SHOVEL)
            || stack.is(Items.STONE_SHOVEL)
            || stack.is(Items.IRON_SHOVEL)
            || stack.is(Items.GOLDEN_SHOVEL)
            || stack.is(Items.DIAMOND_SHOVEL)
            || stack.is(Items.NETHERITE_SHOVEL)

            || stack.is(Items.SHEARS);
    }

    public enum ListMode {
        Blacklist,
        Whitelist
    }
}
