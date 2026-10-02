package me.aleksilassila.litematica.printer.materials;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.widgets.WidgetMaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListPlacement;
import fi.dy.masa.litematica.materials.MaterialListUtils;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.util.BlockInfoListType;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.malilib.interfaces.ICompletionListener;
import fi.dy.masa.malilib.util.LayerRange;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SmartMaterialList {
    private static boolean adjusting;
    private static boolean rebuilding;

    private SmartMaterialList() {
    }

    public static boolean isRebuilding() {
        return rebuilding;
    }

    public static void setRebuilding(boolean value) {
        rebuilding = value;
    }

    public record Result(int liveChunks, int cachedChunks, int estChunks, long savedAtSeconds) {
    }

    public static String describe(Result result) {
        if (result == null) return "";
        List<String> parts = new ArrayList<>(3);
        if (result.liveChunks() > 0) parts.add("live");
        if (result.cachedChunks() > 0) {
            String time = result.savedAtSeconds() > 0
                    ? " " + new SimpleDateFormat("HH:mm").format(new Date(result.savedAtSeconds() * 1000L))
                    : "";
            parts.add("cached" + time);
        }
        if (result.estChunks() > 0) parts.add("~" + result.estChunks() + " chunks est");
        if (parts.isEmpty()) return "Adjusted: nothing to count";
        return "Adjusted: " + String.join(" + ", parts);
    }

    public static Result adjust(MaterialListBase list, ICompletionListener restoreListener) {
        if (adjusting) return null;
        if (!(list instanceof MaterialListPlacement placementList)) return null;
        if (!Configs.Core.MATERIAL_LIST_AUTO_ADJUST.getBooleanValue()) return null;

        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        WorldSchematic schematicWorld = SchematicWorldHandler.getSchematicWorld();
        if (level == null || schematicWorld == null) return null;

        SchematicPlacement placement = findPlacement(placementList);
        if (placement == null) return null;

        Collection<Box> boxes = placement.getSubRegionBoxes(
                SubRegionPlacement.RequiredEnabled.PLACEMENT_ENABLED).values();
        if (boxes.isEmpty()) return null;

        adjusting = true;
        list.setCompletionListener(null);
        try {
            return scan(placement, boxes, list, level, schematicWorld, client);
        } finally {
            list.setCompletionListener(restoreListener);
            adjusting = false;
        }
    }

    private static Result scan(SchematicPlacement placement, Collection<Box> boxes, MaterialListBase list,
                               ClientLevel level, WorldSchematic schematicWorld, Minecraft client) {
        boolean ignoreState = fi.dy.masa.litematica.config.Configs.Generic.MATERIAL_LIST_IGNORE_STATE.getBooleanValue();
        boolean useCache = Configs.Core.MATERIAL_LIST_CACHE.getBooleanValue();
        String section = useCache ? MaterialListCache.sectionKey(placement.getName()) : null;
        long cachedAt = useCache ? MaterialListCache.sectionSavedAt(section) : 0L;

        Integer layerMin = null;
        Integer layerMax = null;
        if (list.getMaterialListType() == BlockInfoListType.RENDER_LAYERS) {
            LayerRange range = DataManager.getRenderLayerRange();
            if (range.getAxis() == Direction.Axis.Y) {
                layerMin = range.getLayerMin();
                layerMax = range.getLayerMax();
            }
        }

        int minChunkX = Integer.MAX_VALUE;
        int minChunkZ = Integer.MAX_VALUE;
        int maxChunkX = Integer.MIN_VALUE;
        int maxChunkZ = Integer.MIN_VALUE;
        for (Box box : boxes) {
            minChunkX = Math.min(minChunkX, Math.min(box.getPos1().getX(), box.getPos2().getX()) >> 4);
            maxChunkX = Math.max(maxChunkX, Math.max(box.getPos1().getX(), box.getPos2().getX()) >> 4);
            minChunkZ = Math.min(minChunkZ, Math.min(box.getPos1().getZ(), box.getPos2().getZ()) >> 4);
            maxChunkZ = Math.max(maxChunkZ, Math.max(box.getPos1().getZ(), box.getPos2().getZ()) >> 4);
        }

        Object2IntOpenHashMap<BlockState> totals = new Object2IntOpenHashMap<>();
        Object2IntOpenHashMap<BlockState> missing = new Object2IntOpenHashMap<>();
        Object2IntOpenHashMap<BlockState> mismatch = new Object2IntOpenHashMap<>();
        Map<String, Integer> cachedMissing = new HashMap<>();
        Map<String, Integer> cachedMismatch = new HashMap<>();

        int liveChunks = 0;
        int cachedChunks = 0;
        int estChunks = 0;
        boolean wroteCache = false;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Object2IntOpenHashMap<BlockState> chunkTotal = new Object2IntOpenHashMap<>();
                Object2IntOpenHashMap<BlockState> chunkMissing = new Object2IntOpenHashMap<>();
                Object2IntOpenHashMap<BlockState> chunkMismatch = new Object2IntOpenHashMap<>();
                boolean anySlice = scanChunk(boxes, chunkX, chunkZ, layerMin, layerMax,
                        schematicWorld, level, ignoreState,
                        chunkTotal, chunkMissing, chunkMismatch);
                if (!anySlice) continue;

                mergeInto(totals, chunkTotal);
                if (level.hasChunk(chunkX, chunkZ)) {
                    liveChunks++;
                    mergeInto(missing, chunkMissing);
                    mergeInto(mismatch, chunkMismatch);
                    if (useCache) {
                        writeChunk(section, chunkX, chunkZ, chunkMissing, chunkMismatch, client.player);
                        wroteCache = true;
                    }
                } else if (useCache) {
                    MaterialListCache.ChunkData data = MaterialListCache.getChunk(section, chunkX, chunkZ);
                    if (data != null) {
                        cachedChunks++;
                        addToCounts(cachedMissing, data.missing());
                        addToCounts(cachedMismatch, data.mismatch());
                    } else {
                        estChunks++;
                        mergeInto(missing, chunkTotal);
                    }
                } else {
                    estChunks++;
                    mergeInto(missing, chunkTotal);
                }
            }
        }

        if (useCache && wroteCache) MaterialListCache.save();

        List<MaterialListEntry> entries = MaterialListUtils.getMaterialList(totals, missing, mismatch, client.player);
        if (!cachedMissing.isEmpty() || !cachedMismatch.isEmpty()) {
            entries = applyCachedCounts(entries, cachedMissing, cachedMismatch);
        }
        MaterialListUtils.updateAvailableCounts(entries, client.player);
        list.setMaterialListEntries(entries);
        WidgetMaterialListEntry.setMaxNameLength(list.getMaterialsAll(), list.getMultiplier());
        return new Result(liveChunks, cachedChunks, estChunks, cachedAt);
    }

    private static boolean scanChunk(Collection<Box> boxes, int chunkX, int chunkZ,
                                     Integer layerMin, Integer layerMax,
                                     WorldSchematic schematicWorld, ClientLevel level, boolean ignoreState,
                                     Object2IntOpenHashMap<BlockState> chunkTotal,
                                     Object2IntOpenHashMap<BlockState> chunkMissing,
                                     Object2IntOpenHashMap<BlockState> chunkMismatch) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        boolean anySlice = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (Box box : boxes) {
            int x0 = Math.min(box.getPos1().getX(), box.getPos2().getX());
            int x1 = Math.max(box.getPos1().getX(), box.getPos2().getX());
            int y0 = Math.min(box.getPos1().getY(), box.getPos2().getY());
            int y1 = Math.max(box.getPos1().getY(), box.getPos2().getY());
            int z0 = Math.min(box.getPos1().getZ(), box.getPos2().getZ());
            int z1 = Math.max(box.getPos1().getZ(), box.getPos2().getZ());

            int startX = Math.max(x0, baseX);
            int endX = Math.min(x1, baseX + 15);
            int startZ = Math.max(z0, baseZ);
            int endZ = Math.min(z1, baseZ + 15);
            if (startX > endX || startZ > endZ) continue;

            if (layerMin != null) {
                y0 = Math.max(y0, layerMin);
                y1 = Math.min(y1, layerMax);
                if (y0 > y1) continue;
            }

            anySlice = true;
            for (int x = startX; x <= endX; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = startZ; z <= endZ; z++) {
                        pos.set(x, y, z);
                        BlockState schematicState = schematicWorld.getBlockState(pos);
                        if (schematicState.isAir()) continue;
                        chunkTotal.addTo(schematicState, 1);
                        BlockState worldState = level.getBlockState(pos);
                        if (worldState.isAir()) {
                            chunkMissing.addTo(schematicState, 1);
                        } else if (worldState != schematicState
                                && !(ignoreState && worldState.getBlock() == schematicState.getBlock())) {
                            chunkMissing.addTo(schematicState, 1);
                            chunkMismatch.addTo(schematicState, 1);
                        }
                    }
                }
            }
        }
        return anySlice;
    }

    private static void writeChunk(String section, int chunkX, int chunkZ,
                                   Object2IntOpenHashMap<BlockState> chunkMissing,
                                   Object2IntOpenHashMap<BlockState> chunkMismatch,
                                   Player player) {
        List<MaterialListEntry> converted =
                MaterialListUtils.getMaterialList(chunkMissing, chunkMissing, chunkMismatch, player);
        Map<String, Integer> missingById = new HashMap<>();
        Map<String, Integer> mismatchById = new HashMap<>();
        for (MaterialListEntry entry : converted) {
            String id = MaterialListCache.idOf(entry.getStack().getItem());
            missingById.put(id, entry.getCountMissing());
            mismatchById.put(id, entry.getCountMismatched());
        }
        MaterialListCache.putChunk(section, chunkX, chunkZ, missingById, mismatchById);
    }

    private static List<MaterialListEntry> applyCachedCounts(List<MaterialListEntry> entries,
                                                             Map<String, Integer> cachedMissing,
                                                             Map<String, Integer> cachedMismatch) {
        List<MaterialListEntry> result = new ArrayList<>(entries.size());
        for (MaterialListEntry entry : entries) {
            String id = MaterialListCache.idOf(entry.getStack().getItem());
            Integer addMissing = cachedMissing.get(id);
            Integer addMismatch = cachedMismatch.get(id);
            if (addMissing == null && addMismatch == null) {
                result.add(entry);
                continue;
            }
            int total = entry.getCountTotal();
            int miss = Math.min(total, entry.getCountMissing() + (addMissing == null ? 0 : addMissing));
            int mis = Math.min(miss, entry.getCountMismatched() + (addMismatch == null ? 0 : addMismatch));
            if (miss == entry.getCountMissing() && mis == entry.getCountMismatched()) {
                result.add(entry);
            } else {
                result.add(new MaterialListEntry(entry.getStack(), total, miss, mis, entry.getCountAvailable()));
            }
        }
        return result;
    }

    private static SchematicPlacement findPlacement(MaterialListBase list) {
        for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
            if (placement.getMaterialList() == list) return placement;
        }
        return null;
    }

    private static void mergeInto(Object2IntOpenHashMap<BlockState> target,
                                  Object2IntOpenHashMap<BlockState> source) {
        for (Object2IntMap.Entry<BlockState> entry : source.object2IntEntrySet()) {
            target.addTo(entry.getKey(), entry.getIntValue());
        }
    }

    private static void addToCounts(Map<String, Integer> target, Map<String, Integer> source) {
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }
}
