package com.vortex.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.vortex.StashFinder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class StashFinderCommand extends Command {
    public StashFinderCommand() {
        super("stashfind", "Cross-reference the material list with logged chests.", "sf");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            StashFinder.run(StashFinder.lastList, null);
            return SINGLE_SUCCESS;
        });

        builder.then(argument("item", StringArgumentType.greedyString()).executes(context -> {
            StashFinder.run(StashFinder.lastList, StringArgumentType.getString(context, "item"));
            return SINGLE_SUCCESS;
        }));
    }
}
