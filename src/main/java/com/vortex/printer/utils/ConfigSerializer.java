package com.vortex.printer.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class ConfigSerializer {

    private static JsonObject blockPosToJson(BlockPos pos) {
        JsonObject obj = new JsonObject();
        obj.addProperty("x", pos.getX());
        obj.addProperty("y", pos.getY());
        obj.addProperty("z", pos.getZ());
        return obj;
    }

    private static JsonObject vec3ToJson(Vec3 vec) {
        JsonObject obj = new JsonObject();
        obj.addProperty("x", vec.x);
        obj.addProperty("y", vec.y);
        obj.addProperty("z", vec.z);
        return obj;
    }

    private static JsonObject blockPosVecPairToJson(Pair<BlockPos, Vec3> pair) {
        JsonObject obj = new JsonObject();
        obj.add("blockPos", blockPosToJson(pair.getLeft()));
        obj.add("openPos", vec3ToJson(pair.getRight()));
        return obj;
    }

    public static void writeToJson(
        Path file, String type,
        Pair<BlockPos, Vec3> reset,
        Pair<BlockPos, Vec3> cartographyTable,
        Pair<BlockPos, Vec3> finishedMapChest,
        ArrayList<Pair<BlockPos, Vec3>> mapMaterialChests,
        Pair<Vec3, Pair<Float, Float>> dumpStation,
        BlockPos mapCorner,
        HashMap<Item, ArrayList<Pair<BlockPos, Vec3>>> materialDict
    ) throws IOException {
        writeToJsonInternal(file, type, reset, cartographyTable, finishedMapChest, null, null,
            mapMaterialChests, dumpStation, mapCorner, materialDict, null);
    }

    public static void writeToJson(
        Path file, String type,
        Pair<BlockPos, Vec3> cartographyTable,
        Pair<BlockPos, Vec3> finishedMapChest,
        Pair<BlockPos, Vec3> usedToolChest,
        Pair<BlockPos, Vec3> bed,
        ArrayList<Pair<BlockPos, Vec3>> mapMaterialChests,
        Pair<Vec3, Pair<Float, Float>> dumpStation,
        BlockPos mapCorner,
        HashMap<Item, ArrayList<Pair<BlockPos, Vec3>>> materialDict,
        Set<ItemStack> toolSet
    ) throws IOException {
        writeToJsonInternal(file, type, null, cartographyTable, finishedMapChest, usedToolChest, bed,
            mapMaterialChests, dumpStation, mapCorner, materialDict, toolSet);
    }

    private static void writeToJsonInternal(
        Path file, String type,
        Pair<BlockPos, Vec3> reset,
        Pair<BlockPos, Vec3> cartographyTable,
        Pair<BlockPos, Vec3> finishedMapChest,
        Pair<BlockPos, Vec3> usedToolChest,
        Pair<BlockPos, Vec3> bed,
        ArrayList<Pair<BlockPos, Vec3>> mapMaterialChests,
        Pair<Vec3, Pair<Float, Float>> dumpStation,
        BlockPos mapCorner,
        HashMap<Item, ArrayList<Pair<BlockPos, Vec3>>> materialDict,
        Set<ItemStack> toolSet
    ) throws IOException {
        JsonObject root = new JsonObject();

        root.addProperty("type", type);
        if (reset != null) root.add("reset", blockPosVecPairToJson(reset));
        if (cartographyTable != null) root.add("cartographyTable", blockPosVecPairToJson(cartographyTable));
        if (finishedMapChest != null) root.add("finishedMapChest", blockPosVecPairToJson(finishedMapChest));
        if (usedToolChest != null) root.add("usedToolChest", blockPosVecPairToJson(usedToolChest));
        if (bed != null) root.add("bed", blockPosVecPairToJson(bed));

        if (mapMaterialChests != null) {
            JsonArray materialChestsArray = new JsonArray();
            for (Pair<BlockPos, Vec3> pair : mapMaterialChests) {
                materialChestsArray.add(blockPosVecPairToJson(pair));
            }
            root.add("mapMaterialChests", materialChestsArray);
        }

        if (dumpStation != null) {
            JsonObject dumpStationObj = new JsonObject();
            dumpStationObj.add("pos", vec3ToJson(dumpStation.getLeft()));
            dumpStationObj.addProperty("yaw", dumpStation.getRight().getLeft());
            dumpStationObj.addProperty("pitch", dumpStation.getRight().getRight());
            root.add("dumpStation", dumpStationObj);
        }

        if (mapCorner != null) root.add("mapCorner", blockPosToJson(mapCorner));

        if (materialDict != null) {
            JsonObject materialDictObj = new JsonObject();
            for (Map.Entry<Item, ArrayList<Pair<BlockPos, Vec3>>> entry : materialDict.entrySet()) {
                Identifier id = BuiltInRegistries.ITEM.getKey(entry.getKey());
                String blockId = id != null ? id.toString() : "unknown";
                JsonArray chestArray = new JsonArray();
                for (Pair<BlockPos, Vec3> pair : entry.getValue()) {
                    chestArray.add(blockPosVecPairToJson(pair));
                }
                materialDictObj.add(blockId, chestArray);
            }
            root.add("materialDict", materialDictObj);
        }

        if (toolSet != null) {
            JsonArray toolSetArray = new JsonArray();
            for (ItemStack stack : toolSet) {
                JsonObject stackObj = new JsonObject();
                Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                stackObj.addProperty("item", id != null ? id.toString() : "unknown");
                toolSetArray.add(stackObj);
            }
            root.add("toolSet", toolSetArray);
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (Writer writer = Files.newBufferedWriter(file)) {
            gson.toJson(root, writer);
        }
    }
}
