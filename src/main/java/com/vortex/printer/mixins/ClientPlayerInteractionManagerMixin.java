package com.vortex.printer.mixins;

import com.vortex.printer.interfaces.IClientPlayerInteractionManager;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = MultiPlayerGameMode.class, priority = 1002)
public abstract class ClientPlayerInteractionManagerMixin implements IClientPlayerInteractionManager {
    @Shadow
    public abstract void handleContainerInput(int syncId, int slotId, int button, ContainerInput actionType, Player player);
}
