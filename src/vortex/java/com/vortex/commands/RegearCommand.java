package com.vortex.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.vortex.modules.AutoRegear;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class RegearCommand extends Command {
    public RegearCommand() {
        super("regear", "Triggers the Auto Regear module.", "rg");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            AutoRegear.requestRegear();
            return SINGLE_SUCCESS;
        });
    }
}