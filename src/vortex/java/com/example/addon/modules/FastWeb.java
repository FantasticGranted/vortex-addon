package com.example.addon.modules;

import com.example.addon.QuinnAddon;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.world.level.block.Blocks;

public class FastWeb extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(
        new DoubleSetting.Builder()
            .name("speed")
            .description("How fast you move downward while sneaking in a cobweb.")
            .defaultValue(0.5)
            .min(0.01)
            .max(2.0)
            .sliderMin(0.01)
            .sliderMax(2.0)
            .build()
    );

    public FastWeb() {
        super(
            QuinnAddon.CATEGORY,
            "fast-web",
            "Move downward faster while holding Shift inside a cobweb."
        );
    }

    @Override
    public void onActivate() {
    }

    @Override
    public void onDeactivate() {
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        // Only activate while holding Shift.
        if (!mc.options.keyShift.isDown()) return;

        // Only activate while inside a cobweb.
        if (!isInCobweb()) return;

        /*
         * Keep the player's X/Z velocity unchanged,
         * while forcing the configured downward velocity.
         */
        mc.player.setDeltaMovement(
            mc.player.getDeltaMovement().x,
            -speed.get(),
            mc.player.getDeltaMovement().z
        );
    }

    private boolean isInCobweb() {
        if (mc.player == null || mc.level == null) return false;

        return mc.level
            .getBlockState(mc.player.blockPosition())
            .is(Blocks.COBWEB);
    }
}