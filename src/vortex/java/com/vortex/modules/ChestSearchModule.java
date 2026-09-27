package com.vortex.modules;

import com.vortex.ChestSearchData;
import com.vortex.Vortex;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Collectors;

public class ChestSearchModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> passive = sgGeneral.add(new BoolSetting.Builder()
        .name("passive")
        .description("Does not open containers on its own. Only records chests the player opens manually.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> searchRange = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("Search containers within this range.")
        .defaultValue(4).min(1).max(10).sliderRange(1, 10).build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay in ticks between interactions.")
        .defaultValue(5).min(0).max(40).build()
    );

    private final Setting<Boolean> highlightSearched = sgGeneral.add(new BoolSetting.Builder()
        .name("highlight-searched")
        .defaultValue(true).build()
    );

    private final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("box-render-mode")
        .defaultValue(ShapeMode.Both).build()
    );

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(200, 50, 50, 80)).build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(200, 50, 50, 255)).build()
    );

    private final Setting<SettingColor> matchSideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("match-side-color")
        .description("The side color of the bounding box around chests that match the search box query.")
        .defaultValue(new SettingColor(0, 200, 100, 100)).build()
    );

    private final Setting<SettingColor> matchLineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("match-line-color")
        .description("The line color of the bounding box around chests that match the search box query.")
        .defaultValue(new SettingColor(0, 200, 100, 255)).build()
    );

    private final HashSet<BlockPos> searched = new HashSet<>();
    private boolean awaiting;
    private BlockPos[] currPos;
    private int tickCounter;
    private int scannedThisSession;
    private String searchQuery = "";

    public ChestSearchModule() {
        super(Vortex.CATEGORY, "chest-search", "Scans nearby containers and logs their contents per-chest for searching.");
    }

    @Override
    public void onActivate() {
        ChestSearchData data = ChestSearchData.getInstance();
        data.load();
        searched.clear();
        for (ChestSearchData.ChestEntry entry : data.getAll()) {
            searched.add(entry.pos());
        }
        awaiting = false;
        currPos = new BlockPos[2];
        tickCounter = 0;
        scannedThisSession = 0;
    }

    @Override
    public void onDeactivate() {
        ChestSearchData.getInstance().save();
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();

        list.add(theme.label("Scanned this session: " + scannedThisSession));
        list.add(theme.label("Total chests logged: " + ChestSearchData.getInstance().size()));

        WTextBox searchBox = theme.textBox("Search item ID (e.g. white bed)", searchQuery);
        searchBox.action = () -> {
            searchQuery = searchBox.get().trim();
            if (!searchQuery.isEmpty()) {
                info("Search matches: " + ChestSearchData.getInstance().search(searchQuery).size() + " chest(s) for \"" + searchQuery + "\"");
            }
        };
        searchBox.actionOnUnfocused = searchBox.action;
        list.add(searchBox).expandX();

        WButton clearSearch = list.add(theme.button("Clear search highlight")).widget();
        clearSearch.action = () -> {
            searchQuery = "";
            searchBox.set("");
        };

        WButton searchBtn = list.add(theme.button("Search Items in Chat")).widget();
        searchBtn.action = () -> {
            info("Use .chestsearch <item> or .cs <item> to search logged chests.");
            info("Example: .cs diamond");
        };

        WButton rescanAll = list.add(theme.button("Re-scan All Logged Chests")).widget();
        rescanAll.action = () -> {
            searched.clear();
            ChestSearchData.getInstance().clear();
            info("Cleared all logged data. Toggle the module off and on to re-scan.");
        };

        WButton clearData = list.add(theme.button("Clear All Data")).widget();
        clearData.action = () -> {
            searched.clear();
            ChestSearchData.getInstance().clear();
            info("All chest data cleared.");
        };

        return list;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (highlightSearched.get()) {
            for (BlockPos pos : searched) {
                RenderUtils.renderTickingBlock(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0, 8, true, false);
            }
        }
        renderMatches(event);
    }

    private void renderMatches(Render3DEvent event) {
        if (searchQuery.isEmpty()) return;
        for (BlockPos pos : getMatchingPositions()) {
            RenderUtils.renderTickingBlock(pos, matchSideColor.get(), matchLineColor.get(), shapeMode.get(), 0, 8, true, false);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (passive.get()) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen) return;

        if (tickCounter < delay.get()) {
            tickCounter++;
            return;
        }
        tickCounter = 0;

        BlockIterator.register(searchRange.get(), searchRange.get(), (blockPos, blockState) -> {
            if (awaiting || searched.contains(blockPos.immutable())) return;
            if (!isContainer(blockState)) return;

            Vec3 vec = new Vec3(blockPos.getX(), blockPos.getY(), blockPos.getZ());
            BlockHitResult hitResult = new BlockHitResult(vec, Direction.UP, blockPos, false);
            if (mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult) == InteractionResult.SUCCESS) {
                awaiting = true;
                mc.player.swing(InteractionHand.MAIN_HAND);
                currPos[0] = blockPos.immutable();
                currPos[1] = null;

                if (blockState.getBlock() instanceof ChestBlock) {
                    ChestType chestType = blockState.getValue(ChestBlock.TYPE);
                    if (chestType == ChestType.LEFT || chestType == ChestType.RIGHT) {
                        Direction facing = blockState.getValue(ChestBlock.FACING);
                        currPos[1] = blockPos.relative(
                            chestType == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise()
                        );
                    }
                }
            }
        });
    }

    @EventHandler
    private void onInventory(InventoryEvent event) {
        AbstractContainerMenu handler = mc.player.containerMenu;
        awaiting = false;

        boolean autoOpened = currPos[0] != null;
        for (BlockPos pos : currPos) {
            if (pos != null) searched.add(pos);
        }

        if (!autoOpened && passive.get() && mc.hitResult instanceof BlockHitResult hit && isContainer(mc.level.getBlockState(hit.getBlockPos()))) {
            BlockPos pos = hit.getBlockPos().immutable();
            searched.add(pos);
            currPos[0] = pos;
            currPos[1] = null;
        }

        BlockPos[] positions = currPos != null ? currPos.clone() : null;
        if (positions == null || (positions[0] == null && positions[1] == null)) return;

        List<ItemStack> items = new ArrayList<>();
        NonNullList<Slot> slots = handler.slots;
        int containerSlots = slots.size() - 36;
        for (int i = 0; i < containerSlots; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (!stack.isEmpty()) {
                items.add(stack);
                extractShulkerItems(stack, items);
            }
        }

        String world = mc.level != null ? mc.level.dimension().identifier().toString() : "unknown";

        for (BlockPos pos : positions) {
            if (pos != null) {
                ChestSearchData.getInstance().addOrUpdate(pos, world, items);
            }
        }
        scannedThisSession++;
        ChestSearchData.getInstance().save();
        currPos[0] = null;
        currPos[1] = null;

        mc.player.closeContainer();
    }

    private void extractShulkerItems(ItemStack stack, List<ItemStack> out) {
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
            ItemContainerContents container = stack.get(DataComponents.CONTAINER);
            if (container != null) {
                container.nonEmptyItemCopyStream().forEach(out::add);
            }
        }
    }

    private boolean isContainer(BlockState state) {
        Block b = state.getBlock();
        return b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL
            || b instanceof ShulkerBoxBlock
            || b == Blocks.DISPENSER || b == Blocks.DROPPER
            || b == Blocks.HOPPER || b == Blocks.FURNACE
            || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER;
    }

    private List<BlockPos> getMatchingPositions() {
        String norm = normalize(searchQuery);
        if (norm.isEmpty()) return Collections.emptyList();
        return ChestSearchData.getInstance().getAll().stream()
            .filter(chest -> chest.items.stream().anyMatch(item -> {
                try {
                    Identifier id = Identifier.tryParse(item.itemId);
                    if (id == null) return false;
                    String displayName = BuiltInRegistries.ITEM.getOptional(id).map(it -> it.getName(it.getDefaultInstance()).getString()).orElse("");
                    return normalize(displayName).contains(norm) || normalize(item.itemId).contains(norm);
                } catch (Exception e) {
                    return normalize(item.itemId).contains(norm);
                }
            }))
            .map(ChestSearchData.ChestEntry::pos)
            .collect(Collectors.toList());
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase().replace(" ", "");
    }
}