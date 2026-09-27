package com.vortex.printer.modules;

import com.vortex.Vortex;
import com.vortex.printer.interfaces.MapPrinter;
import com.vortex.printer.utils.*;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.utils.StarscriptTextBoxRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Triple;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class StaircasedPrinter extends Module implements MapPrinter {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced", false);
    private final SettingGroup sgMultiUser = settings.createGroup("Multi User", false);
    private final SettingGroup sgError = settings.createGroup("Error Handling");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> interactionRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("interaction-range").description("The maximum range you can place blocks around yourself.")
        .defaultValue(4).min(1).sliderRange(1, 5).build()
    );

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay").description("How many milliseconds to wait after placing.")
        .defaultValue(50).min(1).sliderRange(10, 300).build()
    );

    private final Setting<Double> maxMiningRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-mining-range").description("The maximum range you can mine blocks around yourself.")
        .defaultValue(1).min(0.5).sliderRange(0.5, 2).build()
    );

    private final Setting<List<Block>> startBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("start-blocks").description("Which block to interact with to start the printing process.")
        .defaultValue(Blocks.STONE_BUTTON, Blocks.ACACIA_BUTTON, Blocks.BAMBOO_BUTTON, Blocks.BIRCH_BUTTON,
            Blocks.CRIMSON_BUTTON, Blocks.DARK_OAK_BUTTON, Blocks.JUNGLE_BUTTON, Blocks.OAK_BUTTON,
            Blocks.POLISHED_BLACKSTONE_BUTTON, Blocks.SPRUCE_BUTTON, Blocks.WARPED_BUTTON).build()
    );

    private final Setting<SprintMode> sprinting = sgGeneral.add(new EnumSetting.Builder<SprintMode>()
        .name("sprint-mode").description("How to sprint.").defaultValue(SprintMode.Off).build()
    );

    private final Setting<Boolean> activationReset = sgGeneral.add(new BoolSetting.Builder()
        .name("activation-reset").description("Disable if the bot should continue after reconnecting.")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> rotatePlace = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate-place").description("Rotate when placing a block.").defaultValue(true).build()
    );

    private final Setting<Boolean> sleep = sgGeneral.add(new BoolSetting.Builder()
        .name("sleep").description("Sleep in bed when starting a map to avoid Phantoms.")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> customFolderPath = sgGeneral.add(new BoolSetting.Builder()
        .name("custom-folder-path").description("Allows to set a custom path to the nbt folder.")
        .defaultValue(false).onChanged((value) -> warnPathChanged()).build()
    );

    public final Setting<String> mapPrinterFolderPath = sgGeneral.add(new StringSetting.Builder()
        .name("nerv-printer-folder-path").description("The path to your nerv-printer directory.")
        .defaultValue("C:\\Users\\(username)\\AppData\\Roaming\\.minecraft\\nerv-printer")
        .wide().renderer(StarscriptTextBoxRenderer.class).visible(() -> customFolderPath.get())
        .onChanged((value) -> warnPathChanged()).build()
    );

    private final Setting<Boolean> useDefaultConfigFile = sgGeneral.add(new BoolSetting.Builder()
        .name("use-default-config-file").description("Load a config file when the module is enabled.")
        .defaultValue(false).build()
    );

    public final Setting<String> configFileName = sgGeneral.add(new StringSetting.Builder()
        .name("config-file-name").description("The config file that is loaded when the module is enabled.")
        .defaultValue("carpet-printer-config.json").wide().renderer(StarscriptTextBoxRenderer.class)
        .visible(() -> useDefaultConfigFile.get()).build()
    );

    private final Setting<Integer> preRestockDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("pre-restock-delay").description("How many ticks to wait to take items after opening the chest.")
        .defaultValue(10).min(1).sliderRange(1, 40).build()
    );

    private final Setting<Integer> invActionDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("inventory-action-delay").description("How many ticks to wait between each inventory action.")
        .defaultValue(2).min(1).sliderRange(1, 40).build()
    );

    private final Setting<Integer> postRestockDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("post-restock-delay").description("How many ticks to wait after restocking.")
        .defaultValue(10).min(1).sliderRange(1, 40).build()
    );

    private final Setting<Integer> preSwapDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("pre-swap-delay").description("How many ticks to wait before swapping an item into the hotbar.")
        .defaultValue(5).min(0).sliderRange(0, 20).build()
    );

    private final Setting<Integer> postSwapDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("post-swap-delay").description("How many ticks to wait after swapping an item into the hotbar.")
        .defaultValue(5).min(0).sliderRange(0, 20).build()
    );

    private final Setting<Integer> retryInteractTimer = sgAdvanced.add(new IntSetting.Builder()
        .name("retry-interact-timer").description("How many ticks to wait for chest response before interacting again.")
        .defaultValue(80).min(1).sliderRange(20, 200).build()
    );

    private final Setting<Integer> posResetTimeout = sgAdvanced.add(new IntSetting.Builder()
        .name("pos-reset-timeout").description("How many ticks to wait after the player position was reset.")
        .defaultValue(10).min(0).sliderRange(0, 40).build()
    );

    private final Setting<Integer> jumpCoolDown = sgAdvanced.add(new IntSetting.Builder()
        .name("jump-timeout").description("How many ticks to wait after jumping before jumping again.")
        .defaultValue(5).min(1).sliderRange(1, 20).build()
    );

    private final Setting<Integer> mineLineEndTimeout = sgAdvanced.add(new IntSetting.Builder()
        .name("mine-line-end-timeout").description("How many ticks to wait after mining a line.")
        .defaultValue(20).min(0).sliderRange(0, 30).build()
    );

    private final Setting<Double> durabilityBuffer = sgAdvanced.add(new DoubleSetting.Builder()
        .name("durability-buffer").description("The additional required durability for restocked mining tools (in %).")
        .defaultValue(0.2).min(0).sliderRange(0, 1).build()
    );

    private final Setting<Double> mineLineEndOffset = sgAdvanced.add(new DoubleSetting.Builder()
        .name("mine-LineEndOffset").description("The offset to the Map Area when mining the last block of a row.")
        .defaultValue(1).min(0.4).sliderRange(0.5, 3).build()
    );

    private final Setting<Double> checkpointBuffer = sgAdvanced.add(new DoubleSetting.Builder()
        .name("checkpoint-buffer").description("The buffer area of the checkpoints.")
        .defaultValue(0.2).min(0).sliderRange(0, 1).build()
    );

    private final Setting<Boolean> snapToCheckpoints = sgAdvanced.add(new BoolSetting.Builder()
        .name("snap-to-checkpoints").description("Snap to checkpoints when getting close.").defaultValue(false).build()
    );

    private final Setting<Boolean> moveToFinishedFolder = sgAdvanced.add(new BoolSetting.Builder()
        .name("move-to-finished-folder").description("Moves finished NBT files into the finished-maps folder.")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> disableOnFinished = sgAdvanced.add(new BoolSetting.Builder()
        .name("disable-on-finished").description("Disables the printer when all nbt files are finished.")
        .defaultValue(true).build()
    );

    private final Setting<Boolean> displayMaxRequirements = sgAdvanced.add(new BoolSetting.Builder()
        .name("print-max-requirements").description("Print the maximum amount of material needed for all maps.")
        .defaultValue(false).build()
    );

    private final Setting<Boolean> debugPrints = sgAdvanced.add(new BoolSetting.Builder()
        .name("debug-prints").description("Prints additional information.").defaultValue(false).build()
    );

    private final Setting<String> directMessageCommand = sgMultiUser.add(new StringSetting.Builder()
        .name("direct-message-command").description("The command used to send direct messages.")
        .defaultValue("w").onChanged((value) -> SlaveSystem.directMessageCommand = value).build()
    );

    private final Setting<String> senderPrefix = sgMultiUser.add(new StringSetting.Builder()
        .name("sender-prefix").description("The text that always comes before the name of sender.")
        .defaultValue("").onChanged((value) -> SlaveSystem.senderPrefix = value).build()
    );

    private final Setting<String> senderSuffix = sgMultiUser.add(new StringSetting.Builder()
        .name("sender-suffix").description("The text that is always between the name of the sender and the message.")
        .defaultValue(" whispers: ").onChanged((value) -> SlaveSystem.senderSuffix = value).build()
    );

    private final Setting<Integer> commandDelay = sgMultiUser.add(new IntSetting.Builder()
        .name("chat-message-delay").description("How many ticks to wait between sending chat messages.")
        .defaultValue(50).min(1).sliderRange(1, 100)
        .onChanged((value) -> SlaveSystem.commandDelay = value).build()
    );

    private final Setting<Integer> randomSuffix = sgMultiUser.add(new IntSetting.Builder()
        .name("random-suffix-length").description("Generate a randomized suffix to circumvent anti-spam.")
        .defaultValue(0).min(0).max(36).sliderRange(0, 10)
        .onChanged((value) -> SlaveSystem.randomLength = value).build()
    );

    private final Setting<Boolean> logErrors = sgError.add(new BoolSetting.Builder()
        .name("log-errors").description("Prints warning when a misplacement is detected.").defaultValue(true).build()
    );

    private final Setting<ErrorAction> errorAction = sgError.add(new EnumSetting.Builder<ErrorAction>()
        .name("error-action").description("What to do when a misplacement is detected.")
        .defaultValue(ErrorAction.Ignore).build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render").description("Highlights the selected areas.").defaultValue(true).build()
    );

    private final Setting<Boolean> renderMap = sgRender.add(new BoolSetting.Builder()
        .name("render-map").description("Highlights the position of the map blocks.")
        .defaultValue(false).visible(() -> render.get()).build()
    );

    private final Setting<Boolean> renderChestPositions = sgRender.add(new BoolSetting.Builder()
        .name("render-chest-positions").description("Highlights the selected chests.")
        .defaultValue(true).visible(() -> render.get()).build()
    );

    private final Setting<Boolean> renderOpenPositions = sgRender.add(new BoolSetting.Builder()
        .name("render-open-positions").description("Indicate the position the bot will go to interact with the chest.")
        .defaultValue(true).visible(() -> render.get()).build()
    );

    private final Setting<Boolean> renderCheckpoints = sgRender.add(new BoolSetting.Builder()
        .name("render-checkpoints").description("Indicate the checkpoints the bot will traverse.")
        .defaultValue(true).visible(() -> render.get()).build()
    );

    private final Setting<Boolean> renderSpecialInteractions = sgRender.add(new BoolSetting.Builder()
        .name("render-special-interactions").description("Indicate special interaction positions.")
        .defaultValue(true).visible(() -> render.get()).build()
    );

    private final Setting<Double> indicatorSize = sgRender.add(new DoubleSetting.Builder()
        .name("indicator-size").description("How big the rendered indicator will be.")
        .defaultValue(0.2).min(0).sliderRange(0, 1).visible(() -> render.get()).build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color").description("The render color.")
        .defaultValue(new SettingColor(22, 230, 206, 155)).visible(() -> render.get()).build()
    );

    int timeoutTicks;
    int jumpTimeout;
    int interactTimeout;
    int toBeSwappedSlot;
    int minedLines;
    long lastTickTimeNanos;
    boolean closeNextInvPacket;
    State state;
    State oldState;
    State debugPreviousState;
    Pair<Integer, Integer> workingInterval;
    Pair<Integer, Integer> trueInterval;
    Pair<BlockPos, Vec3> usedToolChest;
    Pair<BlockPos, Vec3> cartographyTable;
    Pair<BlockPos, Vec3> finishedMapChest;
    Pair<BlockPos, Vec3> bed;
    ArrayList<Pair<BlockPos, Vec3>> mapMaterialChests;
    Pair<Vec3, Pair<Float, Float>> dumpStation;
    BlockPos mapCorner;
    BlockPos tempChestPos;
    BlockPos lastInteractedChest;
    BlockPos miningPos;
    Item lastSwappedMaterial;
    ClientboundContainerSetContentPacket toBeHandledInvPacket;
    HashMap<Integer, Pair<Block, Integer>> blockPaletteDict;
    HashMap<Item, ArrayList<Pair<BlockPos, Vec3>>> materialDict;
    Set<ItemStack> toolSet;
    ArrayList<Integer> availableSlots;
    ArrayList<Integer> availableHotBarSlots;
    ArrayList<Triple<Item, Integer, Integer>> restockList;
    ArrayList<BlockPos> checkedChests;
    ArrayList<Pair<Vec3, Pair<String, BlockPos>>> checkpoints;
    ArrayList<File> startedFiles;
    ArrayList<Integer> restockBacklogSlots;
    ArrayList<BlockPos> knownErrors;
    Pair<Block, Integer>[][] map;
    File mapFolder;
    File mapFile;

    public StaircasedPrinter() {
        super(Vortex.CATEGORY, "fullblock-printer", "Automatically builds fullblock maps with optional staircasing from nbt files.");
    }

    @Override
    public void onActivate() {
        lastTickTimeNanos = System.nanoTime();
        if (!activationReset.get() && checkpoints != null) {
            return;
        }
        materialDict = new HashMap<>();
        availableSlots = new ArrayList<>();
        availableHotBarSlots = new ArrayList<>();
        restockList = new ArrayList<>();
        toolSet = new HashSet<>();
        checkedChests = new ArrayList<>();
        checkpoints = new ArrayList<>();
        startedFiles = new ArrayList<>();
        restockBacklogSlots = new ArrayList<>();
        knownErrors = new ArrayList<>();
        usedToolChest = null;
        mapCorner = null;
        lastInteractedChest = null;
        miningPos = null;
        cartographyTable = null;
        finishedMapChest = null;
        bed = null;
        mapMaterialChests = new ArrayList<>();
        dumpStation = null;
        lastSwappedMaterial = null;
        toBeHandledInvPacket = null;
        closeNextInvPacket = false;
        timeoutTicks = 0;
        jumpTimeout = 0;
        interactTimeout = 0;
        toBeSwappedSlot = -1;
        minedLines = 128;
        oldState = null;
        debugPreviousState = null;

        setInterval(new Pair<>(0, 127));
        SlaveSystem.setupSlaveSystem(this, commandDelay.get(), directMessageCommand.get(), senderPrefix.get(), senderSuffix.get(), randomSuffix.get());

        if (!customFolderPath.get()) {
            mapFolder = new File(Utils.getMinecraftDirectory(), "nerv-printer");
        } else {
            mapFolder = new File(mapPrinterFolderPath.get());
        }
        if (!Utils.createFolders(mapFolder)) {
            toggle();
            return;
        }

        if (displayMaxRequirements.get()) {
            HashMap<Block, Integer> materialCountDict = new HashMap<>();
            for (File file : mapFolder.listFiles()) {
                if (!file.isFile()) continue;
                if (!prepareNextMapFile()) return;
                for (Pair<Block, Integer> material : blockPaletteDict.values()) {
                    if (!materialCountDict.containsKey(material.getLeft())) {
                        materialCountDict.put(material.getLeft(), material.getRight());
                    } else {
                        materialCountDict.put(material.getLeft(), Math.max(materialCountDict.get(material.getLeft()), material.getRight()));
                    }
                }
            }
            info("\u00a7aMaterial needed for all files:");
            for (Block block : materialCountDict.keySet()) {
                float shulkerAmount = (float) Math.ceil((float) materialCountDict.get(block) / (float) (27 * 64) * 10) / (float) 10;
                if (shulkerAmount == 0) continue;
                info(block.getName().getString() + ": " + shulkerAmount + " shulker");
            }
            startedFiles.clear();
        }

        if (!prepareNextMapFile()) return;

        state = State.SelectingMapArea;
        if (useDefaultConfigFile.get()) {
            File configFolder = new File(mapFolder, "_configs");
            if (!loadConfig(new File(configFolder, configFileName.get()))) {
                info("Select the \u00a7aMap Building Area (128x128). (Right-click the edge from the inside)");
            }
        } else {
            info("Select the \u00a7aMap Building Area (128x128). (Right-click the edge from the inside)");
        }
    }

    @Override
    public void onDeactivate() {
        Utils.setForwardPressed(false);
        Utils.setBackwardPressed(false);
        Utils.setJumpPressed(false);
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (state == State.SelectingDumpStation && event.packet instanceof ServerboundPlayerActionPacket packet
            && packet.getAction() == ServerboundPlayerActionPacket.Action.DROP_ITEM) {
            dumpStation = new Pair<>(mc.player.position(), new Pair<>(mc.player.getYRot(), mc.player.getXRot()));
            state = State.SelectingFinishedMapChest;
            info("Dump Station selected. Select the \u00a7aFinished Map Chest");
            return;
        }
        if (!(event.packet instanceof ServerboundUseItemOnPacket packet) || state == null) return;
        switch (state) {
            case SelectingMapArea:
                BlockPos hitPos = packet.getHitResult().getBlockPos().offset(packet.getHitResult().getDirection().getUnitVec3i());
                int adjustedX = Utils.getIntervalStart(hitPos.getX());
                int adjustedZ = Utils.getIntervalStart(hitPos.getZ());
                mapCorner = new BlockPos(adjustedX, hitPos.getY(), adjustedZ);
                MapAreaCache.reset(mapCorner);
                state = State.SelectingTable;
                info("Map Area selected. Select the \u00a7aCartography Table.");
                break;
            case SelectingTable:
                BlockPos blockPos = packet.getHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock().equals(Blocks.CARTOGRAPHY_TABLE)) {
                    cartographyTable = new Pair<>(blockPos, mc.player.position());
                    info("Cartography Table selected. Throw an item into the \u00a7aDump Station.");
                    state = State.SelectingDumpStation;
                }
                break;
            case SelectingFinishedMapChest:
                blockPos = packet.getHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock() instanceof ChestBlock) {
                    finishedMapChest = new Pair<>(blockPos, mc.player.position());
                    info("Finished Map Chest selected. Select the \u00a7aUsed Pickaxe Chest.");
                    state = State.SelectingUsedPickaxeChest;
                }
                break;
            case SelectingUsedPickaxeChest:
                blockPos = packet.getHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock() instanceof ChestBlock) {
                    usedToolChest = new Pair<>(blockPos, mc.player.position());
                    if (sleep.get()) {
                        info("Used Pickaxe Chest selected. Select the \u00a7abed used for sleeping.");
                        state = State.SelectingBed;
                    } else {
                        info("Used Pickaxe Chest selected. Select all \u00a7aMaterial-, Tool-, and Map-Chests.");
                        state = State.SelectingChests;
                    }
                }
                break;
            case SelectingBed:
                blockPos = packet.getHitResult().getBlockPos();
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock() instanceof BedBlock) {
                    bed = new Pair<>(blockPos, mc.player.position());
                    info("Bed selected. Select all \u00a7aMaterial-, Tool-, and Map-Chests.");
                    state = State.SelectingChests;
                }
                break;
            case SelectingChests:
                if (startBlocks.get().isEmpty())
                    warning("No block selected as Start Block! Please select one in the settings.");
                blockPos = packet.getHitResult().getBlockPos();
                BlockState blockState = MapAreaCache.getCachedBlockState(blockPos);
                if (MapAreaCache.getCachedBlockState(blockPos).getBlock().equals(Blocks.CHEST)) {
                    tempChestPos = blockPos;
                    state = State.AwaitRegisterResponse;
                }
                if (startBlocks.get().contains(blockState.getBlock())) {
                    if (materialDict.isEmpty()) {
                        warning("No Material Chests selected!");
                        return;
                    }
                    if (toolSet.isEmpty()) {
                        warning("No Tool Chests selected!");
                        return;
                    }
                    if (mapMaterialChests.isEmpty()) {
                        warning("No Map Chests selected!");
                        return;
                    }
                    startBuilding();
                }
                break;
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (state == null) return;

        if (event.packet instanceof ClientboundPlayerPositionPacket) {
            timeoutTicks = posResetTimeout.get();
            if (timeoutTicks > 0) {
                Utils.setForwardPressed(false);
                Utils.setBackwardPressed(false);
            }
        }

        if (!(event.packet instanceof ClientboundContainerSetContentPacket packet)) return;

        if (state.equals(State.AwaitRegisterResponse)) {
            Item foundItem = null;
            ItemStack foundItemStack = null;
            boolean isMixedContent = false;
            for (int i = 0; i < packet.items().size() - 36; i++) {
                ItemStack stack = packet.items().get(i);
                if (!stack.isEmpty()) {
                    if (foundItem != null && foundItem != stack.getItem().asItem()) {
                        isMixedContent = true;
                    }
                    foundItem = stack.getItem().asItem();
                    foundItemStack = stack;
                    if (foundItem == Items.MAP || foundItem == Items.GLASS_PANE) {
                        info("Registered \u00a7aMapChest");
                        mapMaterialChests = Utils.saveAdd(mapMaterialChests, tempChestPos, mc.player.position());
                        state = State.SelectingChests;
                        return;
                    }
                }
            }
            if (isMixedContent) {
                warning("Different items found in chest. Please only have one item type in the chest.");
                state = State.SelectingChests;
                return;
            }
            if (foundItem == null) {
                warning("No items found in chest.");
                state = State.SelectingChests;
                return;
            }
            if (ToolUtils.isTool(foundItemStack)) {
                toolSet.add(foundItemStack);
            }
            info("Registered item: \u00a7a" + new ItemStack(foundItem).getHoverName().getString());
            materialDict.computeIfAbsent(foundItem, k -> new ArrayList<>());
            materialDict.put(foundItem, Utils.saveAdd(materialDict.get(foundItem), tempChestPos, mc.player.position()));
            state = State.SelectingChests;
            return;
        }

        List<State> allowedStates = Arrays.asList(State.AwaitRestockResponse, State.AwaitMapChestResponse,
            State.AwaitCartographyResponse, State.AwaitFinishedMapChestResponse, State.AwaitUsedToolChestResponse);
        if (allowedStates.contains(state)) {
            toBeHandledInvPacket = packet;
            timeoutTicks = preRestockDelay.get();
        }
    }

    private void handleInventoryPacket(ClientboundContainerSetContentPacket packet) {
        if (debugPrints.get()) info("Handling InvPacket for: " + state);
        closeNextInvPacket = true;
        switch (state) {
            case AwaitRestockResponse:
                interactTimeout = 0;
                if (restockList.isEmpty()) break;
                boolean foundMaterials = false;
                List<Integer> slots = new ArrayList<>();
                for (int i = 0; i < packet.items().size() - 36; i++) {
                    slots.add(i);
                }
                Collections.shuffle(slots);
                for (int slot : slots) {
                    ItemStack stack = packet.items().get(slot);
                    Triple<Item, Integer, Integer> currentRestock = restockList.get(0);

                    if (currentRestock.getMiddle() == 0) {
                        foundMaterials = true;
                        break;
                    }
                    if (!stack.isEmpty() && (stack.getCount() == 64 || !stack.isStackable())) {
                        foundMaterials = true;
                        int highestFreeSlot = Utils.findHighestFreeSlot(packet);
                        if (highestFreeSlot == -1) {
                            warning("No free slots found in inventory.");
                            checkpoints.add(0, new Pair<>(dumpStation.getLeft(), new Pair<>("dump", null)));
                            state = State.Walking;
                            return;
                        }
                        restockBacklogSlots.add(slot);
                        Triple<Item, Integer, Integer> oldTriple = restockList.remove(0);
                        restockList.add(0, Triple.of(oldTriple.getLeft(), oldTriple.getMiddle() - 1, oldTriple.getRight() - 64));
                    }
                }
                if (!foundMaterials) endRestocking();
                break;
            case AwaitMapChestResponse:
                int mapSlot = -1;
                int paneSlot = -1;
                for (int slot = 0; slot < packet.items().size() - 36; slot++) {
                    ItemStack stack = packet.items().get(slot);
                    if (stack.getItem() == Items.MAP) mapSlot = slot;
                    if (stack.getItem() == Items.GLASS_PANE) paneSlot = slot;
                }
                if (mapSlot == -1 || paneSlot == -1) {
                    warning("Not enough Empty Maps/Glass Panes in Map Material Chest");
                    return;
                }
                interactTimeout = 0;
                timeoutTicks = postRestockDelay.get();
                Utils.getOneItem(mapSlot, false, availableSlots, availableHotBarSlots, packet);
                Utils.getOneItem(paneSlot, true, availableSlots, availableHotBarSlots, packet);
                mc.player.getInventory().setSelectedSlot(availableHotBarSlots.get(0));

                BlockPos centerBlockPos = mapCorner.offset(map.length / 2 - 1, map[map.length / 2 - 1][map[0].length / 2 - 1].getRight(), map[0].length / 2 - 1);
                Vec3 center = centerBlockPos.getCenter().add(0, 0.5, 0);
                Vec3 centerEdge = mapCorner.offset(map.length / 2 - 1, 0, -1).getCenter().add(0, 0.5, 0);
                checkpoints.add(new Pair<>(centerEdge, new Pair<>("walkRestock", null)));
                checkpoints.add(new Pair<>(center, new Pair<>("fillMap", null)));
                checkpoints.add(new Pair<>(centerEdge, new Pair<>("walkRestock", null)));
                checkpoints.add(new Pair<>(cartographyTable.getRight(), new Pair<>("cartographyTable", null)));
                state = State.Walking;
                break;
            case AwaitCartographyResponse:
                interactTimeout = 0;
                timeoutTicks = postRestockDelay.get();
                boolean searchingMap = true;
                for (int slot : availableSlots) {
                    int adjustedSlot = slot;
                    if (adjustedSlot < 9) {
                        adjustedSlot += 30;
                    } else {
                        adjustedSlot -= 6;
                    }
                    ItemStack stack = packet.items().get(adjustedSlot);
                    if (searchingMap && stack.getItem() == Items.FILLED_MAP) {
                        mc.gameMode.handleContainerInput(packet.containerId(), adjustedSlot, 0, ContainerInput.QUICK_MOVE, mc.player);
                        searchingMap = false;
                    }
                }
                for (int slot : availableSlots) {
                    int adjustedSlot = slot;
                    if (adjustedSlot < 9) {
                        adjustedSlot += 30;
                    } else {
                        adjustedSlot -= 6;
                    }
                    ItemStack stack = packet.items().get(adjustedSlot);
                    if (!searchingMap && stack.getItem() == Items.GLASS_PANE) {
                        mc.gameMode.handleContainerInput(packet.containerId(), adjustedSlot, 0, ContainerInput.QUICK_MOVE, mc.player);
                        break;
                    }
                }
                mc.gameMode.handleContainerInput(packet.containerId(), 2, 0, ContainerInput.QUICK_MOVE, mc.player);
                checkpoints.add(new Pair<>(finishedMapChest.getRight(), new Pair<>("finishedMapChest", null)));
                state = State.Walking;
                break;
            case AwaitFinishedMapChestResponse:
                interactTimeout = 0;
                timeoutTicks = postRestockDelay.get();
                for (int slot = packet.items().size() - 36; slot < packet.items().size(); slot++) {
                    ItemStack stack = packet.items().get(slot);
                    if (stack.getItem() == Items.FILLED_MAP) {
                        mc.gameMode.handleContainerInput(packet.containerId(), slot, 0, ContainerInput.QUICK_MOVE, mc.player);
                        break;
                    }
                }
                startMining();
                break;
            case AwaitUsedToolChestResponse:
                interactTimeout = 0;
                for (int slot = packet.items().size() - 36; slot < packet.items().size(); slot++) {
                    ItemStack stack = packet.items().get(slot);
                    if (ToolUtils.isTool(stack)) {
                        mc.gameMode.handleContainerInput(packet.containerId(), slot, 0, ContainerInput.QUICK_MOVE, mc.player);
                    }
                }
                state = State.AwaitNBTFile;
                break;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (state == null) return;

        long now = System.nanoTime();
        long timeDifference = now - lastTickTimeNanos;
        int allowedPlacements = (int) (timeDifference / (placeDelay.get() * 1_000_000L));
        if (allowedPlacements > 0) {
            lastTickTimeNanos += (long) allowedPlacements * placeDelay.get() * 1_000_000L;
        } else if (timeDifference > placeDelay.get() * 1_000_000L) {
            lastTickTimeNanos = now;
        }

        if (!state.equals(debugPreviousState)) {
            debugPreviousState = state;
            if (debugPrints.get()) info("State changed to: \u00a7a" + state);
        }

        if (state.equals(State.AwaitMasterAllBuilt)) {
            if (SlaveSystem.allSlavesFinished()) {
                if (!endBuilding()) return;
            } else {
                return;
            }
        }

        if (state.equals(State.AwaitMasterAllBuiltSkip)) {
            if (SlaveSystem.allSlavesFinished()) {
                startMining();
            } else {
                return;
            }
        }

        if (state.equals(State.AwaitManualRepair)) {
            knownErrors.clear();
            knownErrors.addAll(getInvalidPlacements());
            if (knownErrors.isEmpty()) {
                checkpoints.add(new Pair<>(mc.player.position(), new Pair<>("lineEnd", null)));
                state = State.Walking;
            } else {
                return;
            }
        }

        if (state.equals(State.AwaitMasterAllMined)) {
            if (SlaveSystem.allSlavesFinished()) {
                minedLines = -1;
                advanceMinedLines();
                if (minedLines >= map.length) {
                    endMining();
                } else {
                    info("Not all lines mined. Redo mining.");
                    calculateMiningPath();
                    state = State.Walking;
                    for (String slave : SlaveSystem.slaves) {
                        if (minedLines >= map.length) break;
                        SlaveSystem.queueDM(slave, "mine:" + minedLines);
                        advanceMinedLines();
                        SlaveSystem.activeSlavesDict.put(slave, true);
                        SlaveSystem.finishedSlavesDict.put(slave, false);
                    }
                }
            } else {
                return;
            }
        }

        if (interactTimeout > 0) {
            interactTimeout--;
            if (interactTimeout == 0) {
                info("Interaction timed out. Interacting again...");
                if (state == State.AwaitCartographyResponse) {
                    interactWithBlock(cartographyTable.getLeft());
                } else {
                    interactWithBlock(lastInteractedChest);
                }
            }
        }

        if (jumpTimeout > 0) {
            jumpTimeout--;
            return;
        }

        if (timeoutTicks > 0) {
            if (mc.player.onGround()) timeoutTicks--;
            Utils.setForwardPressed(false);
            Utils.setBackwardPressed(false);
            Utils.setJumpPressed(false);
            return;
        }

        if (toBeSwappedSlot != -1) {
            swapIntoHotbar(toBeSwappedSlot);
            toBeSwappedSlot = -1;
            if (postSwapDelay.get() != 0) {
                timeoutTicks = postSwapDelay.get();
                return;
            }
        }

        if (!restockBacklogSlots.isEmpty()) {
            int slot = restockBacklogSlots.remove(0);
            mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot, 1, ContainerInput.QUICK_MOVE, mc.player);
            if (restockBacklogSlots.isEmpty()) {
                if (state.equals(State.AwaitRestockResponse)) {
                    endRestocking();
                }
            } else {
                timeoutTicks = invActionDelay.get();
            }
            return;
        }

        if ((state.equals(State.Mining) || state.equals(State.AwaitBlockBreak)) && miningPos != null) {
            if (MapAreaCache.getCachedBlockState(miningPos).isAir()) {
                miningPos = null;
                state = State.Mining;
            } else {
                mc.player.setXRot((float) Rotations.getPitch(miningPos));
                BlockUtils.breakBlock(miningPos, true);

                if (Math.abs(miningPos.getZ() - mc.player.getZ()) >= maxMiningRange.get()) {
                    state = State.AwaitBlockBreak;
                }

                if (state.equals(State.AwaitBlockBreak)) {
                    Utils.setForwardPressed(false);
                    Utils.setBackwardPressed(false);
                    Utils.setJumpPressed(false);
                    return;
                }
            }
        }

        if (state.equals(State.Mining)) {
            int relativeX = Math.abs(mc.player.blockPosition().getX() - mapCorner.getX());
            if (isLineMined(relativeX)) {
                miningPos = null;
                timeoutTicks = mineLineEndTimeout.get();
                if (SlaveSystem.isSlave()) {
                    Utils.setBackwardPressed(false);
                    state = State.AwaitSlaveMineLine;
                    SlaveSystem.queueMasterDM("finished");
                    return;
                } else {
                    if (minedLines < map.length) {
                        state = State.Walking;
                        if (timeoutTicks == 0) Utils.setForwardPressed(true);
                        Utils.setBackwardPressed(false);
                        calculateMiningPath();
                    } else {
                        info("Waiting for slaves to finish mining...");
                        state = State.AwaitMasterAllMined;
                        Utils.setBackwardPressed(false);
                        return;
                    }
                }
            }
        }

        if (state == State.Dumping) {
            int dumpSlot = getDumpSlot();
            if (dumpSlot == -1) {
                state = State.Walking;
                if (SlaveSystem.isSlave() && checkpoints.isEmpty()) {
                    refillMiningInventory();
                } else {
                    HashMap<Item, Integer> requiredItems = getRequiredItems();
                    Pair<ArrayList<Integer>, HashMap<Item, Integer>> invInformation = Utils.getInvInformation(requiredItems, availableSlots);
                    refillBuildingInventory(invInformation.getRight());
                }
            } else {
                if (debugPrints.get())
                    info("Dumping \u00a7a" + mc.player.getInventory().getItem(dumpSlot).getHoverName().getString() + " (slot " + dumpSlot + ")");
                InvUtils.drop().slot(dumpSlot);
                timeoutTicks = invActionDelay.get();
            }
        }

        if (state == State.AwaitNBTFile) {
            if (!prepareNextMapFile()) {
                return;
            }
            startBuilding();
        }

        if (toBeHandledInvPacket != null) {
            handleInventoryPacket(toBeHandledInvPacket);
            toBeHandledInvPacket = null;
            return;
        }

        if (closeNextInvPacket) {
            if (mc.screen != null) {
                mc.player.closeContainer();
            }
            closeNextInvPacket = false;
        }

        if (state.equals(State.Walking)) {
            Utils.setForwardPressed(true);
            Utils.setBackwardPressed(false);
        } else if (state.equals(State.Mining)) {
            Utils.setForwardPressed(false);
            Utils.setBackwardPressed(true);
        } else {
            return;
        }
        Utils.setJumpPressed(false);
        if ((mc.options.keyUp.isDown() || mc.options.keyDown.isDown()) && jumpTimeout <= 0) {
            Direction direction = Direction.fromYRot(mc.player.getYRot());
            if (mc.options.keyDown.isDown()) direction = direction.getOpposite();
            BlockPos target = mc.player.blockPosition().offset(direction.getUnitVec3i());
            if (mc.player.onGround() && !MapAreaCache.getCachedBlockState(target).isAir()
                && MapAreaCache.getCachedBlockState(target.above(1)).isAir() && MapAreaCache.getCachedBlockState(target.above(2)).isAir()) {
                jumpTimeout = jumpCoolDown.get();
                Utils.setJumpPressed(true);
            }
        }
        if (checkpoints.isEmpty()) {
            checkpoints.add(new Pair<>(mc.player.position(), new Pair<>("lineEnd", null)));
        }
        Vec3 goal = checkpoints.get(0).getLeft();
        if (PlayerUtils.distanceTo(goal.add(0, mc.player.getY() - goal.y, 0)) < checkpointBuffer.get()) {
            Pair<String, BlockPos> checkpointAction = checkpoints.get(0).getRight();
            if (debugPrints.get() && checkpointAction.getLeft() != null)
                info("Reached: \u00a7a" + checkpointAction.getLeft());
            if (snapToCheckpoints.get()) mc.player.setPos(goal.x, mc.player.getY(), goal.z);
            checkpoints.remove(0);
            switch (checkpointAction.getLeft()) {
                case "lineEnd":
                    calculateBuildingPath(false);
                    ArrayList<BlockPos> newErrors = getInvalidPlacements();
                    for (BlockPos errorPos : newErrors) {
                        BlockPos relativePos = errorPos.offset(-mapCorner.getX(), -mapCorner.getY(), -mapCorner.getZ());
                        if (logErrors.get()) {
                            info("Error at: " + errorPos.toShortString() + ". Is: "
                                + MapAreaCache.getCachedBlockState(errorPos).getBlock().getName().getString()
                                + ". Should be: " + map[relativePos.getX()][relativePos.getZ()].getLeft().getName().getString());
                        }
                        if (SlaveSystem.isSlave()) {
                            SlaveSystem.queueMasterDM("error:" + relativePos.getX() + ":" + relativePos.getZ());
                        }
                    }
                    knownErrors.addAll(newErrors);
                    break;
                case "mapMaterialChest":
                    Pair<BlockPos, Vec3> bestChest = getBestChest(Items.CARTOGRAPHY_TABLE);
                    if (bestChest != null) interactWithBlock(bestChest.getLeft());
                    state = State.AwaitMapChestResponse;
                    return;
                case "fillMap":
                    mc.getConnection().getConnection().send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, Utils.getNextInteractID(), mc.player.getYRot(), mc.player.getXRot()));
                    return;
                case "cartographyTable":
                    state = State.AwaitCartographyResponse;
                    interactWithBlock(cartographyTable.getLeft());
                    return;
                case "finishedMapChest":
                    state = State.AwaitFinishedMapChestResponse;
                    interactWithBlock(finishedMapChest.getLeft());
                    return;
                case "dump":
                    state = State.Dumping;
                    Utils.setForwardPressed(false);
                    mc.player.setYRot(dumpStation.getRight().getLeft());
                    mc.player.setXRot(dumpStation.getRight().getRight());
                    return;
                case "sleep":
                    interactWithBlock(bed.getLeft());
                    interactTimeout = 0;
                    mc.getConnection().getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SLEEPING));
                    return;
                case "refill":
                    state = State.AwaitRestockResponse;
                    interactWithBlock(checkpointAction.getRight());
                    return;
                case "startMine":
                    state = State.Mining;
                    Utils.setForwardPressed(false);
                    Utils.setBackwardPressed(true);
                    break;
                case "miningLineEnd":
                    Utils.setBackwardPressed(false);
                    checkpoints.add(new Pair<>(mc.player.position(), new Pair<>("miningLineEnd", null)));
                    break;
                case "usedToolChest":
                    state = State.AwaitUsedToolChestResponse;
                    interactWithBlock(usedToolChest.getLeft());
                    return;
            }
            if (checkpoints.isEmpty()) {
                if (state.equals(State.Walking)) {
                    if (SlaveSystem.isSlave()) {
                        checkpoints.add(new Pair<>(dumpStation.getLeft(), new Pair<>("dump", null)));
                    } else {
                        if (SlaveSystem.allSlavesFinished()) {
                            if (!endBuilding()) return;
                        } else {
                            info("Waiting for slaves to finish placing...");
                            state = State.AwaitMasterAllBuilt;
                            Utils.setForwardPressed(false);
                            return;
                        }
                    }
                }
            }
            if (checkpoints.isEmpty()) return;
            goal = checkpoints.get(0).getLeft();
        }

        if (checkpoints.isEmpty()) return;
        double lookZ = goal.z;
        if (PlayerUtils.distanceTo(goal) > 2) {
            lookZ = mc.player.getZ() + Math.max(Math.min(goal.z - mc.player.getZ(), 1), -1);
        }
        Vec3 lookPos = new Vec3(goal.x, goal.y, lookZ);
        if (state.equals(State.Walking)) {
            mc.player.setYRot((float) Rotations.getYaw(lookPos));
        } else {
            mc.player.setYRot((float) Rotations.getYaw(lookPos) + 180f);
        }

        String nextAction = checkpoints.get(0).getRight().getLeft();
        if (("".equals(nextAction) || "lineEnd".equals(nextAction)) && sprinting.get() != SprintMode.Always) {
            mc.player.setSprinting(false);
        } else if (sprinting.get() != SprintMode.Off) {
            mc.player.setSprinting(true);
        }
        final List<String> allowPlaceActions = Arrays.asList("", "lineEnd", "sprint", "miningLineEnd");
        if (!allowPlaceActions.contains(nextAction)) return;

        if (allowedPlacements <= 0) return;
        BlockPos nextBlockPos = getNextBlockPos(state.equals(State.Mining));

        if (miningPos != null || nextBlockPos == null) return;

        if (state.equals(State.Walking)) {
            if (PlayerUtils.distanceTo(nextBlockPos.getCenter()) <= interactionRange.get()) {
                tryPlacingBlock(nextBlockPos);
            }
        } else {
            Vec3 centerPos = nextBlockPos.getCenter();
            if (centerPos.z() - mc.player.getZ() > 0.5) {
                miningPos = nextBlockPos;
                mc.player.setXRot((float) Rotations.getPitch(miningPos));
                BlockState blockState = MapAreaCache.getCachedBlockState(miningPos);
                ItemStack bestTool = ToolUtils.getBestTool(toolSet, blockState);
                for (int slot : availableHotBarSlots) {
                    if (mc.player.getInventory().getItem(slot).isEmpty()) continue;
                    Item item = mc.player.getInventory().getItem(slot).getItem();
                    if (item.equals(bestTool.getItem())) {
                        InvUtils.swap(slot, false);
                        BlockUtils.breakBlock(miningPos, true);
                        state = State.Mining;
                        if (Math.abs(miningPos.getZ() - mc.player.getZ()) >= maxMiningRange.get()) {
                            state = State.AwaitBlockBreak;
                        }
                        break;
                    }
                }
            }
        }
    }

    // Restocking

    private Pair<BlockPos, Vec3> getBestChest(Item item) {
        Vec3 bestPos = null;
        BlockPos bestChestPos = null;
        ArrayList<Pair<BlockPos, Vec3>> list;
        if (item.equals(Items.CARTOGRAPHY_TABLE)) {
            list = mapMaterialChests;
        } else if (materialDict.containsKey(item)) {
            list = materialDict.get(item);
        } else {
            warning("No chest found for " + new ItemStack(item).getHoverName().getString());
            toggle();
            return null;
        }
        for (Pair<BlockPos, Vec3> p : list) {
            if (checkedChests.contains(p.getLeft())) continue;
            if (bestPos == null || PlayerUtils.distanceTo(p.getRight()) < PlayerUtils.distanceTo(bestPos)) {
                bestPos = p.getRight();
                bestChestPos = p.getLeft();
            }
        }
        if (bestPos == null || bestChestPos == null) {
            checkedChests.clear();
            return list.isEmpty() ? null : getBestChest(item);
        }
        return new Pair<>(bestChestPos, bestPos);
    }

    private void refillBuildingInventory(HashMap<Item, Integer> invMaterial) {
        restockList.clear();
        HashMap<Item, Integer> requiredItems = getRequiredItems();
        for (Item item : invMaterial.keySet()) {
            int oldAmount = requiredItems.remove(item);
            requiredItems.put(item, oldAmount - invMaterial.get(item));
        }

        for (Item item : requiredItems.keySet()) {
            Integer amount = requiredItems.get(item);
            if (amount == null || amount <= 0) continue;
            int stacks = (int) Math.ceil((float) amount / 64f);
            info("Restocking \u00a7a" + stacks + " stacks " + new ItemStack(item).getHoverName().getString() + " (" + amount + ")");
            restockList.add(0, Triple.of(item, stacks, amount));
        }
        addClosestRestockCheckpoint();
    }

    private void refillMiningInventory() {
        restockList.clear();
        HashMap<ItemStack, Integer> toolUseDict = new HashMap<>();
        for (int x = 0; x < map.length; x++) {
            for (int z = 0; z < 128; z++) {
                BlockState blockstate = MapAreaCache.getCachedBlockState(mapCorner.offset(x, map[x][z].getRight(), z));
                if (!blockstate.isAir()) {
                    ItemStack bestTool = ToolUtils.getBestTool(toolSet, blockstate);
                    if (bestTool == null) continue;
                    toolUseDict.merge(bestTool, 1, Integer::sum);
                }
            }
        }

        for (ItemStack itemStack : toolUseDict.keySet()) {
            int unbreakingLevel = 0;
            for (var e : itemStack.getEnchantments().entrySet()) {
                if (e.getKey().is(Enchantments.UNBREAKING)) {
                    unbreakingLevel = e.getValue();
                }
            }
            int rawUses = toolUseDict.get(itemStack);
            float slaveModifier = (float) (trueInterval.getRight() - trueInterval.getLeft() + 1) / (float) map.length;
            double adjustedUses = (float) rawUses / (float) (unbreakingLevel + 1) * durabilityBuffer.get() * slaveModifier;
            int itemsNeeded = (int) Math.ceil(adjustedUses / (float) itemStack.getMaxDamage());
            info("Restocking \u00a7a" + itemsNeeded + " " + itemStack.getHoverName().getString() + " (" + rawUses + " uses)");
            restockList.add(0, Triple.of(itemStack.getItem().asItem(), itemsNeeded, itemsNeeded));
        }

        addClosestRestockCheckpoint();
    }

    private void addClosestRestockCheckpoint() {
        if (restockList.isEmpty()) return;
        double smallestDistance = Double.MAX_VALUE;
        Triple<Item, Integer, Integer> closestEntry = null;
        Pair<BlockPos, Vec3> restockPos = null;
        for (Triple<Item, Integer, Integer> entry : restockList) {
            Pair<BlockPos, Vec3> bestRestockPos = getBestChest(entry.getLeft());
            if (bestRestockPos == null) {
                warning("No chest found for " + new ItemStack(entry.getLeft()).getHoverName().getString());
                toggle();
                return;
            }
            double chestDistance = PlayerUtils.distanceTo(bestRestockPos.getRight());
            if (chestDistance < smallestDistance) {
                smallestDistance = chestDistance;
                closestEntry = entry;
                restockPos = bestRestockPos;
            }
        }
        if (closestEntry == null || restockPos == null) return;
        restockList.remove(closestEntry);
        restockList.add(0, closestEntry);
        checkpoints.add(0, new Pair<>(restockPos.getRight(), new Pair<>("refill", restockPos.getLeft())));
    }

    private void endRestocking() {
        if (restockList.isEmpty()) {
            state = State.Walking;
            return;
        }
        if (restockList.get(0).getMiddle() > 0) {
            warning("Not all necessary stacks restocked. Searching for another chest...");
            checkedChests.add(lastInteractedChest);
            Pair<BlockPos, Vec3> bestRestockPos = getBestChest(getMaterialFromPos(lastInteractedChest));
            if (bestRestockPos == null) return;
            checkpoints.add(0, new Pair<>(bestRestockPos.getRight(), new Pair<>("refill", bestRestockPos.getLeft())));
        } else {
            checkedChests.clear();
            restockList.remove(0);
            addClosestRestockCheckpoint();
            if (SlaveSystem.isSlave() && checkpoints.isEmpty()) {
                state = State.AwaitSlaveMineLine;
                SlaveSystem.queueMasterDM("finished");
                return;
            }
        }
        timeoutTicks = postRestockDelay.get();
        state = State.Walking;
    }

    private Item getMaterialFromPos(BlockPos pos) {
        for (Item item : materialDict.keySet()) {
            for (Pair<BlockPos, Vec3> p : materialDict.get(item)) {
                if (p.getLeft().equals(pos)) return item;
            }
        }
        warning("Could not find material for chest position : " + pos.toShortString());
        toggle();
        return null;
    }

    // Block Interactions

    private void interactWithBlock(BlockPos chestPos) {
        Utils.setForwardPressed(false);
        mc.player.setDeltaMovement(0, 0, 0);
        mc.player.setYRot((float) Rotations.getYaw(chestPos.getCenter()));
        mc.player.setXRot((float) Rotations.getPitch(chestPos.getCenter()));

        BlockHitResult hitResult = new BlockHitResult(chestPos.getCenter(), Utils.getInteractionSide(chestPos), chestPos, false);
        BlockUtils.interact(hitResult, InteractionHand.MAIN_HAND, true);
        interactTimeout = retryInteractTimer.get();
        lastInteractedChest = chestPos;
    }

    private void tryPlacingBlock(BlockPos pos) {
        BlockPos relativePos = pos.offset(-mapCorner.getX(), -mapCorner.getY(), -mapCorner.getZ());
        Item material = map[relativePos.getX()][relativePos.getZ()].getLeft().asItem();
        for (int slot : availableHotBarSlots) {
            if (mc.player.getInventory().getItem(slot).isEmpty()) continue;
            Item foundMaterial = mc.player.getInventory().getItem(slot).getItem();
            if (foundMaterial.equals(material)) {
                BlockUtils.place(pos, InteractionHand.MAIN_HAND, slot, rotatePlace.get(), 50, true, true, false);
                if (material.equals(lastSwappedMaterial)) lastSwappedMaterial = null;
                return;
            }
        }
        for (int slot : availableSlots) {
            if (mc.player.getInventory().getItem(slot).isEmpty() || availableHotBarSlots.contains(slot)) continue;
            Item foundMaterial = mc.player.getInventory().getItem(slot).getItem();
            if (foundMaterial.equals(material)) {
                lastSwappedMaterial = material;
                toBeSwappedSlot = slot;
                Utils.setForwardPressed(false);
                mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, mc.player.getDeltaMovement().y, 0);
                timeoutTicks = preSwapDelay.get();
                return;
            }
        }
        if (material.equals(lastSwappedMaterial)) return;
        info("No " + new ItemStack(material).getHoverName().getString() + " found in inventory. Resetting...");
        mc.player.setDeltaMovement(0, 0, 0);
        Vec3 pathCheckpoint = new Vec3(mc.player.getX(), mapCorner.getCenter().y, mapCorner.north().getCenter().z);
        checkpoints.add(0, new Pair<>(mc.player.position(), new Pair<>("walkRestock", null)));
        checkpoints.add(0, new Pair<>(pathCheckpoint, new Pair<>("walkRestock", null)));
        checkpoints.add(0, new Pair<>(dumpStation.getLeft(), new Pair<>("dump", null)));
        checkpoints.add(0, new Pair<>(pathCheckpoint, new Pair<>("walkRestock", null)));
    }

    private BlockPos getNextBlockPos(boolean mining) {
        int relativeX = mc.player.blockPosition().getX() - mapCorner.getX();
        int lowerX = mining ? relativeX : workingInterval.getLeft();
        int upperX = mining ? relativeX : workingInterval.getRight();
        for (int x = lowerX; x <= upperX; x++) {
            for (int z = 0; z < 128; z++) {
                int adjustedZ = mining ? 127 - z : z;
                BlockPos blockPos = mapCorner.offset(x, map[x][adjustedZ].getRight(), adjustedZ);
                BlockState blockState = MapAreaCache.getCachedBlockState(blockPos);
                if (blockState.isAir() ^ mining) {
                    return blockPos;
                }
            }
        }
        return null;
    }

    // Path and Building Management

    private void calculateBuildingPath(boolean sprintFirst) {
        checkpoints.clear();
        for (int x = workingInterval.getLeft(); x <= workingInterval.getRight(); x++) {
            boolean lineFinished = true;
            for (int z = 0; z < 128; z++) {
                BlockState blockstate = MapAreaCache.getCachedBlockState(mapCorner.offset(x, map[x][z].getRight(), z));
                if (blockstate.isAir()) {
                    lineFinished = false;
                    break;
                }
            }
            if (lineFinished) continue;
            Vec3 cp1 = mapCorner.getCenter().add(x, 0.5, -1);
            Vec3 cp2 = mapCorner.getCenter().add(x, map[x][map[0].length - 2].getRight() + 0.5, map[0].length - 2);
            checkpoints.add(new Pair<>(cp1, new Pair<>("", null)));
            checkpoints.add(new Pair<>(cp2, new Pair<>("", null)));
            checkpoints.add(new Pair<>(cp1, new Pair<>("lineEnd", null)));
        }
        if (!checkpoints.isEmpty() && sprintFirst) {
            Pair<Vec3, Pair<String, BlockPos>> firstPoint = checkpoints.remove(0);
            checkpoints.add(0, new Pair<>(firstPoint.getLeft(), new Pair<>("sprint", firstPoint.getRight().getRight())));
        }
    }

    private void calculateMiningPath() {
        if (minedLines >= map.length) return;
        checkpoints.clear();
        Vec3 cp1 = mapCorner.getCenter().add(minedLines, 0.5, -mineLineEndOffset.get());
        Vec3 cp2 = mapCorner.getCenter().add(minedLines, map[minedLines][0].getRight() + 0.5, -1);
        for (int i = 0; i < map[minedLines].length - 1; i++) {
            cp2 = mapCorner.getCenter().add(minedLines, map[minedLines][i].getRight() + 0.5, i);
            if (i + 2 >= map[minedLines].length) break;
            BlockPos airPos = mapCorner.offset(minedLines, map[minedLines][i + 2].getRight(), i + 2);
            if (MapAreaCache.getCachedBlockState(airPos).isAir()) break;
        }
        checkpoints.add(new Pair<>(cp1, new Pair<>("miningLineStart", null)));
        checkpoints.add(new Pair<>(cp2, new Pair<>("startMine", null)));
        checkpoints.add(new Pair<>(cp1, new Pair<>("miningLineEnd", null)));
        advanceMinedLines();
    }

    private void advanceMinedLines() {
        while (minedLines < map.length) {
            minedLines++;
            if (!isLineMined(minedLines)) return;
        }
    }

    private boolean isLineMined(int line) {
        if (line >= map.length) return false;
        for (int z = 0; z < map[line].length; z++) {
            BlockState blockstate = MapAreaCache.getCachedBlockState(mapCorner.offset(line, map[line][z].getRight(), z));
            if (!blockstate.isAir()) return false;
        }
        return true;
    }

    private void startBuilding() {
        info("Start building map");
        if (!SlaveSystem.isSlave()) SlaveSystem.startAllSlaves();
        if (availableSlots.isEmpty()) setupSlots();
        MapAreaCache.reset(mapCorner);
        calculateBuildingPath(true);
        checkpoints.add(0, new Pair<>(dumpStation.getLeft(), new Pair<>("dump", null)));
        if (sleep.get()) {
            if (bed == null) {
                warning("Can not sleep because bed was not set.");
            } else {
                checkpoints.add(0, new Pair<>(bed.getRight(), new Pair<>("sleep", null)));
            }
        }
        state = State.Walking;
    }

    private boolean endBuilding() {
        if (!knownErrors.isEmpty()) {
            if (errorAction.get() == ErrorAction.ManualRepair) {
                workingInterval = new Pair<>(0, map.length - 1);
                info("Found errors: ");
                for (int i = knownErrors.size() - 1; i >= 0; i--) {
                    info("Pos: " + knownErrors.get(i).toShortString());
                }
                state = State.AwaitManualRepair;
                Utils.setForwardPressed(false);
                warning("ErrorAction is ManualRepair. The module resumes when all errors are fixed.");
                return false;
            }
        }
        info("Finished building map");
        state = State.Walking;
        workingInterval = trueInterval;
        knownErrors.clear();
        SlaveSystem.setAllSlavesUnfinished();
        Pair<BlockPos, Vec3> bestChest = getBestChest(Items.CARTOGRAPHY_TABLE);
        if (bestChest == null) return false;
        checkpoints.add(new Pair<>(dumpStation.getLeft(), new Pair<>("dump", null)));
        checkpoints.add(new Pair<>(bestChest.getRight(), new Pair<>("mapMaterialChest", bestChest.getLeft())));
        try {
            if (moveToFinishedFolder.get())
                mapFile.renameTo(new File(mapFile.getParentFile().getAbsolutePath() + File.separator + "_finished_maps" + File.separator + mapFile.getName()));
        } catch (Exception e) {
            warning("Failed to move map file " + mapFile.getName() + " to finished map folder");
            e.printStackTrace();
        }
        return true;
    }

    private void startMining() {
        info("Start mining map");
        minedLines = -1;
        advanceMinedLines();
        calculateMiningPath();
        refillMiningInventory();
        state = State.Walking;
        if (sleep.get()) {
            if (bed == null) {
                warning("Can not sleep because bed was not set.");
            } else {
                checkpoints.add(0, new Pair<>(bed.getRight(), new Pair<>("sleep", null)));
            }
        }
        for (String slave : SlaveSystem.slaves) {
            if (minedLines >= map.length) break;
            SlaveSystem.queueDM(slave, "mine:" + minedLines);
            advanceMinedLines();
            SlaveSystem.activeSlavesDict.put(slave, true);
            SlaveSystem.finishedSlavesDict.put(slave, false);
        }
    }

    private void endMining() {
        info("Finished mining map");
        SlaveSystem.sendToAllSlaves("start");
        for (String slave : SlaveSystem.activeSlavesDict.keySet()) {
            SlaveSystem.activeSlavesDict.put(slave, true);
        }
        SlaveSystem.setAllSlavesUnfinished();
        checkpoints.clear();
        checkpoints.add(0, new Pair<>(usedToolChest.getRight(), new Pair<>("usedToolChest", null)));
        state = State.Walking;
    }

    public ArrayList<BlockPos> getInvalidPlacements() {
        ArrayList<BlockPos> invalidPlacements = new ArrayList<>();
        for (int x = workingInterval.getRight(); x >= workingInterval.getLeft(); x--) {
            for (int z = 127; z >= 0; z--) {
                BlockPos relativePos = new BlockPos(x, map[x][z].getRight(), z);
                BlockPos absolutePos = mapCorner.offset(relativePos.getX(), relativePos.getY(), relativePos.getZ());
                if (knownErrors.contains(absolutePos)) continue;
                BlockState blockState = MapAreaCache.getCachedBlockState(absolutePos);
                Block block = blockState.getBlock();
                if (!blockState.isAir()) {
                    if (map[x][z].getLeft() != block) invalidPlacements.add(absolutePos);
                }
            }
        }
        return invalidPlacements;
    }

    // Inventory Management

    private boolean setupSlots() {
        availableSlots = Utils.getAvailableSlots(materialDict);
        for (int slot : availableSlots) {
            if (slot < 9) {
                availableHotBarSlots.add(slot);
            }
        }
        info("Inventory slots available for building: " + availableSlots);
        if (availableHotBarSlots.isEmpty()) {
            warning("No free slots found in hot-bar!");
            availableSlots.clear();
            toggle();
            return false;
        }
        if (availableSlots.size() < 2) {
            warning("You need at least 2 free inventory slots!");
            availableSlots.clear();
            toggle();
            return false;
        }
        return true;
    }

    private int getDumpSlot() {
        HashMap<Item, Integer> requiredItems = getRequiredItems();
        Pair<ArrayList<Integer>, HashMap<Item, Integer>> invInformation = Utils.getInvInformation(requiredItems, availableSlots);
        if (invInformation.getLeft().isEmpty()) {
            return -1;
        }
        return invInformation.getLeft().get(0);
    }

    private HashMap<Item, Integer> getRequiredItems() {
        HashMap<Item, Integer> requiredItems = new HashMap<>();
        for (int x = workingInterval.getLeft(); x <= workingInterval.getRight(); x++) {
            for (int z = 0; z < 128; z++) {
                BlockState blockState = MapAreaCache.getCachedBlockState(mapCorner.offset(x, map[x][z].getRight(), z));
                if (blockState.isAir() && map[x][z] != null) {
                    Item material = map[x][z].getLeft().asItem();
                    requiredItems.merge(material, 1, Integer::sum);
                    if (Utils.stacksRequired(requiredItems.values()) > availableSlots.size()) {
                        requiredItems.put(material, requiredItems.get(material) - 1);
                        return requiredItems;
                    }
                }
            }
        }
        return requiredItems;
    }

    private void swapIntoHotbar(int slot) {
        Map<Item, Integer> itemSlot = new HashMap<>();
        Map<Item, Integer> itemDistance = new HashMap<>();
        Map<Item, Integer> itemFrequency = new HashMap<>();

        int targetSlot = availableHotBarSlots.get(0);

        for (int hotbarSlot : availableHotBarSlots) {
            ItemStack stack = mc.player.getInventory().getItem(hotbarSlot);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                itemSlot.put(item, hotbarSlot);
                itemDistance.put(item, -1);
                itemFrequency.put(item, 0);
            } else {
                targetSlot = hotbarSlot;
                break;
            }
        }

        if (mc.player.getInventory().getItem(targetSlot).isEmpty()) {
            Utils.performSwap(slot, targetSlot);
            return;
        }

        int blockCounter = 0;
        for (int x = workingInterval.getLeft(); x <= workingInterval.getRight(); x++) {
            for (int z = 0; z < 128; z++) {
                if (!Utils.isInInterval(workingInterval, x)) break;
                blockCounter++;

                BlockState state = MapAreaCache.getCachedBlockState(mapCorner.offset(x, map[x][z].getRight(), z));
                if (state.isAir()) {
                    Block block = map[x][z].getLeft();
                    if (block == null) continue;

                    Item item = block.asItem();

                    if (itemDistance.containsKey(item) &&
                        itemDistance.get(item) == -1) {
                        itemDistance.put(item, blockCounter);
                    }
                }
            }
        }

        for (int hotbarSlot : availableHotBarSlots) {
            ItemStack stack = mc.player.getInventory().getItem(hotbarSlot);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                itemFrequency.put(item, itemFrequency.get(item) + 1);
            }
        }

        Item bestItem = null;
        int bestDistance = -2;
        int bestFrequency = -1;

        for (Item item : itemSlot.keySet()) {
            int distance = itemDistance.get(item);
            int frequency = itemFrequency.get(item);

            boolean better = false;

            if (distance == -1 && bestDistance != -1) {
                better = true;
            } else if (frequency > bestFrequency) {
                better = true;
            } else if (frequency == bestFrequency && distance > bestDistance && bestDistance != -1) {
                better = true;
            }

            if (better) {
                bestItem = item;
                bestDistance = distance;
                bestFrequency = frequency;
            }
        }

        if (bestItem != null) {
            targetSlot = itemSlot.get(bestItem);
        }

        Utils.performSwap(slot, targetSlot);
    }

    // MapPrinter Interface for Slave Logic

    public void setInterval(Pair<Integer, Integer> interval) {
        workingInterval = interval;
        trueInterval = interval;
    }

    public void addError(BlockPos relPos) {
        BlockPos absPos = mapCorner.offset(relPos.getX(), map[relPos.getX()][relPos.getZ()].getRight(), relPos.getZ());
        if (!knownErrors.contains(absPos)) knownErrors.add(new BlockPos(absPos));
    }

    public void pause() {
        if (!state.equals(State.AwaitSlaveContinue)) {
            oldState = state;
            state = State.AwaitSlaveContinue;
            Utils.setForwardPressed(false);
        }
    }

    public void start() {
        if (availableSlots.isEmpty()) {
            state = State.AwaitNBTFile;
            return;
        }
        if (state.equals(State.AwaitSlaveContinue)) {
            state = oldState;
            return;
        }
        if (state.equals(State.AwaitSlaveMineLine)) {
            checkpoints.clear();
            checkpoints.add(0, new Pair<>(usedToolChest.getRight(), new Pair<>("usedToolChest", null)));
            state = State.Walking;
        }
    }

    public boolean getActivationReset() {
        return activationReset.get();
    }

    public void skipBuilding() {
        if (availableSlots.isEmpty()) setupSlots();
        knownErrors.clear();
        checkpoints.clear();
        if (SlaveSystem.isSlave()) {
            checkpoints.add(new Pair<>(dumpStation.getLeft(), new Pair<>("dump", null)));
            state = State.Walking;
        } else {
            try {
                if (moveToFinishedFolder.get())
                    mapFile.renameTo(new File(mapFile.getParentFile().getAbsolutePath() + File.separator + "_finished_maps" + File.separator + mapFile.getName()));
            } catch (Exception e) {
                warning("Failed to move map file " + mapFile.getName() + " to finished map folder");
                e.printStackTrace();
            }
            state = State.AwaitMasterAllBuiltSkip;
        }
    }

    public void slaveFinished(String slave) {
        if (minedLines < map.length) {
            SlaveSystem.queueDM(slave, "mine:" + minedLines);
            advanceMinedLines();
            SlaveSystem.activeSlavesDict.put(slave, true);
            SlaveSystem.finishedSlavesDict.put(slave, false);
        }
    }

    public void mineLine(int lines) {
        minedLines = lines;
        calculateMiningPath();
        state = State.Walking;
    }

    private void warnPathChanged() {
        if (checkpoints != null && !activationReset.get()) {
            String reString = isActive() ? "re" : "";
            warning("The custom path is only applied if the module is " + reString + "started with Activation Reset enabled!");
        }
    }

    // Config System

    private void saveConfig(File configFile) {
        if (configFile == null) {
            error("No config file name selected.");
            return;
        }
        if (cartographyTable == null || finishedMapChest == null || dumpStation == null || mapCorner == null
            || materialDict.isEmpty() || usedToolChest == null || toolSet.isEmpty()) {
            error("Cannot save config: Missing required data.");
            return;
        }
        try {
            ConfigSerializer.writeToJson(
                configFile.toPath(), "staircased", cartographyTable, finishedMapChest,
                usedToolChest, bed, mapMaterialChests, dumpStation, mapCorner, materialDict, toolSet);
            Component configText = Component.literal(configFile.getName())
                .withStyle(Style.EMPTY
                    .withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent.OpenFile(configFile.getAbsolutePath()))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open config")))
                    .withUnderlined(true));
            info(Component.literal("Successfully saved config to: ").append(configText));
        } catch (IOException e) {
            error("Failed to create config file.");
        }
    }

    private boolean loadConfig(File configFile) {
        if (configFile == null || !configFile.exists() || state == null) {
            warning("Could not find config file.");
            return false;
        }
        List<State> allowedStates = List.of(
            State.SelectingChests, State.SelectingBed, State.SelectingFinishedMapChest,
            State.SelectingUsedPickaxeChest, State.SelectingDumpStation, State.SelectingTable,
            State.SelectingMapArea, State.AwaitRegisterResponse
        );
        if (!allowedStates.contains(state)) {
            error("Can only load config during the registration phase.");
            return false;
        }

        try {
            ConfigDeserializer.ConfigData data = ConfigDeserializer.readFromJson(configFile.toPath());

            if (!data.type.equals("staircased")) {
                error("Config file is of type " + data.type + " and not 'staircased'.");
                return false;
            }
            if (data.cartographyTable == null || data.finishedMapChest == null || data.dumpStation == null || data.mapCorner == null
                || data.materialDict.isEmpty() || data.usedToolChest == null || toolSet == null) {
                error("Config file is missing required data.");
                return false;
            }
            this.cartographyTable = data.cartographyTable;
            this.finishedMapChest = data.finishedMapChest;
            this.usedToolChest = data.usedToolChest;
            this.bed = data.bed;
            this.mapMaterialChests = data.mapMaterialChests;
            this.dumpStation = data.dumpStation;
            this.mapCorner = data.mapCorner;
            MapAreaCache.reset(mapCorner);
            this.materialDict = data.materialDict;
            this.toolSet = data.toolSet;
            Component configText = Component.literal(configFile.getName())
                .withStyle(Style.EMPTY
                    .withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent.OpenFile(configFile.getAbsolutePath()))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open config")))
                    .withUnderlined(true));
            info(Component.literal("Successfully loaded config: ").append(configText));
            info("Interact with the Start Block to start printing.");
            state = State.SelectingChests;
        } catch (IOException e) {
            error("Failed to read config file.");
        }
        return true;
    }

    // NBT file handling

    private boolean prepareNextMapFile() {
        mapFile = Utils.getNextMapFile(mapFolder, startedFiles, moveToFinishedFolder.get());
        if (mapFile == null) {
            if (disableOnFinished.get()) {
                info("All nbt files finished");
                toggle();
            }
            return false;
        }
        if (!loadNBTFile()) {
            warning("Failed to read nbt file.");
            toggle();
            return false;
        }
        return true;
    }

    private boolean loadNBTFile() {
        try {
            info("Building: \u00a7a" + mapFile.getName());
            CompoundTag nbt = NbtIo.readCompressed(mapFile.toPath(), NbtAccounter.unlimitedHeap());
            ListTag paletteList = nbt.getListOrEmpty("palette");
            blockPaletteDict = Utils.getBlockPalette(paletteList);

            ListTag blockList = nbt.getListOrEmpty("blocks");
            map = generateMapArray(blockList);

            info("Requirements: ");
            for (Pair<Block, Integer> p : blockPaletteDict.values()) {
                if (p.getRight() == 0) continue;
                info(p.getLeft().getName().getString() + ": " + p.getRight());
            }

            for (int x = 0; x < map.length; x++) {
                for (int z = 0; z < map[x].length; z++) {
                    if (map[x][z] == null) {
                        warning("No 128x129 (extra line on north side) map present in file: " + mapFile.getName());
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private Pair<Block, Integer>[][] generateMapArray(ListTag blockList) {
        Pair<Block, Integer>[][] absoluteHeightMap = new Pair[128][129];
        for (int i = 0; i < blockList.size(); i++) {
            CompoundTag block = blockList.getCompoundOrEmpty(i);
            if (block == null || block.isEmpty()) continue;
            if (!block.contains("state")) continue;
            int blockId = block.getIntOr("state", 0);
            if (!blockPaletteDict.containsKey(blockId)) continue;
            if (!block.contains("pos")) continue;
            ListTag pos = block.getListOrEmpty("pos");
            if (pos == null || pos.size() < 3) continue;
            int x = pos.getIntOr(0, 0);
            int y = pos.getIntOr(1, 0);
            int z = pos.getIntOr(2, 0);

            if (absoluteHeightMap[x][z] == null || absoluteHeightMap[x][z].getRight() < y) {
                Block material = blockPaletteDict.get(blockId).getLeft();
                absoluteHeightMap[x][z] = new Pair<>(material, y);
            }
            if (z > 0) {
                Pair<Block, Integer> oldPair = blockPaletteDict.get(blockId);
                blockPaletteDict.put(blockId, new Pair<>(oldPair.getLeft(), oldPair.getRight() + 1));
            }
        }

        Pair<Block, Integer>[][] smoothedHeightMap = new Pair[128][128];
        for (int x = 0; x < absoluteHeightMap.length; x++) {
            int totalYDiff = 0;
            for (int z = 1; z < absoluteHeightMap[0].length; z++) {
                int predecessorY = absoluteHeightMap[x][z - 1].getRight();
                int currentY = absoluteHeightMap[x][z].getRight();
                totalYDiff += Math.max(-1, Math.min(currentY - predecessorY, 1));
                smoothedHeightMap[x][z - 1] = new Pair<>(absoluteHeightMap[x][z].getLeft(), totalYDiff);
            }
        }
        return smoothedHeightMap;
    }

    // Rendering

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WTable table = new WTable();
        list.add(table);

        File configFolder = new File(mapFolder, "_configs");
        if (!configFolder.exists()) return table;

        table.add(theme.label("Configurations: "));
        WButton saveButton = table.add(theme.button("Save Config")).widget();
        saveButton.action = () -> {
            String path = TinyFileDialogs.tinyfd_saveFileDialog(
                "Save Config",
                new File(configFolder, "staircased-printer-config.json").getAbsolutePath(),
                null, null
            );
            if (path != null) saveConfig(new File(path));
        };

        WButton loadButton = table.add(theme.button("Load Config")).widget();
        loadButton.action = () -> {
            String path = TinyFileDialogs.tinyfd_openFileDialog(
                "Load Config",
                new File(configFolder, "staircased-printer-config.json").getAbsolutePath(),
                null, null, false
            );
            if (path != null) loadConfig(new File(path));
        };
        table.row();

        WTable slaveTable = new WTable();
        list.add(slaveTable);
        SlaveTableController slaveController = new SlaveTableController(slaveTable, theme, true);
        slaveController.rebuild();
        SlaveSystem.tableController = slaveController;
        return list;
    }

    @Override
    public String getInfoString() {
        if (mapFile != null) {
            return mapFile.getName();
        } else {
            return "None";
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mapCorner == null || !render.get()) return;

        event.renderer.box(mapCorner.getX(), mapCorner.getY(), mapCorner.getZ(), mapCorner.getX() + 128, mapCorner.getY(), mapCorner.getZ() + 128, color.get(), color.get(), ShapeMode.Lines, 0);

        if (renderMap.get() && !(state.equals(State.Mining) || state.equals(State.AwaitBlockBreak))) {
            for (int x = workingInterval.getLeft(); x <= workingInterval.getRight(); x++) {
                for (int z = 0; z < map[0].length; z++) {
                    BlockPos renderPos = mapCorner.offset(x, map[x][z].getRight(), z);
                    if (!MapAreaCache.getCachedBlockState(renderPos).isAir()) continue;
                    event.renderer.box(renderPos, color.get(), color.get(), ShapeMode.Lines, 0);
                }
            }
        }

        if (knownErrors != null) {
            for (BlockPos pos : knownErrors) {
                event.renderer.box(pos, color.get(), color.get(), ShapeMode.Lines, 0);
            }
        }

        ArrayList<Pair<BlockPos, Vec3>> renderedPairs = new ArrayList<>();
        for (ArrayList<Pair<BlockPos, Vec3>> list : materialDict.values()) {
            renderedPairs.addAll(list);
        }
        renderedPairs.addAll(mapMaterialChests);
        for (Pair<BlockPos, Vec3> pair : renderedPairs) {
            if (renderChestPositions.get())
                event.renderer.box(pair.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
            if (renderOpenPositions.get()) {
                Vec3 openPos = pair.getRight();
                event.renderer.box(openPos.x - indicatorSize.get(), openPos.y - indicatorSize.get(), openPos.z - indicatorSize.get(), openPos.x + indicatorSize.get(), openPos.y + indicatorSize.get(), openPos.z + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
        }

        if (renderCheckpoints.get()) {
            for (Pair<Vec3, Pair<String, BlockPos>> pair : checkpoints) {
                Vec3 cp = pair.getLeft();
                event.renderer.box(cp.x - indicatorSize.get(), cp.y - indicatorSize.get(), cp.z - indicatorSize.get(), cp.x + indicatorSize.get(), cp.y + indicatorSize.get(), cp.z + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
        }

        if (renderSpecialInteractions.get()) {
            if (usedToolChest != null) {
                event.renderer.box(usedToolChest.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(usedToolChest.getRight().x - indicatorSize.get(), usedToolChest.getRight().y - indicatorSize.get(), usedToolChest.getRight().z - indicatorSize.get(), usedToolChest.getRight().x + indicatorSize.get(), usedToolChest.getRight().y + indicatorSize.get(), usedToolChest.getRight().z + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (bed != null) {
                event.renderer.box(bed.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(bed.getRight().x - indicatorSize.get(), bed.getRight().y - indicatorSize.get(), bed.getRight().z - indicatorSize.get(), bed.getRight().x + indicatorSize.get(), bed.getRight().y + indicatorSize.get(), bed.getRight().z + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (cartographyTable != null) {
                event.renderer.box(cartographyTable.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(cartographyTable.getRight().x - indicatorSize.get(), cartographyTable.getRight().y - indicatorSize.get(), cartographyTable.getRight().z - indicatorSize.get(), cartographyTable.getRight().x + indicatorSize.get(), cartographyTable.getRight().y + indicatorSize.get(), cartographyTable.getRight().z + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (dumpStation != null) {
                event.renderer.box(dumpStation.getLeft().x - indicatorSize.get(), dumpStation.getLeft().y - indicatorSize.get(), dumpStation.getLeft().z - indicatorSize.get(), dumpStation.getLeft().x + indicatorSize.get(), dumpStation.getLeft().y + indicatorSize.get(), dumpStation.getLeft().z + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
            if (finishedMapChest != null) {
                event.renderer.box(finishedMapChest.getLeft(), color.get(), color.get(), ShapeMode.Lines, 0);
                event.renderer.box(finishedMapChest.getRight().x - indicatorSize.get(), finishedMapChest.getRight().y - indicatorSize.get(), finishedMapChest.getRight().z - indicatorSize.get(), finishedMapChest.getRight().x + indicatorSize.get(), finishedMapChest.getRight().y + indicatorSize.get(), finishedMapChest.getRight().z + indicatorSize.get(), color.get(), color.get(), ShapeMode.Both, 0);
            }
        }
    }

    private enum State {
        SelectingMapArea, SelectingTable, SelectingUsedPickaxeChest, SelectingDumpStation,
        SelectingFinishedMapChest, SelectingBed, SelectingChests, AwaitRegisterResponse,
        AwaitRestockResponse, AwaitMapChestResponse, AwaitFinishedMapChestResponse,
        AwaitUsedToolChestResponse, AwaitCartographyResponse, AwaitNBTFile, AwaitBlockBreak,
        AwaitMasterAllBuilt, AwaitMasterAllBuiltSkip, AwaitMasterAllMined, AwaitSlaveContinue,
        AwaitSlaveMineLine, AwaitManualRepair, Walking, Mining, Dumping
    }

    private enum SprintMode {
        Off, NotPlacing, Always
    }

    private enum ErrorAction {
        Ignore, ManualRepair
    }
}
