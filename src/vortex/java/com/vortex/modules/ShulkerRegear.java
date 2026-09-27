package com.vortex.modules;

import com.vortex.Vortex;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ShulkerRegear extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("Items to keep stocked. Regears when any of these is below the threshold.")
        .defaultValue(Items.TOTEM_OF_UNDYING, Items.END_CRYSTAL, Items.FIREWORK_ROCKET, Items.OBSIDIAN, Items.ENDER_PEARL, Items.GOLDEN_APPLE)
        .build()
    );

    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("threshold")
        .description("Regear when you have less than this many (inventory + hotbar + offhand).")
        .defaultValue(8)
        .min(1)
        .sliderMax(64)
        .build()
    );

    private final Setting<Boolean> takeEverything = sgGeneral.add(new BoolSetting.Builder()
        .name("take-everything")
        .description("Pulls the entire contents of the shulker box. Disable to only take the matching items.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Seconds between regear passes.")
        .defaultValue(6)
        .min(0)
        .sliderMax(60)
        .build()
    );

    private final Setting<Integer> actionDelay = sgGeneral.add(new IntSetting.Builder()
        .name("action-delay")
        .description("Ticks between container clicks while pulling items.")
        .defaultValue(1)
        .min(0)
        .sliderMax(5)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates toward the shulker box while placing, opening and breaking it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useTool = sgGeneral.add(new BoolSetting.Builder()
        .name("use-tool")
        .description("Switches to the fastest tool in your hotbar while breaking the shulker box.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> shulkerNames = sgGeneral.add(new StringListSetting.Builder()
        .name("shulker-names")
        .description("Only use shulker boxes whose name contains one of these (case insensitive). Empty uses any shulker.")
        .defaultValue()
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders a box around the shulker box while the regear is running.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the box is rendered.")
        .defaultValue(ShapeMode.Lines)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(150, 220, 255, 50))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(150, 220, 255, 200))
        .build()
    );

    private enum State { IDLE, PREPARE, PLACE, OPEN, TAKE, CLOSE, BREAK }

    private State state = State.IDLE;
    private BlockPos shulkerPos;
    private int timer;
    private int takeSlot;
    private int clickTimer;
    private int breakTicks;
    private int cooldownTimer;
    private int prevSlot = -1;
    private boolean toolSwapped;

    public ShulkerRegear() {
        super(Vortex.CATEGORY, "shulker-regear", "Automatically places a shulker, pulls items out of it and breaks it when you're low on resources.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        closeContainer();
        reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        if (!mc.player.isAlive()) {
            if (state != State.IDLE) {
                closeContainer();
                reset();
            }
            return;
        }

        if (state == State.IDLE) {
            if (cooldownTimer > 0) {
                cooldownTimer--;
                return;
            }
            tryTrigger();
        } else {
            runState();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || state == State.IDLE || shulkerPos == null) return;
        event.renderer.box(shulkerPos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    private void tryTrigger() {
        if (mc.screen != null) return;
        if (mc.player.containerMenu.containerId != 0) return;

        for (Item item : items.get()) {
            if (countItem(item) >= threshold.get()) continue;

            int shulkerSlot = findShulkerWith(item);
            if (shulkerSlot == -1) continue;

            startFlow(shulkerSlot);
            return;
        }
    }

    private void startFlow(int shulkerSlot) {
        BlockPos pos = findPlacePos();
        if (pos == null) {
            info("Couldn't find a spot to place the shulker box.");
            cooldownTimer = 60;
            return;
        }

        shulkerPos = pos;
        prevSlot = mc.player.getInventory().getSelectedSlot();
        toolSwapped = false;

        if (shulkerSlot >= 9) {
            InvUtils.move().from(shulkerSlot).toHotbar(8);
            InvUtils.swap(8, false);
        } else {
            InvUtils.swap(shulkerSlot, false);
        }

        state = State.PREPARE;
        timer = 4;
    }

    private void runState() {
        switch (state) {
            case PREPARE -> {
                if (--timer <= 0) {
                    doPlace();
                    state = State.PLACE;
                    timer = 30;
                }
            }

            case PLACE -> {
                if (isShulkerPlaced()) {
                    state = State.OPEN;
                    timer = 40;
                    clickTimer = 0;
                    return;
                }

                if (--timer <= 0) {
                    info("Failed to place the shulker box.");
                    reset();
                    cooldownTimer = 40;
                }
            }

            case OPEN -> {
                if (mc.player.containerMenu instanceof ShulkerBoxMenu) {
                    state = State.TAKE;
                    timer = 200;
                    takeSlot = 0;
                    clickTimer = 0;
                    return;
                }

                if (--timer <= 0 || !isShulkerPlaced()) {
                    cleanupBreak();
                    return;
                }

                if (--clickTimer <= 0) {
                    clickTimer = 4;
                    doOpen();
                }
            }

            case TAKE -> {
                if (!(mc.player.containerMenu instanceof ShulkerBoxMenu)) {
                    cleanupBreak();
                    return;
                }

                if (--timer <= 0 || takeSlot >= 27) {
                    state = State.CLOSE;
                    timer = 20;
                    return;
                }

                if (--clickTimer <= 0) {
                    clickTimer = actionDelay.get();
                    ItemStack stack = mc.player.containerMenu.getSlot(takeSlot).getItem();
                    if (!stack.isEmpty() && (takeEverything.get() || matchesTarget(stack))) {
                        InvUtils.shiftClick().slotId(takeSlot);
                    }
                    takeSlot++;
                }
            }

            case CLOSE -> {
                closeContainer();
                if (mc.player.containerMenu.containerId == 0 || --timer <= 0) {
                    state = State.BREAK;
                    breakTicks = 0;
                }
            }

            case BREAK -> {
                if (!isShulkerPlaced()) {
                    done();
                    return;
                }

                if (breakTicks > 120) {
                    info("Gave up breaking the shulker box.");
                    done();
                    return;
                }
                breakTicks++;

                if (useTool.get() && !toolSwapped) {
                    toolSwapped = true;
                    int slot = InvUtils.findFastestTool(mc.level.getBlockState(shulkerPos)).slot();
                    if (slot != -1 && slot < 9) InvUtils.swap(slot, false);
                }

                BlockUtils.breakBlock(shulkerPos, true);
            }
        }
    }

    // Placing

    private void doPlace() {
        Vec3 center = Vec3.atCenterOf(shulkerPos);
        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(center), Rotations.getPitch(center), 50, this::placeNow);
        } else {
            placeNow();
        }
    }

    private void placeNow() {
        if (isShulkerPlaced()) return;

        BlockPos surface = shulkerPos.below();
        BlockHitResult result = new BlockHitResult(Vec3.atCenterOf(surface).add(0, 0.5, 0), Direction.UP, surface, false);
        InteractionResult r = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, result);
        if (r.consumesAction()) mc.player.swing(InteractionHand.MAIN_HAND);
    }

    // Opening

    private void doOpen() {
        Vec3 center = Vec3.atCenterOf(shulkerPos);
        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(center), Rotations.getPitch(center), 50, this::openNow);
        } else {
            openNow();
        }
    }

    private void openNow() {
        BlockHitResult result = new BlockHitResult(Vec3.atCenterOf(shulkerPos), Direction.UP, shulkerPos, false);
        InteractionResult r = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, result);
        if (r.consumesAction()) mc.player.swing(InteractionHand.MAIN_HAND);
    }

    // Cleanup

    private void cleanupBreak() {
        closeContainer();
        if (isShulkerPlaced()) {
            state = State.BREAK;
            breakTicks = 0;
            toolSwapped = false;
        } else {
            done();
        }
    }

    private void done() {
        if (prevSlot != -1) {
            InvUtils.swap(prevSlot, false);
            prevSlot = -1;
        }
        reset();
        cooldownTimer = Math.max(20, cooldown.get() * 20);
    }

    private void reset() {
        state = State.IDLE;
        shulkerPos = null;
        timer = 0;
        takeSlot = 0;
        clickTimer = 0;
        breakTicks = 0;
    }

    private void closeContainer() {
        if (mc.player == null) return;
        if (mc.player.containerMenu.containerId != 0) mc.player.closeContainer();
    }

    // Searching

    private boolean isShulkerPlaced() {
        return shulkerPos != null && mc.level.getBlockState(shulkerPos).getBlock() instanceof ShulkerBoxBlock;
    }

    private int countItem(Item item) {
        int count = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(item)) count += stack.getCount();
        }

        if (mc.player.getOffhandItem().is(item)) count += mc.player.getOffhandItem().getCount();
        return count;
    }

    private int findShulkerWith(Item item) {
        List<String> names = shulkerNames.get().stream().map(n -> n.toLowerCase(Locale.ROOT)).toList();

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!isShulker(stack)) continue;

            if (!names.isEmpty()) {
                String hover = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                boolean matches = names.stream().anyMatch(hover::contains);
                if (!matches) continue;
            }

            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
            if (contents == null) continue;

            boolean found = contents.nonEmptyItemCopyStream().anyMatch(inner -> inner.is(item));
            if (found) return i;
        }

        return -1;
    }

    private boolean matchesTarget(ItemStack stack) {
        for (Item item : items.get()) {
            if (stack.is(item)) return true;
        }
        return false;
    }

    private boolean isShulker(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock() instanceof ShulkerBoxBlock;
        }
        return false;
    }

    private BlockPos findPlacePos() {
        BlockPos playerPos = mc.player.blockPosition();
        Direction dir = mc.player.getDirection();

        List<BlockPos> candidates = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            BlockPos neighbor = playerPos.relative(dir);
            candidates.add(neighbor);
            candidates.add(neighbor.offset(0, -1, 0));
            dir = dir.getClockWise();
        }

        for (BlockPos candidate : candidates) {
            if (candidate.equals(playerPos)) continue;
            if (isValidPlacement(candidate)) return candidate;
        }

        return null;
    }

    private boolean isValidPlacement(BlockPos pos) {
        if (!mc.level.getBlockState(pos).canBeReplaced()) return false;

        BlockPos below = pos.below();
        if (mc.level.getBlockState(below).getCollisionShape(mc.level, below).isEmpty()) return false;

        if (!BlockUtils.canPlace(pos)) return false;

        return mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= 16.0;
    }
}