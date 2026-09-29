package me.aleksilassila.litematica.printer.utils;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//#if MC >= 12005
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.PotionContents;
//#else
//$$ import net.minecraft.world.item.alchemy.PotionUtils;
//#endif
import net.minecraft.world.item.alchemy.Potions;

public final class PotionStackUtils {
    private PotionStackUtils() {
    }

    public static ItemStack createWaterPotionStack() {
        //#if MC >= 12005
        return PotionContents.createItemStack(Items.POTION, Potions.WATER);
        //#else
        //$$ return PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
        //#endif
    }

    public static boolean isWaterPotion(ItemStack stack) {
        if (stack == null || !stack.is(Items.POTION)) {
            return false;
        }
        //#if MC >= 12005
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.is(Potions.WATER);
        //#else
        //$$ return PotionUtils.getPotion(stack) == Potions.WATER;
        //#endif
    }
}
