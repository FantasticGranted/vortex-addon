package me.aleksilassila.litematica.printer.materials;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public final class MaterialListCache {
    private static final Path FILE = Paths.get("config", "litematica-printer", "material-list-cache.json");

    private static JsonObject root;
    private static boolean loaded;
    private static boolean dirty;

    private MaterialListCache() {
    }

    public record ChunkData(Map<String, Integer> missing, Map<String, Integer> mismatch, long savedAt) {
    }

    public static String sectionKey(String placementName) {
        Minecraft client = Minecraft.getInstance();
        return worldId(client) + "|" + dimensionId(client) + "|" + placementName;
    }

    public static long sectionSavedAt(String section) {
        load();
        JsonObject sectionObject = sectionObject(section, false);
        return sectionObject != null && sectionObject.has("savedAt")
                ? sectionObject.get("savedAt").getAsLong() : 0L;
    }

    public static ChunkData getChunk(String section, int chunkX, int chunkZ) {
        load();
        JsonObject sectionObject = sectionObject(section, false);
        if (sectionObject == null || !sectionObject.has("chunks")) return null;
        JsonElement element = sectionObject.getAsJsonObject("chunks").get(chunkKey(chunkX, chunkZ));
        if (element == null || !element.isJsonObject()) return null;
        JsonObject chunk = element.getAsJsonObject();
        JsonObject missingObject = chunk.has("missing") && chunk.get("missing").isJsonObject()
                ? chunk.getAsJsonObject("missing") : new JsonObject();
        JsonObject mismatchObject = chunk.has("mismatch") && chunk.get("mismatch").isJsonObject()
                ? chunk.getAsJsonObject("mismatch") : new JsonObject();
        return new ChunkData(readCounts(missingObject), readCounts(mismatchObject),
                chunk.has("t") ? chunk.get("t").getAsLong() : 0L);
    }

    public static void putChunk(String section, int chunkX, int chunkZ,
                                Map<String, Integer> missing, Map<String, Integer> mismatch) {
        load();
        JsonObject sectionObject = sectionObject(section, true);
        if (sectionObject == null) return;
        if (!sectionObject.has("chunks") || !sectionObject.get("chunks").isJsonObject()) {
            sectionObject.add("chunks", new JsonObject());
        }
        long now = System.currentTimeMillis() / 1000L;
        JsonObject chunk = new JsonObject();
        chunk.add("missing", writeCounts(missing));
        chunk.add("mismatch", writeCounts(mismatch));
        chunk.addProperty("t", now);
        sectionObject.getAsJsonObject("chunks").add(chunkKey(chunkX, chunkZ), chunk);
        sectionObject.addProperty("savedAt", now);
        dirty = true;
    }

    public static void save() {
        if (!dirty || root == null) return;
        dirty = false;
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
            }
        } catch (Exception ignored) {
        }
    }

    public static String idOf(Item item) {
        return String.valueOf(BuiltInRegistries.ITEM.getKey(item));
    }

    public static Item itemOf(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) return null;
        return BuiltInRegistries.ITEM.getValue(identifier);
    }

    private static JsonObject sectionObject(String section, boolean create) {
        if (root == null) root = new JsonObject();
        if (!root.has("sections") || !root.get("sections").isJsonObject()) {
            if (!create) return null;
            root.add("sections", new JsonObject());
        }
        JsonObject sections = root.getAsJsonObject("sections");
        if (create && !sections.has(section)) sections.add(section, new JsonObject());
        JsonElement element = sections.get(section);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static Map<String, Integer> readCounts(JsonObject object) {
        Map<String, Integer> counts = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            try {
                counts.put(entry.getKey(), entry.getValue().getAsInt());
            } catch (Exception ignored) {
            }
        }
        return counts;
    }

    private static JsonObject writeCounts(Map<String, Integer> counts) {
        JsonObject object = new JsonObject();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            object.addProperty(entry.getKey(), entry.getValue());
        }
        return object;
    }

    private static String chunkKey(int chunkX, int chunkZ) {
        return chunkX + "," + chunkZ;
    }

    private static String worldId(Minecraft client) {
        if (client.getSingleplayerServer() != null) {
            return "singleplayer:" + client.getSingleplayerServer().getWorldData().getLevelName();
        }
        String address = client.getCurrentServer() == null ? null : client.getCurrentServer().ip;
        if (address == null && client.getConnection() != null && client.getConnection().getConnection() != null) {
            address = String.valueOf(client.getConnection().getConnection().getRemoteAddress());
        }
        if (address != null) return "multiplayer:" + address;
        return "session:" + System.identityHashCode(client.level);
    }

    private static String dimensionId(Minecraft client) {
        return client.level == null ? "unknown" : String.valueOf(client.level.dimension());
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        root = new JsonObject();
        if (!Files.isRegularFile(FILE)) return;
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed != null && parsed.isJsonObject()) root = parsed.getAsJsonObject();
        } catch (Exception ignored) {
        }
    }
}
