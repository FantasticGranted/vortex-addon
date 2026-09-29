
package me.aleksilassila.litematica.printer.mixin.printer.litematica;

import fi.dy.masa.litematica.util.InventoryUtils;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(InventoryUtils.class)
public interface InventoryUtilsAccessor {
    @Invoker("getEmptyPickBlockableHotbarSlot")
    static int getEmptyPickBlockableHotbarSlot(Inventory inventory){
        return -1;
    }

    @Invoker("canPickToSlot")
    static boolean canPickToSlot(Inventory inventory, int slot) {
        return false;
    }

    @Accessor(remap = false)
    static int getNextPickSlotIndex() {
        throw new UnsupportedOperationException();
    }

    @Accessor(remap = false)
    static List<Integer> getPICK_BLOCKABLE_SLOTS() {
        throw new UnsupportedOperationException();
    }

    @Accessor(remap = false)
    static void setNextPickSlotIndex(int nextPickSlotIndex) {
        throw new UnsupportedOperationException();
    }
}
