package net.numericly.superprinter.modules;

import java.util.ArrayList;
import java.util.List;

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.numericly.superprinter.SuperPrinter;
import net.numericly.superprinter.utils.InventoryManager;
import net.numericly.superprinter.utils.ShulkerUtils;
import net.numericly.superprinter.utils.Utils;
import net.numericly.superprinter.utils.tasks.Task;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.numericly.superprinter.utils.tasks.Tasks;
import net.numericly.superprinter.utils.tasks.PlaceTask;

public class ModulePrinter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRendering = settings.createGroup("Rendering");

    private final Setting<Double> bpt = sgGeneral.add(new DoubleSetting.Builder()
        .name("blocks/tick")
        .description("How many blocks place per tick. (1.5 is paper max)")
        .defaultValue(1.5)
        .min(0)
        .sliderRange(0.1, 2)
        .build()
    );

    public final Setting<Integer> inventoryWaitTime = sgGeneral.add(new IntSetting.Builder()
        .name("inventory-wait-time")
        .description("Time to wait for inventory updates in ms.")
        .defaultValue(300)
        .min(0)
        .sliderRange(0, 1000)
        .onModuleActivated(setting -> InventoryManager.inventoryWaitTime = setting.get())
        .onChanged(value -> InventoryManager.inventoryWaitTime = value)
        .build()
    );

    public final Setting<Boolean> autoDupe = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-dupes")
        .description("Automatically dupes stack if at less than half.")
        .defaultValue(true)
        .onModuleActivated(setting -> InventoryManager.autoDupe = setting.get())
        .onChanged(value -> InventoryManager.autoDupe = value)
        .build()
    );

    public final Setting<String> dupeCommand = sgGeneral.add(new StringSetting.Builder()
        .name("dupe-command")
        .description("The dupe command to double the lowest stack of your held item.")
        .defaultValue("dupe")
        .visible(autoDupe::get)
        .build()
    );

    public final Setting<Integer> dupeWaitTime = sgGeneral.add(new IntSetting.Builder()
        .name("dupe-wait-time")
        .description("Time to wait for auto dupe to complete in ms.")
        .defaultValue(200)
        .min(0)
        .sliderRange(0, 1000)
        .visible(autoDupe::get)
        .build()
    );

    public final Setting<Boolean> autoCloseSignGui = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-close-sign-gui")
        .description("Automatically closes the sign edit GUI after the printer places a sign.")
        .defaultValue(true)
        .build()
    );

    public static long lastSignPlaceTime = 0;

    private static final long SIGN_CLOSE_WINDOW_MS = 1000;

    private final Setting<Boolean> renderBlocks = sgRendering.add(new BoolSetting.Builder()
        .name("render-placed-blocks")
        .description("Renders block placements.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRendering.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRendering.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The side color.")
        .defaultValue(new SettingColor(255, 255, 255, 50))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRendering.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The line color.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build()
    );

    public final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance at which the printer tries to place blocks.")
        .defaultValue(5)
        .min(1)
        .max(20)
        .sliderRange(2, 12)
        .onModuleActivated(setting -> Utils.placeRange = setting.get())
        .onChanged(value -> Utils.placeRange = value)
        .build()
    );

    public final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Allow placing blocks without a supporting block behind them (carpets, maparts).")
        .defaultValue(false)
        .onModuleActivated(setting -> PlaceTask.airPlace = setting.get())
        .onChanged(value -> PlaceTask.airPlace = value)
        .build()
    );

    public final Setting<Boolean> accurateRotations = sgGeneral.add(new BoolSetting.Builder()
        .name("accurate-rotations")
        .description("Matches the exact required block state instead of a relaxed match.")
        .defaultValue(true)
        .onModuleActivated(setting -> PlaceTask.accurateRotations = setting.get())
        .onChanged(value -> PlaceTask.accurateRotations = value)
        .build()
    );

    private final Setting<Integer> fadeTime = sgRendering.add(new IntSetting.Builder()
        .name("fade-time")
        .description("Time for the rendering to fade, in milliseconds.")
        .defaultValue(400)
        .sliderRange(100, 3000)
        .min(0)
        .visible(renderBlocks::get)
        .build()
    );

    private boolean dupeNext = false;
    private long dupedAt = 0;

    private final List<BlockPos> toCheck = new ArrayList<>();

    record CompletedTask(Long time, BlockPos location) {}

    private final List<CompletedTask> completedTasks = new ArrayList<>();

    public ModulePrinter() {
        super(SuperPrinter.CATEGORY, "printer", "Prints litematica schematics.\nFrom: numericly");
    }

    @Override
    public void onActivate() {
        completedTasks.clear();
        Utils.placeRange = range.get();
        ShulkerUtils.reset();
    }

    @Override
    public void onDeactivate() {
        Utils.placeRange = -1;
        ShulkerUtils.reset();
    }

    double extraBlock = 0.0;

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        if (dupeNext) {
            dupedAt = System.currentTimeMillis();
            mc.getConnection().sendCommand(dupeCommand.get());
            dupeNext = false;
        }

        completedTasks.removeIf(s -> System.currentTimeMillis() - s.time() > fadeTime.get());

        WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();

        if (worldSchematic == null) {
            completedTasks.clear();
            toggle();
            return;
        }

        BlockIterator.register((int) Math.ceil(range.get()) + 1, 7, (pos, blockState) -> {
            BlockState required = worldSchematic.getBlockState(pos);

            if (Utils.isWithinBlockInteractionRange(pos) &&
                !required.isAir() &&
                DataManager.getRenderLayerRange().isPositionWithinRange(pos) &&
                blockState != required
            ) {
                toCheck.add(new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
            }
        });

        BlockIterator.after(() -> {
            toCheck.sort(Utils.NEAREST);

            int blocksAllowed = (int) Math.floor(bpt.get());

            if (extraBlock >= 1) {
                extraBlock = 0;
                blocksAllowed++;
            }

            long sinceDuped = System.currentTimeMillis() - dupedAt;

            if (autoDupe.get() && mc.player.gameMode() != GameType.CREATIVE && sinceDuped > dupeWaitTime.get()) {
                ItemStack stack = mc.player.getMainHandItem();
                int halfStackSize = stack.getItem().getDefaultMaxStackSize() / 2;

                if (stack.getCount() <= halfStackSize) {
                    dupeNext = true;

                    return;
                }
            }

            int executed = 0;

            Profiler.get().push("sp-placement");

            for (BlockPos pos : toCheck) {
                if (executed >= blocksAllowed) {
                    break;
                }

                BlockState required = worldSchematic.getBlockState(pos);
                BlockState current = mc.level.getBlockState(pos);

                Task task = Tasks.getOrCreateTask(required, current, new BlockPos(pos.getX(), pos.getY(), pos.getZ()));

                if (task != null && task.execute()) {
                    completedTasks.add(new CompletedTask(System.currentTimeMillis(), task.getLocation()));
                    executed++;
                }
            }

            Profiler.get().pop();

            toCheck.clear();

            extraBlock += bpt.get() % 1;
        });
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!autoCloseSignGui.get()) return;
        if (!(event.screen instanceof AbstractSignEditScreen)) return;
        if (System.currentTimeMillis() - lastSignPlaceTime > SIGN_CLOSE_WINDOW_MS) return;

        event.setCancelled(true);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (renderBlocks.get()) {
            completedTasks.forEach(s -> {
                long elapsed = System.currentTimeMillis() - s.time();
                double fadeAmount = Math.max(0, 1 - (double) elapsed / (double) fadeTime.get());

                event.renderer.box(s.location(), fade(sideColor.get(), fadeAmount), fade(lineColor.get(), fadeAmount), shapeMode.get(), 0);
            });
        }
    }

    private Color fade(Color color, double amount) {
        Color newColor = color.copy();

        newColor.a((int) ((double) color.a * amount));

        return newColor;
    }
}