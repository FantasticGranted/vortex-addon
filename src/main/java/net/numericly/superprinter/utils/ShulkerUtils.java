package net.numericly.superprinter.utils;

import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class ShulkerUtils {

    private static final long OPEN_TIMEOUT_MS = 2000;
    private static final long RETRY_COOLDOWN_MS = 1500;

    private static int shulkerSlot = -1;
    private static int hotbarSlot = -1;
    private static long openedAt = 0;
    private static long cooldownUntil = 0;
    private static Item target;

    public static boolean withdrawing() {
        return hotbarSlot != -1;
    }

    public static boolean withdraw(Item item) {
        assert mc.player != null;

        if (mc.screen != null && !withdrawing()) {
            return false;
        }

        if (withdrawing()) {
            if (target != item) {
                closeAndRestore();
                return false;
            }

            if (mc.screen instanceof ShulkerBoxScreen) {
                boolean moved = false;

                for (int i = 0; i < 27; i++) {
                    ItemStack stack = mc.player.containerMenu.getSlot(i).getItem();

                    if (stack.getItem() == item) {
                        InvUtils.shiftClick().slotId(i);
                        moved = true;
                    }
                }

                if (moved) {
                    closeAndRestore();
                }
            } else if (mc.player.containerMenu != mc.player.inventoryMenu) {
                closeAndRestore();
            } else if (System.currentTimeMillis() - openedAt > OPEN_TIMEOUT_MS) {
                closeAndRestore();
            }

            return false;
        }

        if (System.currentTimeMillis() < cooldownUntil) {
            return false;
        }

        int slot = findShulkerWith(item);

        if (slot == -1) {
            return false;
        }

        int to = findFreeHotbarSlot();

        if (to == -1) {
            return false;
        }

        InvUtils.move().from(slot).toHotbar(to);

        InventoryManager.setSlotUpdate(to);
        InventoryManager.setSlotUpdate(slot);

        shulkerSlot = slot;
        hotbarSlot = to;
        target = item;
        openedAt = System.currentTimeMillis();

        mc.player.getInventory().setSelectedSlot(to);

        mc.getConnection().getConnection().send(
            new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, mc.player.getYRot(), mc.player.getXRot())
        );

        return false;
    }

    public static void reset() {
        closeAndRestore();

        target = null;
        shulkerSlot = -1;
        hotbarSlot = -1;
    }

    private static void closeAndRestore() {
        assert mc.player != null;

        if (hotbarSlot != -1) {
            if (mc.player.containerMenu != mc.player.inventoryMenu) {
                mc.getConnection().getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));

                mc.player.containerMenu = mc.player.inventoryMenu;
                mc.setScreen(null);
            }

            if (shulkerSlot != -1) {
                InvUtils.move().fromHotbar(hotbarSlot).to(shulkerSlot);

                InventoryManager.setSlotUpdate(hotbarSlot);
                InventoryManager.setSlotUpdate(shulkerSlot);
            }

            cooldownUntil = System.currentTimeMillis() + RETRY_COOLDOWN_MS;
        }

        hotbarSlot = -1;
        shulkerSlot = -1;
        target = null;
    }

    private static int findShulkerWith(Item item) {
        for (int i = SlotUtils.MAIN_START; i <= SlotUtils.MAIN_END; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);

            if (!(stack.getItem() instanceof BlockItem blockItem)) continue;

            if (!(blockItem.getBlock() instanceof ShulkerBoxBlock)) continue;

            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);

            if (contents != null && contents.nonEmptyItemCopyStream().anyMatch(s -> s.getItem() == item)) {
                return i;
            }
        }

        return -1;
    }

    private static int findFreeHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);

            if (stack.isEmpty() && !InventoryManager.getSlotUpdate(i)) {
                return i;
            }
        }

        for (int i = 0; i < 9; i++) {
            if (!InventoryManager.getSlotUpdate(i)) {
                return i;
            }
        }

        return -1;
    }
}