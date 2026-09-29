package com.example.addon;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class SoundRegistry implements ModInitializer {
    public static SoundEvent METAL_PIPE;
    public static SoundEvent VINE_BOOM;

    @Override
    public void onInitialize() {
        METAL_PIPE = register("metal_pipe");
        VINE_BOOM = register("vine_boom");
    }

    private static SoundEvent register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath("addon", name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }
}
