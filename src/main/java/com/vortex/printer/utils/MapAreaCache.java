package com.vortex.printer.utils;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class MapAreaCache {
    private static BlockPos mapCorner = null;
    private static Map<ChunkPos, LevelChunk> cachedChunks = new HashMap<>();

    private static final Map<BlockPos, BlockState> tickCache = new HashMap<>();
    private static int tickCounter = 0;

    public static boolean isWithinMap(BlockPos pos) {
        if (mapCorner == null) return false;
        BlockPos relativePos = pos.offset(-mapCorner.getX(), -mapCorner.getY(), -mapCorner.getZ());
        return relativePos.getX() >= 0 && relativePos.getX() < 128 && relativePos.getZ() >= 0 && relativePos.getZ() < 128;
    }

    private static boolean isMapAreaClearCached = false;
    private static boolean isMapAreaClearValid = false;

    public static boolean isMapAreaClear() {
        if (isMapAreaClearValid) return isMapAreaClearCached;
        if (mapCorner == null) return false;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                mutable.set(mapCorner.getX() + x, mapCorner.getY(), mapCorner.getZ() + z);
                BlockState blockState = mc.level.getBlockState(mutable);
                if (!blockState.isAir() || !blockState.getFluidState().isEmpty()) {
                    isMapAreaClearCached = false;
                    isMapAreaClearValid = true;
                    return false;
                }
            }
        }
        isMapAreaClearCached = true;
        isMapAreaClearValid = true;
        return true;
    }

    public static void clearClearCache() {
        isMapAreaClearValid = false;
    }

    public static void reset(BlockPos newCorner) {
        mapCorner = newCorner.immutable();
        cachedChunks.clear();
        tickCache.clear();
        isMapAreaClearValid = false;
    }

    public static BlockState getCachedBlockState(BlockPos blockPos) {
        if (mapCorner == null) return mc.level.getBlockState(blockPos);

        BlockState cached = tickCache.get(blockPos);
        if (cached != null) return cached;

        BlockState state = lookupBlockState(blockPos);
        tickCache.put(blockPos, state);
        return state;
    }

    private static BlockState lookupBlockState(BlockPos blockPos) {
        int chunkX = blockPos.getX() >> 4;
        int chunkZ = blockPos.getZ() >> 4;
        if (mc.level.getChunkSource().hasChunk(chunkX, chunkZ)) {
            return mc.level.getBlockState(blockPos);
        }
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        if (cachedChunks.containsKey(chunkPos)) {
            LevelChunk chunk = cachedChunks.get(chunkPos);
            return chunk.getBlockState(blockPos);
        }
        ChatUtils.warning("Could not fetch Block at " + blockPos.toShortString() + ". Try loading the entire Map Area first.");
        return mc.level.getBlockState(blockPos);
    }

    @EventHandler
    private static void onReceivePacket(PacketEvent.Receive event) {
        if (mapCorner != null && event.packet instanceof ClientboundForgetLevelChunkPacket packet) {
            ChunkPos chunkPos = packet.pos();
            BlockPos chunkCorner = new BlockPos(chunkPos.getMinBlockX(), 0, chunkPos.getMinBlockZ());
            if (isWithinMap(chunkCorner)) {
                cachedChunks.put(chunkPos, mc.level.getChunk(chunkPos.x(), chunkPos.z()));
            }
        }
    }

    @EventHandler
    private static void onTick(TickEvent.Pre event) {
        tickCache.clear();
        tickCounter++;
        if (tickCounter % 10 == 0) {
            isMapAreaClearValid = false;
        }
    }
}
