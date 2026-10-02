package com.example.addon.modules;

import com.example.addon.QuinnAddon;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AntiPhase extends Module {
    private final SettingGroup sgGeneral =
        settings.getDefaultGroup();

    private final SettingGroup sgTarget =
        settings.createGroup("Targeting");

    private final Setting<List<Block>> materials =
        sgGeneral.add(
            new BlockListSetting.Builder()
                .name("material")
                .description("Blocks to use for AntiPhase.")
                .defaultValue(
                    Blocks.LADDER,
                    Blocks.VINE,
                    Blocks.SCAFFOLDING,
                    Blocks.DARK_OAK_BUTTON
                )
                .build()
        );

    private final Setting<Integer> bpt =
        sgGeneral.add(
            new IntSetting.Builder()
                .name("blocks-per-tick")
                .description("Blocks per tick to place.")
                .defaultValue(2)
                .min(1)
                .max(6)
                .sliderMax(6)
                .build()
        );

    private final Setting<Integer> delay =
        sgGeneral.add(
            new IntSetting.Builder()
                .name("delay")
                .description("Delay between placements in milliseconds.")
                .defaultValue(50)
                .min(0)
                .sliderMax(500)
                .build()
        );

    private final Setting<Boolean> rotate =
        sgGeneral.add(
            new BoolSetting.Builder()
                .name("rotate")
                .description("Rotate towards the block when placing.")
                .defaultValue(true)
                .build()
        );

    private final Setting<Boolean> doubles =
        sgGeneral.add(
            new BoolSetting.Builder()
                .name("doubles")
                .description("Attempts to place blocks at the target's feet and head.")
                .defaultValue(false)
                .build()
        );

    private final Setting<Boolean> pauseOnEat =
        sgGeneral.add(
            new BoolSetting.Builder()
                .name("pause-on-eat")
                .description("Pause placement when using an item.")
                .defaultValue(false)
                .build()
        );

    private final Setting<Double> range =
        sgTarget.add(
            new DoubleSetting.Builder()
                .name("range")
                .description("Maximum target range for AntiPhase.")
                .defaultValue(4.5)
                .min(1.0)
                .sliderMin(1.0)
                .sliderMax(12.0)
                .build()
        );

    private final Setting<Boolean> ignoreFriends =
        sgTarget.add(
            new BoolSetting.Builder()
                .name("ignore-friends")
                .description("Do not target players on your friends list.")
                .defaultValue(true)
                .build()
        );

    private final Setting<Boolean> ignoreNaked =
        sgTarget.add(
            new BoolSetting.Builder()
                .name("ignore-naked")
                .description("Ignore players with no armor equipped.")
                .defaultValue(false)
                .build()
        );

    private long lastPlaceTime = 0;

    public AntiPhase() {
        super(
            QuinnAddon.CATEGORY,
            "antiphase",
            "Prevents your targets from phasing into blocks.\nFrom: Quinn"
        );
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (pauseOnEat.get() && mc.player.isUsingItem()) {
            return;
        }

        if (System.currentTimeMillis() - lastPlaceTime < delay.get()) {
            return;
        }

        List<Player> targets = findTargets();

        if (targets.isEmpty()) {
            return;
        }

        targets.sort(
            Comparator.comparingDouble(
                player -> mc.player.distanceToSqr(player)
            )
        );

        FindItemResult blockItem =
            InvUtils.findInHotbar(stack -> {
                if (!(stack.getItem() instanceof BlockItem block)) {
                    return false;
                }

                return materials.get().contains(block.getBlock());
            });

        if (!blockItem.found()) {
            return;
        }

        int placed = 0;

        for (Player target : targets) {
            if (placed >= bpt.get()) {
                break;
            }

            BlockPos targetPos =
                BlockPos.containing(
                    target.getX(),
                    target.getY(),
                    target.getZ()
                );

            if (targetPos.equals(
                BlockPos.containing(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ()
                )
            )) {
                continue;
            }

            /*
             * Place the first AntiPhase block at the target's feet.
             */
            if (tryPlace(targetPos, blockItem)) {
                placed++;
                lastPlaceTime = System.currentTimeMillis();
            }

            /*
             * If doubles is enabled, also try to place a block
             * at the target's head/face position.
             */
            if (doubles.get() && placed < bpt.get()) {
                BlockPos facePos = targetPos.above();

                if (tryPlace(facePos, blockItem)) {
                    placed++;
                    lastPlaceTime = System.currentTimeMillis();
                }
            }
        }
    }

    private boolean tryPlace(
        BlockPos pos,
        FindItemResult blockItem
    ) {
        Block blockAtPos =
            mc.level
                .getBlockState(pos)
                .getBlock();

        if (materials.get().contains(blockAtPos)) {
            return false;
        }

        if (!mc.level
            .getBlockState(pos)
            .canBeReplaced()) {

            return false;
        }

        return BlockUtils.place(
            pos,
            blockItem,
            rotate.get() ? 50 : 0,
            false
        );
    }

    private List<Player> findTargets() {
        List<Player> list =
            new ArrayList<>();

        for (Player player : mc.level.players()) {
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

            if (ignoreNaked.get()
                && isNaked(player)) {

                continue;
            }

            if (mc.player.distanceTo(player) > range.get()) {
                continue;
            }

            list.add(player);
        }

        return list;
    }

    private boolean isNaked(Player player) {
        return isEmpty(
            player.getItemBySlot(EquipmentSlot.HEAD)
        )
        && isEmpty(
            player.getItemBySlot(EquipmentSlot.CHEST)
        )
        && isEmpty(
            player.getItemBySlot(EquipmentSlot.LEGS)
        )
        && isEmpty(
            player.getItemBySlot(EquipmentSlot.FEET)
        );
    }

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }
}