package com.vortex.printer.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public final class ConfigDeserializer {

    private static BlockPos jsonToBlockPos(JsonObject obj) {
        return new BlockPos(obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt());
    }

    private static Vec3 jsonToVec3(JsonObject obj) {
        return new Vec3(obj.get("x").getAsDouble(), obj.get("y").getAsDouble(), obj.get("z").getAsDouble());
    }

    private static Pair<BlockPos, Vec3> jsonToBlockPosVecPair(JsonObject obj) {
        BlockPos pos = jsonToBlockPos(obj.getAsJsonObject("blockPos"));
        Vec3 openPos = jsonToVec3(obj.getAsJsonObject("openPos"));
        return new Pair<>(pos, openPos);
    }

    public static class ConfigData {
        public String type;
        public Pair<BlockPos, Vec3> reset;
        public Pair<BlockPos, Vec3> cartographyTable;
        public Pair<BlockPos, Vec3> finishedMapChest;
        public Pair<BlockPos, Vec3> usedToolChest;
        public Pair<BlockPos, Vec3> bed;
        public ArrayList<Pair<BlockPos, Vec3>> mapMaterialChests;
        public Pair<Vec3, Pair<Float, Float>> dumpStation;
        public BlockPos mapCorner;
        public HashMap<Item, ArrayList<Pair<BlockPos, Vec3>>> materialDict;
        public Set<ItemStack> toolSet;
    }

    private static JsonObject getObj(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : null;
    }

    public static ConfigData readFromJson(Path file) throws IOException {
        Gson gson = new Gson();
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            ConfigData data = new ConfigData();

            data.type = root.get("type").getAsString();

            JsonObject obj;
            obj = getObj(root, "reset");
            data.reset = obj != null ? jsonToBlockPosVecPair(obj) : null;
            obj = getObj(root, "cartographyTable");
            data.cartographyTable = obj != null ? jsonToBlockPosVecPair(obj) : null;
            obj = getObj(root, "finishedMapChest");
            data.finishedMapChest = obj != null ? jsonToBlockPosVecPair(obj) : null;
            obj = getObj(root, "usedToolChest");
            data.usedToolChest = obj != null ? jsonToBlockPosVecPair(obj) : null;
            obj = getObj(root, "bed");
            data.bed = obj != null ? jsonToBlockPosVecPair(obj) : null;

            data.mapMaterialChests = new ArrayList<>();
            if (root.has("mapMaterialChests")) {
                for (JsonElement e : root.getAsJsonArray("mapMaterialChests")) {
                    data.mapMaterialChests.add(jsonToBlockPosVecPair(e.getAsJsonObject()));
                }
            }

            if (root.has("dumpStation")) {
                JsonObject dump = root.getAsJsonObject("dumpStation");
                Vec3 pos = jsonToVec3(dump.getAsJsonObject("pos"));
                float yaw = dump.get("yaw").getAsFloat();
                float pitch = dump.get("pitch").getAsFloat();
                data.dumpStation = new Pair<>(pos, new Pair<>(yaw, pitch));
            } else {
                data.dumpStation = null;
            }

            data.mapCorner = jsonToBlockPos(root.getAsJsonObject("mapCorner"));

            data.materialDict = new HashMap<>();
            if (root.has("materialDict")) {
                JsonObject materialDictObj = root.getAsJsonObject("materialDict");
                for (String key : materialDictObj.keySet()) {
                    Identifier id = Identifier.parse(key);
                    Item item = BuiltInRegistries.ITEM.getValue(id);
                    ArrayList<Pair<BlockPos, Vec3>> list = new ArrayList<>();
                    for (JsonElement e : materialDictObj.getAsJsonArray(key)) {
                        list.add(jsonToBlockPosVecPair(e.getAsJsonObject()));
                    }
                    data.materialDict.put(item, list);
                }
            }

            data.toolSet = new HashSet<>();
            if (root.has("toolSet")) {
                for (JsonElement e : root.getAsJsonArray("toolSet")) {
                    JsonObject o = e.getAsJsonObject();
                    Identifier id = Identifier.parse(o.get("item").getAsString());
                    data.toolSet.add(new ItemStack(BuiltInRegistries.ITEM.getValue(id)));
                }
            }

            return data;
        }
    }
}
