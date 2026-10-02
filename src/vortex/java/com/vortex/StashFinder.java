package com.vortex;

import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StashFinder {
    public static MaterialListBase lastList;

    private static final int MAX_LINES = 80;

    public static void run(MaterialListBase list, String filter) {
        if (list != null) lastList = list;
        if (lastList == null) {
            chat("Open the material list first.");
            return;
        }

        ChestSearchData data = ChestSearchData.getInstance();
        data.load();
        if (data.size() == 0) {
            chat("No chests logged yet - turn on Chest-Search first.");
            return;
        }

        Map<String, int[]> stash = indexStash(data);
        List<MaterialListEntry> entries = lastList.getMaterialsAll();
        if (entries.isEmpty()) {
            chat("The material list is empty.");
            return;
        }

        String query = filter == null ? null : filter.trim().toLowerCase();
        int matched = 0, shown = 0, covered = 0, partial = 0, absent = 0, notNeeded = 0;

        for (MaterialListEntry entry : entries) {
            ItemStack stack = entry.getStack();
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            String display = stack.getHoverName().getString();

            if (query != null && !display.toLowerCase().contains(query) && !id.toLowerCase().contains(query)) continue;
            matched++;

            int need = entry.getCountMissing();
            int[] stashInfo = stash.get(id);
            int found = stashInfo == null ? 0 : stashInfo[0];
            int chests = stashInfo == null ? 0 : stashInfo[1];

            if (need <= 0) {
                notNeeded++;
            } else if (found >= need) {
                covered++;
            } else if (found > 0) {
                partial++;
            } else {
                absent++;
            }

            if (shown >= MAX_LINES) continue;
            shown++;

            MutableComponent line = Component.literal(display).withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" | need " + format(need) + " | stash " + format(found)
                    + " @ " + chests + (chests == 1 ? " chest" : " chests")).withStyle(ChatFormatting.GRAY))
                .withStyle(style -> style
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(".cs " + id))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(id + "\nClick to search logged chests (.cs)"))));
            addChat(line);
        }

        if (matched == 0) {
            chat(query == null ? "No materials in the list." : "No materials matched \"" + filter.trim() + "\".");
            return;
        }

        if (matched > shown) {
            addChat(Component.literal("... " + (matched - shown) + " more (use .stashfind <text>)")
                .withStyle(ChatFormatting.DARK_GRAY));
        }

        int needed = covered + partial + absent;
        MutableComponent summary = Component.literal("Stash check: ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(covered + " fully covered").withStyle(ChatFormatting.GREEN))
            .append(Component.literal(", " + partial + " partial").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(", " + absent + " not found").withStyle(ChatFormatting.RED))
            .append(Component.literal(" (of " + needed + " needed materials"
                + (notNeeded > 0 ? ", " + notNeeded + " already in stock" : "") + ")").withStyle(ChatFormatting.GRAY));
        addChat(summary);
    }

    private static Map<String, int[]> indexStash(ChestSearchData data) {
        Map<String, int[]> byId = new HashMap<>();
        for (ChestSearchData.ChestEntry chest : data.getAll()) {
            Set<String> counted = new HashSet<>();
            for (ChestSearchData.ItemEntry item : chest.items) {
                if (item.itemId == null || item.count <= 0) continue;
                int[] agg = byId.computeIfAbsent(item.itemId, k -> new int[2]);
                agg[0] += item.count;
                if (counted.add(item.itemId)) agg[1]++;
            }
        }
        return byId;
    }

    private static String format(long n) {
        return String.format("%,d", n);
    }

    private static void chat(String message) {
        addChat(Component.literal(message).withStyle(ChatFormatting.GRAY));
    }

    private static void addChat(Component component) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gui != null && mc.gui.getChat() != null) {
            mc.gui.getChat().addClientSystemMessage(component);
        }
    }
}
