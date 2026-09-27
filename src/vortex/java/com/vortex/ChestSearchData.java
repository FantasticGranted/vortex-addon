package com.vortex;

import com.google.gson.*;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class ChestSearchData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final File DATA_FILE = new File(MeteorClient.FOLDER, "ChestSearch/chests.json");
    private static ChestSearchData instance;

    private final List<ChestEntry> chests = new ArrayList<>();

    public static ChestSearchData getInstance() {
        if (instance == null) instance = new ChestSearchData();
        return instance;
    }

    public static class ChestEntry {
        public int x, y, z;
        public String world;
        public long lastScanned;
        public List<ItemEntry> items = new ArrayList<>();

        public BlockPos pos() { return new BlockPos(x, y, z); }
    }

    public static class ItemEntry {
        public String itemId;
        public int count;
    }

    public void addOrUpdate(BlockPos pos, String world, List<ItemStack> stacks) {
        ChestEntry existing = null;
        for (ChestEntry e : chests) {
            if (e.x == pos.getX() && e.y == pos.getY() && e.z == pos.getZ()) {
                existing = e;
                break;
            }
        }
        if (existing == null) {
            existing = new ChestEntry();
            existing.x = pos.getX();
            existing.y = pos.getY();
            existing.z = pos.getZ();
            existing.world = world;
            chests.add(existing);
        }
        existing.lastScanned = System.currentTimeMillis();
        existing.items.clear();
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            merged.merge(id, stack.getCount(), Integer::sum);
        }
        for (Map.Entry<String, Integer> e : merged.entrySet()) {
            ItemEntry entry = new ItemEntry();
            entry.itemId = e.getKey();
            entry.count = e.getValue();
            existing.items.add(entry);
        }
    }

    public List<ChestEntry> search(String query) {
        String lower = query.toLowerCase();
        return chests.stream()
            .filter(chest -> chest.items.stream()
                .anyMatch(item -> {
                    try {
                        Identifier id = Identifier.tryParse(item.itemId);
                        if (id == null) return false;
                        String displayName = BuiltInRegistries.ITEM.getOptional(id).map(it -> it.getName(it.getDefaultInstance()).getString().toLowerCase()).orElse("");
                        return displayName.contains(lower) || item.itemId.toLowerCase().contains(lower);
                    } catch (Exception e) {
                        return item.itemId.toLowerCase().contains(lower);
                    }
                }))
            .collect(Collectors.toList());
    }

    public List<ChestEntry> getAll() { return chests; }

    public void save() {
        try {
            DATA_FILE.getParentFile().mkdirs();
            Writer writer = new FileWriter(DATA_FILE);
            GSON.toJson(chests, writer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load() {
        chests.clear();
        if (!DATA_FILE.exists()) return;
        try {
            Reader reader = new FileReader(DATA_FILE);
            ChestEntry[] arr = GSON.fromJson(reader, ChestEntry[].class);
            reader.close();
            if (arr != null) chests.addAll(Arrays.asList(arr));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void clear() {
        chests.clear();
        save();
    }

    public int size() { return chests.size(); }
}