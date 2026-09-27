package com.vortex.modules;

import com.vortex.Vortex;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class NoGlitchBlocks extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onlyPlacement = sgGeneral.add(new BoolSetting.Builder()
        .name("only-placement")
        .description("Only block glitchy placements, allow opening containers and using blocks with tools.")
        .defaultValue(true)
        .build()
    );

    public NoGlitchBlocks() {
        super(Vortex.CATEGORY, "no-glitch-blocks", "Cancels interaction with blocks that don't have a full hitbox, blocking the block placement glitch.");
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (mc.level == null || mc.player == null) return;

        BlockPos pos = event.result.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        if (state.isCollisionShapeFullBlock(mc.level, pos)) return;

        if (onlyPlacement.get() && !(mc.player.getMainHandItem().getItem() instanceof BlockItem)) return;

        event.setCancelled(true);
    }
}