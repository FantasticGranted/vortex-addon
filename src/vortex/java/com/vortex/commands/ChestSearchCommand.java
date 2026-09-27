package com.vortex.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.vortex.ChestSearchData;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import java.util.List;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class ChestSearchCommand extends Command {
    public ChestSearchCommand() {
        super("chestsearch", "Search logged chests for items.", "cs");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            info("Usage: .chestsearch <item name> or .cs <item name>");
            info("Example: .cs diamond");
            return SINGLE_SUCCESS;
        });

        builder.then(argument("item", StringArgumentType.greedyString()).executes(context -> {
            String query = StringArgumentType.getString(context, "item");
            searchAndPrint(query);
            return SINGLE_SUCCESS;
        }));
    }

    private void searchAndPrint(String query) {
        ChestSearchData data = ChestSearchData.getInstance();
        data.load();
        List<ChestSearchData.ChestEntry> results = data.search(query);

        if (results.isEmpty()) {
            warning("No chests found containing \"" + query + "\".");
            info("Total chests logged: " + data.size() + ". Use the Chest-Search module to scan more.");
            return;
        }

        info("Found " + results.size() + " chest(s) containing \"" + query + "\":");

        for (ChestSearchData.ChestEntry chest : results) {
            MutableComponent header = Component.literal(" > ")
                .append(Component.literal("[" + chest.x + ", " + chest.y + ", " + chest.z + "]")
                    .withStyle(ChatFormatting.AQUA)
                    .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand(".goto " + chest.x + " " + chest.y + " " + chest.z))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to teleport (.goto)")))
                    ));

            int totalItems = 0;
            StringBuilder itemsStr = new StringBuilder();
            boolean first = true;
            for (ChestSearchData.ItemEntry item : chest.items) {
                totalItems += item.count;
                try {
                    Identifier id = Identifier.tryParse(item.itemId);
                    if (id != null) {
                        String name = BuiltInRegistries.ITEM.getOptional(id).map(it -> it.getName(it.getDefaultInstance()).getString()).orElse("");
                        if (!first) itemsStr.append(", ");
                        itemsStr.append(name).append(" x").append(item.count);
                        first = false;
                    }
                } catch (Exception ignored) {}
            }

            header.append(Component.literal(" (" + totalItems + " items)").withStyle(ChatFormatting.GRAY));
            info(header);

            MutableComponent itemsText = Component.literal("     Items: ").withStyle(ChatFormatting.DARK_GREEN)
                .append(Component.literal(itemsStr.toString()).withStyle(ChatFormatting.WHITE));
            info(itemsText);
        }
    }
}