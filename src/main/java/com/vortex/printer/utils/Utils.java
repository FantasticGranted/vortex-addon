package com.vortex.printer.utils;

import com.vortex.printer.interfaces.IClientPlayerInteractionManager;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;

import java.io.File;
import java.util.*;
import java.util.function.BiConsumer;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public final class Utils {

    private static int nextInteractID = 2;

    public static int getNextInteractID() {
        return nextInteractID;
    }

    public static ArrayList<Pair<BlockPos, net.minecraft.world.phys.Vec3>> saveAdd(ArrayList<Pair<BlockPos, net.minecraft.world.phys.Vec3>> list, BlockPos blockPos, net.minecraft.world.phys.Vec3 openPos) {
        for (Pair<BlockPos, net.minecraft.world.phys.Vec3> pair : list) {
            if (pair.getLeft().equals(blockPos)) {
                list.remove(pair);
                break;
            }
        }
        list.add(new Pair<>(blockPos, openPos));
        return list;
    }

    public static int stacksRequired(Collection<Integer> amounts) {
        int stacks = 0;
        for (int amount : amounts) {
            if (amount == 0) continue;
            stacks += Math.ceil((float) amount / 64f);
        }
        return stacks;
    }

    public static ArrayList<Integer> getAvailableSlots(HashMap<Item, ArrayList<Pair<BlockPos, net.minecraft.world.phys.Vec3>>> materials) {
        ArrayList<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            if (mc.player.getInventory().getItem(slot).isEmpty()) {
                slots.add(slot);
                continue;
            }
            Item item = mc.player.getInventory().getItem(slot).getItem();
            if (materials.containsKey(item)) {
                slots.add(slot);
            }
        }
        return slots;
    }

    public static Pair<ArrayList<Integer>, HashMap<Item, Integer>> getInvInformation(HashMap<Item, Integer> requiredItems, ArrayList<Integer> availableSlots) {
        ArrayList<Integer> dumpSlots = new ArrayList<>();
        HashMap<Item, Integer> materialInInv = new HashMap<>();
        for (int slot : availableSlots) {
            if (mc.player.getInventory().getItem(slot).isEmpty()) continue;
            Item item = mc.player.getInventory().getItem(slot).getItem();
            if (requiredItems.containsKey(item)) {
                int requiredAmount = requiredItems.get(item);
                int requiredModulusAmount = (requiredAmount - (requiredAmount / 64) * 64);
                if (requiredModulusAmount == 0) requiredModulusAmount = 64;
                int stackAmount = mc.player.getInventory().getItem(slot).getCount();
                if (requiredAmount > 0 && requiredModulusAmount <= stackAmount) {
                    int oldEntry = requiredItems.remove(item);
                    requiredItems.put(item, Math.max(0, oldEntry - stackAmount));
                    if (materialInInv.containsKey(item)) {
                        oldEntry = materialInInv.remove(item);
                        materialInInv.put(item, oldEntry + stackAmount);
                    } else {
                        materialInInv.put(item, stackAmount);
                    }
                    continue;
                }
            }
            dumpSlots.add(slot);
        }
        return new Pair<>(dumpSlots, materialInInv);
    }

    public static File getMinecraftDirectory() {
        return FabricLoader.getInstance().getGameDir().toFile();
    }

    public static boolean createFolders(File mapFolder) {
        File finishedMapFolder = new File(mapFolder.getAbsolutePath() + File.separator + "_finished_maps");
        File configFolder = new File(mapFolder.getAbsolutePath() + File.separator + "_configs");
        if (!mapFolder.exists()) {
            if (mapFolder.mkdir()) {
                ChatUtils.info("Created nerv-printer folder in the Minecraft directory.");
            } else {
                ChatUtils.warning("Failed to create nerv-printer folder in the Minecraft directory. Try to enable customFolderPath and enter a path.");
                return false;
            }
        }
        if (!finishedMapFolder.exists()) {
            if (!finishedMapFolder.mkdir()) {
                ChatUtils.warning("Failed to create finished-map folder in the nerv-printer folder");
                return false;
            }
        }
        if (!configFolder.exists()) {
            if (!configFolder.mkdir()) {
                ChatUtils.warning("Failed to create config folder in the nerv-printer folder");
                return false;
            }
        }
        return true;
    }

    public static int getIntervalStart(int pos) {
        return (int) Math.floor((float) (pos + 64) / 128f) * 128 - 64;
    }

    public static void setForwardPressed(boolean pressed) {
        mc.options.keyUp.setDown(pressed);
    }

    public static void setBackwardPressed(boolean pressed) {
        mc.options.keyDown.setDown(pressed);
    }

    public static void setJumpPressed(boolean pressed) {
        mc.options.keyJump.setDown(pressed);
    }

    public static int findHighestFreeSlot(ClientboundContainerSetContentPacket packet) {
        for (int i = packet.items().size() - 1; i > packet.items().size() - 1 - 36; i--) {
            ItemStack stack = packet.items().get(i);
            if (stack.isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    public static void performSwap(int fromSlot, int toSlot) {
        mc.player.getInventory().setSelectedSlot(toSlot);
        IClientPlayerInteractionManager cim = (IClientPlayerInteractionManager) mc.gameMode;
        cim.clickSlot(mc.player.containerMenu.containerId, fromSlot, toSlot, ContainerInput.SWAP, mc.player);
    }

    public static void iterateBlocks(BlockPos startingPos, int horizontalRadius, int verticalRadius, BiConsumer<BlockPos, BlockState> function) {
        int px = startingPos.getX();
        int py = startingPos.getY();
        int pz = startingPos.getZ();
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        int hRadius = Math.max(0, horizontalRadius);
        int vRadius = Math.max(0, verticalRadius);
        for (int x = px - hRadius; x <= px + hRadius; x++) {
            for (int z = pz - hRadius; z <= pz + hRadius; z++) {
                for (int y = py - vRadius; y <= py + vRadius; y++) {
                    blockPos.set(x, y, z);
                    BlockState blockState = MapAreaCache.getCachedBlockState(blockPos);
                    function.accept(blockPos, blockState);
                }
            }
        }
    }

    public static HashMap<Integer, Pair<Block, Integer>> getBlockPalette(ListTag paletteList) {
        HashMap<Integer, Pair<Block, Integer>> blockPaletteDict = new HashMap<>();
        for (int i = 0; i < paletteList.size(); i++) {
            CompoundTag block = paletteList.getCompoundOrEmpty(i);
            if (block.isEmpty()) continue;
            String blockName = block.getStringOr("Name", "");
            if (blockName.isEmpty()) continue;
            Identifier id = Identifier.parse(blockName);
            Block b = BuiltInRegistries.BLOCK.getValue(id);
            blockPaletteDict.put(i, new Pair<>(b, 0));
        }
        return blockPaletteDict;
    }

    public static Block[][] generateMapArray(ListTag blockList, HashMap<Integer, Pair<Block, Integer>> blockPalette) {
        int maxHeight = Integer.MIN_VALUE;
        int minX = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (int i = 0; i < blockList.size(); i++) {
            CompoundTag block = blockList.getCompoundOrEmpty(i);
            if (block.isEmpty()) continue;
            if (!block.contains("state")) continue;
            int blockId = block.getIntOr("state", 0);
            if (!blockPalette.containsKey(blockId)) continue;
            if (!block.contains("pos")) continue;
            ListTag pos = block.getListOrEmpty("pos");
            if (pos.size() < 3) continue;
            int x = pos.getIntOr(0, 0);
            int y = pos.getIntOr(1, 0);
            int z = pos.getIntOr(2, 0);
            if (y > maxHeight) maxHeight = y;
            if (x < minX) minX = x;
            if (z > maxZ) maxZ = z;
        }
        maxZ -= 127;

        Block[][] map = new Block[128][128];
        for (int i = 0; i < blockList.size(); i++) {
            CompoundTag block = blockList.getCompoundOrEmpty(i);
            if (block.isEmpty()) continue;
            if (!block.contains("state")) continue;
            int blockId = block.getIntOr("state", 0);
            if (!blockPalette.containsKey(blockId)) continue;
            if (!block.contains("pos")) continue;
            ListTag pos = block.getListOrEmpty("pos");
            if (pos.size() < 3) continue;
            int x = pos.getIntOr(0, 0);
            int y = pos.getIntOr(1, 0);
            int z = pos.getIntOr(2, 0);

            int adjustedX = x - minX;
            int adjustedZ = z - maxZ;

            if (y == maxHeight && adjustedX < map.length && adjustedZ < map.length && adjustedX >= 0 && adjustedZ >= 0) {
                map[adjustedX][adjustedZ] = blockPalette.get(blockId).getLeft();
                Pair<Block, Integer> oldPair = blockPalette.get(blockId);
                blockPalette.put(blockId, new Pair<>(oldPair.getLeft(), oldPair.getRight() + 1));
            }
        }

        blockPalette.entrySet().removeIf(entry -> entry.getValue().getRight() == 0);

        return map;
    }

    public static ArrayList<BlockPos> getInvalidPlacements(BlockPos mapCorner, Pair<Integer, Integer> interval, Block[][] map, ArrayList<BlockPos> knownErrors) {
        ArrayList<BlockPos> invalidPlacements = new ArrayList<>();
        for (int x = interval.getRight(); x >= interval.getLeft(); x--) {
            for (int z = 127; z >= 0; z--) {
                BlockPos absolutePos = mapCorner.offset(x, 0, z);
                if (knownErrors.contains(absolutePos)) continue;
                BlockState blockState = MapAreaCache.getCachedBlockState(absolutePos);
                Block block = blockState.getBlock();
                if (!blockState.isAir()) {
                    if (map[x][z] != block) invalidPlacements.add(absolutePos);
                }
            }
        }
        return invalidPlacements;
    }

    public static void getOneItem(int sourceSlot, boolean avoidFirstHotBar, ArrayList<Integer> availableSlots,
                                  ArrayList<Integer> availableHotBarSlots, ClientboundContainerSetContentPacket packet) {
        int targetSlot = availableHotBarSlots.get(0);
        if (avoidFirstHotBar) {
            targetSlot = availableSlots.get(0);
            if (availableSlots.get(0).equals(availableHotBarSlots.get(0))) {
                targetSlot = availableSlots.get(1);
            }
        }
        if (targetSlot < 9) {
            targetSlot += 27;
        } else {
            targetSlot -= 9;
        }
        targetSlot = packet.items().size() - 36 + targetSlot;
        mc.gameMode.handleContainerInput(packet.containerId(), sourceSlot, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(packet.containerId(), targetSlot, 1, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(packet.containerId(), sourceSlot, 0, ContainerInput.PICKUP, mc.player);
    }

    public static File getNextMapFile(File mapFolder, ArrayList<File> startedFiles, boolean areMoved) {
        File[] files = mapFolder.listFiles();
        if (files == null) return null;
        Arrays.sort(files, Comparator
            .comparingInt((File f) -> f.getName().length())
            .thenComparing(File::getName));

        for (File file : files) {
            if ((!startedFiles.contains(file) || areMoved) &&
                file.isFile() && file.getName().toLowerCase().endsWith(".nbt")) {
                startedFiles.add(file);
                return file;
            }
        }
        return null;
    }

    public static Direction getInteractionSide(BlockPos blockPos) {
        double minDistance = Double.MAX_VALUE;
        Direction bestSide = Direction.UP;
        for (Direction side : Direction.values()) {
            double neighbourDistance = mc.player.getEyePosition().distanceTo(blockPos.offset(side.getUnitVec3i()).getCenter());
            if (neighbourDistance < minDistance) {
                minDistance = neighbourDistance;
                bestSide = side;
            }
        }
        return bestSide;
    }

    public static boolean isInInterval(Pair<Integer, Integer> interval, int number) {
        return number >= interval.getLeft() && number <= interval.getRight();
    }

    @EventHandler
    public void onGameLeft(GameLeftEvent event) {
        nextInteractID = 2;
    }

    @EventHandler(priority = EventPriority.HIGHEST - 1)
    private static void onRecievePacket(PacketEvent.Receive event) {
        if (event.packet instanceof ServerboundUseItemPacket packet) {
            nextInteractID = packet.getSequence() + 1;
        }
        if (event.packet instanceof ServerboundUseItemOnPacket packet) {
            nextInteractID = packet.getSequence() + 1;
        }
    }
}
