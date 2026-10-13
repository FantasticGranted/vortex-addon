package com.vortex.modules;

import com.vortex.Vortex;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class KeyPearl extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Puts the pearl back where it was after throwing.")
        .defaultValue(true)
        .build()
    );

    private boolean swapSent;
    private int pendingHandlerSlot = -1;

    public KeyPearl() {
        super(Vortex.CATEGORY, "key-pearl", "Throws an ender pearl where you're looking using an offhand swap.");
    }

    @Override
    public void onActivate() {
        swapSent = false;
        pendingHandlerSlot = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null) {
            toggle();
            return;
        }

        if (mc.gui.screen() != null) return;

        if (mc.player.getOffhandItem().is(Items.ENDER_PEARL)) {
            mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
            mc.player.swing(InteractionHand.OFF_HAND);

            if (swapBack.get() && pendingHandlerSlot != -1) {
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, pendingHandlerSlot, 40, ContainerInput.SWAP, mc.player);
            }

            toggle();
            return;
        }

        if (swapSent) {
            toggle();
            return;
        }

        FindItemResult pearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
        int inventorySlot;

        if (pearl.found() && !pearl.isOffhand()) {
            inventorySlot = pearl.slot();
        } else {
            FindItemResult any = InvUtils.find(Items.ENDER_PEARL);
            if (!any.found()) {
                toggle();
                return;
            }
            inventorySlot = any.slot();
        }

        pendingHandlerSlot = inventorySlot <= 8 ? 36 + inventorySlot : inventorySlot;
        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, pendingHandlerSlot, 40, ContainerInput.SWAP, mc.player);
        swapSent = true;
    }
}
