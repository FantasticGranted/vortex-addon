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
import net.minecraft.world.item.ItemStack;
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
    private int originSlot = -1;
    private int targetSlot = -1;

    public KeyPearl() {
        super(Vortex.CATEGORY, "key-pearl", "Throws an ender pearl where you're looking using an alt swap.");
    }

    @Override
    public void onActivate() {
        swapSent = false;
        originSlot = -1;
        targetSlot = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null) {
            toggle();
            return;
        }

        if (mc.screen != null) return;

        if (mc.player.getMainHandItem().is(Items.ENDER_PEARL)) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            mc.player.swing(InteractionHand.MAIN_HAND);

            if (swapBack.get() && originSlot != -1) altSwap();
            toggle();
            return;
        }

        if (swapSent) {
            if (swapBack.get() && originSlot != -1) {
                ItemStack stack = mc.player.getInventory().getItem(targetSlot);
                if (stack.isEmpty() || stack.is(Items.ENDER_PEARL)) altSwap();
            }
            toggle();
            return;
        }

        if (mc.player.getOffhandItem().is(Items.ENDER_PEARL)) {
            mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
            mc.player.swing(InteractionHand.OFF_HAND);
            toggle();
            return;
        }

        int selected = mc.player.getInventory().getSelectedSlot();
        FindItemResult hotbar = InvUtils.findInHotbar(Items.ENDER_PEARL);
        int invSlot = -1;

        if (hotbar.found() && !hotbar.isOffhand()) {
            invSlot = hotbar.slot();
        } else {
            FindItemResult any = InvUtils.find(Items.ENDER_PEARL);
            if (any.found() && any.slot() >= 9 && any.slot() <= 35) invSlot = any.slot();
        }

        if (invSlot == -1 || invSlot == selected) {
            toggle();
            return;
        }

        targetSlot = selected;
        originSlot = invSlot <= 8 ? 36 + invSlot : invSlot;
        altSwap();
        swapSent = true;
    }

    private void altSwap() {
        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, originSlot, targetSlot, ContainerInput.SWAP, mc.player);
    }
}
