package com.vortex.modules;

import com.vortex.Vortex;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.PlayerModelPart;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class SkinBlink extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> speed = sgGeneral.add(new IntSetting.Builder()
        .name("speed")
        .description("Blink speed in ticks.")
        .defaultValue(5)
        .min(1)
        .max(50)
        .sliderRange(1, 20)
        .build()
    );

    private int tick;

    public SkinBlink() {
        super(Vortex.CATEGORY, "skin-blink", "Rapidly toggles your skin layers to make you look like you're blinking.");
    }

    @Override
    public void onActivate() {
        tick = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.options == null) return;

        if (++tick < speed.get()) return;
        tick = 0;

        for (PlayerModelPart part : PlayerModelPart.values()) {
            mc.options.setModelPart(part, !mc.options.isModelPartEnabled(part));
        }
    }
}