package me.aleksilassila.litematica.printer.integration.quickshulker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

public class QuickShulkerSupport {
    private static final Minecraft client = Minecraft.getInstance();

    public static int switchPlayerInvToHotbarAir(int slot) {
        if (client.player == null) return -1;
        LocalPlayer player = client.player;
        AbstractContainerMenu sc = player.containerMenu;
        NonNullList<Slot> slots = sc.slots;
        int i = sc.equals(player.inventoryMenu) ? 9 : 0;
        int playerSlotOrdinal = 0;
        for (; i < slots.size(); i++) {
            if (!(slots.get(i).container instanceof Inventory)) {
                continue;
            }
            if (slots.get(i).getItem().isEmpty()) {
                fi.dy.masa.malilib.util.InventoryUtils.swapSlots(sc, i, slot);
                return playerSlotOrdinal < 27 ? playerSlotOrdinal + 9 : playerSlotOrdinal - 27;
            }
            playerSlotOrdinal++;
        }
        return -1;
    }

}
