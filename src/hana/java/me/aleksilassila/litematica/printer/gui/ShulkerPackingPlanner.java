package me.aleksilassila.litematica.printer.gui;

import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ShulkerPackingPlanner {
    public static final int SLOTS_PER_SHULKER = 27;

    private ShulkerPackingPlanner() {
    }

    public record LoadItem(String name, int units, int slots) {
    }

    public record Load(List<LoadItem> items) {
        public String describe(int index) {
            StringBuilder builder = new StringBuilder("#").append(index).append(": ");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) builder.append(", ");
                LoadItem item = items.get(i);
                builder.append(item.name()).append(" x").append(item.units());
            }
            return builder.toString();
        }
    }

    private static final class PendingLoad {
        final List<LoadItem> items = new ArrayList<>();
        int usedSlots;
    }

    public static List<Load> plan(MaterialListBase list) {
        int multiplier = Math.max(1, list.getMultiplier());
        List<Needed> needed = new ArrayList<>();
        for (MaterialListEntry entry : list.getMaterialsFiltered(true)) {
            int missing = entry.getCountMissing();
            if (missing <= 0) continue;
            needed.add(new Needed(entry.getStack(), missing * multiplier));
        }
        needed.sort(Comparator.comparingInt((Needed item) -> item.units).reversed());

        List<PendingLoad> pending = new ArrayList<>();
        PendingLoad current = null;

        for (Needed item : needed) {
            int maxStack = Math.max(1, item.stack().getItem().getDefaultMaxStackSize());
            int remaining = item.units();
            String name = item.stack().getHoverName().getString();

            while (remaining > 0) {
                if (current == null || current.usedSlots >= SLOTS_PER_SHULKER) {
                    current = new PendingLoad();
                    pending.add(current);
                }
                int slotsLeft = SLOTS_PER_SHULKER - current.usedSlots;
                int take = Math.min(remaining, slotsLeft * maxStack);
                int slotsUsed = (take + maxStack - 1) / maxStack;
                current.items.add(new LoadItem(name, take, slotsUsed));
                current.usedSlots += slotsUsed;
                remaining -= take;
            }
        }

        List<Load> loads = new ArrayList<>(pending.size());
        for (PendingLoad load : pending) {
            loads.add(new Load(List.copyOf(load.items)));
        }
        return loads;
    }

    private record Needed(ItemStack stack, int units) {
    }
}
