package com.vortex.printer.utils;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

public final class ToolUtils {

    public static ItemStack getBestTool(Set<ItemStack> tools, BlockState targetBlock) {
        float bestScore = 1;
        ItemStack bestStack = null;
        for (ItemStack tool : tools) {
            if (tool.getDestroySpeed(targetBlock) > bestScore) {
                bestScore = tool.getDestroySpeed(targetBlock);
                bestStack = tool;
            }
        }
        if (bestStack == null) {
            for (ItemStack tool : tools) {
                if (tool.is(holder -> holder.is(ItemTags.PICKAXES))) {
                    return tool;
                }
            }
        }
        return bestStack;
    }

    public static boolean isTool(ItemStack itemStack) {
        if (itemStack.is(holder -> holder.is(ItemTags.PICKAXES))
            || itemStack.is(holder -> holder.is(ItemTags.AXES))
            || itemStack.is(holder -> holder.is(ItemTags.SHOVELS))
            || itemStack.is(holder -> holder.is(ItemTags.HOES))
            || itemStack.getItem() instanceof ShearsItem) {
            return true;
        }
        return false;
    }
}
