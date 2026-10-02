package net.numericly.superprinter.modules;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.numericly.superprinter.SuperPrinter;
import net.numericly.superprinter.utils.ShulkerUtils;
import net.numericly.superprinter.utils.Utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class CarpetMap extends Module {
    private static final int NORMAL_RADIUS = 14;
    private static final int BIG_RADIUS = 48;
    private static final long BIG_SCAN_INTERVAL_MS = 1500;
    private static final long PROBE_INTERVAL_MS = 2000;
    private static final long STALL_MS = 5000;
    private static final long STUCK_MS = 8000;
    private static final long SKIP_EXPIRE_MS = 15000;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> restockThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("restock-threshold")
        .description("Restock the current material from a shulker when its total inventory count drops to this value.")
        .defaultValue(16)
        .min(1)
        .max(64)
        .build()
    );

    private final Setting<Boolean> renderPath = sgGeneral.add(new BoolSetting.Builder()
        .name("render-path")
        .description("Renders the current walk goal.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoEnablePrinter = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-enable-printer")
        .description("Turns the printer module on together with this one.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Prints walker state changes to chat.")
        .defaultValue(false)
        .build()
    );

    private final Map<Long, Long> ignored = new HashMap<>();

    private BlockPos goal;
    private long goalSince;
    private long inRangeSince;
    private long lastBigScan;
    private long lastProbe;
    private int emptyProbes;
    private int stuckTicks;
    private int jumpTicks;
    private int tickCounter;
    private int noSchematicTicks;
    private double lastX;
    private double lastZ;
    private boolean printerWasActive;

    public CarpetMap() {
        super(SuperPrinter.CATEGORY, "carpet-map", "Walks to the next block the printer needs to place and restocks materials from shulkers.\nFrom: numericly");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;

        printerWasActive = Modules.get().isActive(ModulePrinter.class);

        if (autoEnablePrinter.get() && !printerWasActive) {
            Modules.get().get(ModulePrinter.class).toggle();
        }

        ignored.clear();
        goal = null;
        goalSince = 0;
        inRangeSince = 0;
        lastBigScan = 0;
        lastProbe = 0;
        emptyProbes = 0;
        stuckTicks = 0;
        jumpTicks = 0;
        tickCounter = 0;
        noSchematicTicks = 0;
        lastX = mc.player.getX();
        lastZ = mc.player.getZ();
    }

    @Override
    public void onDeactivate() {
        releaseKeys();
        restorePrinter();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        long now = System.currentTimeMillis();

        WorldSchematic schematic = SchematicWorldHandler.getSchematicWorld();

        if (schematic == null) {
            if (noSchematicTicks % 100 == 1) {
                info("Waiting for a litematica schematic...");
            }

            noSchematicTicks++;
            releaseKeys();
            return;
        }

        noSchematicTicks = 0;

        if (mc.player.gameMode() != GameType.CREATIVE) {
            Item restockItem = null;

            if (goal != null) {
                Item item = schematic.getBlockState(goal).getBlock().asItem();

                if (item != Items.AIR) {
                    restockItem = item;
                }
            }

            if (restockItem != null && countInInventory(restockItem) <= restockThreshold.get()) {
                ShulkerUtils.withdraw(restockItem);
            }
        }

        // A GUI is open (chat, inventory, restock shulker, litematica, ...). Movement keys
        // are captured by screens, so while one is open we drive the player with packets
        // directly and keep the client entity in sync, letting the walker, printer and
        // restock keep working. Timers are refreshed so the GUI never causes false skips.
        if (mc.screen != null || ShulkerUtils.withdrawing()) {
            releaseKeys();
            moveDuringGui(schematic, now);
            goalSince = now;
            inRangeSince = 0;
            return;
        }

        cleanupIgnored(now);

        tickCounter++;

        BlockPos near = null;

        if (tickCounter % 2 == 0) {
            near = findTarget(schematic, NORMAL_RADIUS);
        }

        if (goal != null && (isPlaced(schematic, goal) || ignored.containsKey(goal.asLong()))) {
            logDebug("Goal " + goal.toShortString() + " done, finding next.");
            goal = null;
            inRangeSince = 0;
            stuckTicks = 0;
            lastProbe = 0;
        }

        if (near != null && (goal == null || !goal.equals(near))) {
            setGoal(near, now);
        } else if (goal == null) {
            if (now - lastBigScan > BIG_SCAN_INTERVAL_MS) {
                lastBigScan = now;
                BlockPos far = findTarget(schematic, BIG_RADIUS);

                if (far != null) {
                    setGoal(far, now);
                    emptyProbes = 0;
                }
            }

            if (goal == null && now - lastProbe > PROBE_INTERVAL_MS) {
                lastProbe = now;
                BlockPos p = findAnyUnplacedInPlacement(schematic);

                if (p != null) {
                    setGoal(p, now);
                    emptyProbes = 0;
                } else {
                    emptyProbes++;

                    if (emptyProbes == 10) {
                        emptyProbes = 0;
                        info("No unplaced blocks found in any placement; keeping the module ready.");
                    }
                }
            }
        }

        if (goal == null) {
            releaseKeys();
            return;
        }

        if (Utils.isWithinBlockInteractionRange(goal)) {
            if (inRangeSince == 0) {
                inRangeSince = now;
            }

            releaseKeys();

            if (now - inRangeSince > STALL_MS) {
                skipGoal("Cannot place block at " + goal.toShortString() + ", skipping it.");
            }
            return;
        }

        inRangeSince = 0;

        double dx = goal.getX() + 0.5 - mc.player.getX();
        double dz = goal.getZ() + 0.5 - mc.player.getZ();
        double horiz = dx * dx + dz * dz;
        double yGap = Math.abs(goal.getY() + 0.5 - mc.player.getY());

        if (horiz < 9.0 && yGap > 6.0) {
            skipGoal("Block at " + goal.toShortString() + " is out of reach vertically, skipping it.");
            return;
        }

        face(goal.getX() + 0.5, goal.getZ() + 0.5);
        mc.options.keyUp.setDown(true);

        if (jumpTicks > 0) {
            mc.options.keyJump.setDown(true);
            jumpTicks--;
            return;
        }

        mc.options.keyJump.setDown(false);

        double hSpeed = Math.hypot(mc.player.getX() - lastX, mc.player.getZ() - lastZ);
        lastX = mc.player.getX();
        lastZ = mc.player.getZ();

        if (mc.player.onGround()) {
            if (hSpeed < 0.02) {
                stuckTicks++;

                if (stuckTicks > 40) {
                    jumpTicks = 15;
                    stuckTicks = 0;
                }
            } else {
                stuckTicks = 0;
            }
        }

        if (now - goalSince > STUCK_MS) {
            skipGoal("Could not reach block at " + goal.toShortString() + ", skipping it.");
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!renderPath.get() || goal == null || mc.player == null) return;

        SettingColor side = new SettingColor(255, 170, 0, 40);
        SettingColor line = new SettingColor(255, 170, 0, 200);

        event.renderer.box(
            goal.getX(), goal.getY(), goal.getZ(),
            goal.getX() + 1.0, goal.getY() + 1.0, goal.getZ() + 1.0,
            side, line, ShapeMode.Sides, 0
        );

        event.renderer.line(
            mc.player.getEyeY(), mc.player.getY(), mc.player.getZ(),
            goal.getX() + 0.5, goal.getY() + 0.5, goal.getZ() + 0.5,
            line, line
        );
    }

    private void moveDuringGui(WorldSchematic schematic, long now) {
        if (goal != null && (isPlaced(schematic, goal) || ignored.containsKey(goal.asLong()))) {
            goal = null;
            inRangeSince = 0;
            stuckTicks = 0;
        }

        tickCounter++;

        if (tickCounter % 2 == 0) {
            BlockPos near = findTarget(schematic, NORMAL_RADIUS);

            if (near != null && (goal == null || !goal.equals(near))) {
                setGoal(near, now);
            }
        }

        if (goal == null) return;

        if (Utils.isWithinBlockInteractionRange(goal)) return;

        double dx = goal.getX() + 0.5 - mc.player.getX();
        double dz = goal.getZ() + 0.5 - mc.player.getZ();
        double hDist = Math.sqrt(dx * dx + dz * dz);

        if (hDist < 0.01) return;

        double step = 0.21585;
        double nx = mc.player.getX() + dx / hDist * step;
        double nz = mc.player.getZ() + dz / hDist * step;
        double y = mc.player.getY();

        face(goal.getX() + 0.5, goal.getZ() + 0.5);

        mc.player.setPos(nx, y, nz);
        mc.getConnection().getConnection().send(new ServerboundMovePlayerPacket.PosRot(nx, y, nz, mc.player.getYRot(), mc.player.getXRot(), true, false));
    }

    private void setGoal(BlockPos pos, long now) {
        logDebug("Moving to " + pos.toShortString());
        goal = pos;
        goalSince = now;
        inRangeSince = 0;
        stuckTicks = 0;
    }

    private void skipGoal(String message) {
        info(message);
        ignored.put(goal.asLong(), System.currentTimeMillis() + SKIP_EXPIRE_MS);
        goal = null;
        inRangeSince = 0;
        stuckTicks = 0;
        lastProbe = 0;
    }

    private void logDebug(String message) {
        if (debug.get()) {
            info(message);
        }
    }

    private void restorePrinter() {
        if (Modules.get().isActive(ModulePrinter.class) != printerWasActive) {
            Modules.get().get(ModulePrinter.class).toggle();
        }
    }

    private void cleanupIgnored(long now) {
        ignored.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    private BlockPos findTarget(WorldSchematic schematic, int radius) {
        BlockPos center = mc.player.blockPosition();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        long best = 0;
        double bestDist = Double.MAX_VALUE;

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();

        for (int y = center.getY() - 3; y <= center.getY() + 3; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    long key = BlockPos.asLong(center.getX() + x, y, center.getZ() + z);

                    if (ignored.containsKey(key)) continue;

                    mutable.set(center.getX() + x, y, center.getZ() + z);

                    BlockState required = schematic.getBlockState(mutable);

                    if (required.isAir()) continue;
                    if (!DataManager.getRenderLayerRange().isPositionWithinRange(mutable)) continue;
                    if (mc.level.getBlockState(mutable) == required) continue;

                    double dx = center.getX() + x + 0.5 - px;
                    double dy = y + 0.5 - py;
                    double dz = center.getZ() + z + 0.5 - pz;
                    double dist = dx * dx + dy * dy + dz * dz;

                    if (dist < bestDist) {
                        bestDist = dist;
                        best = key;
                    }
                }
            }
        }

        if (bestDist == Double.MAX_VALUE) {
            return null;
        }

        return BlockPos.of(best);
    }

    private BlockPos findAnyUnplacedInPlacement(WorldSchematic schematic) {
        List<SchematicPlacement> placements = DataManager.getSchematicPlacementManager().getAllSchematicsPlacements();

        if (placements == null || placements.isEmpty()) {
            return null;
        }

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        long best = 0;
        double bestDist = Double.MAX_VALUE;

        for (SchematicPlacement placement : placements) {
            Box box = placement.getEclosingBox();

            if (box == null) continue;

            BlockPos p1 = box.getPos1();
            BlockPos p2 = box.getPos2();

            long xSize = (long) p2.getX() - p1.getX() + 1;
            long zSize = (long) p2.getZ() - p1.getZ() + 1;
            long ySize = (long) p2.getY() - p1.getY() + 1;

            int stride = xSize * zSize * ySize > 2_000_000 ? 4 : 1;

            for (int y = p1.getY(); y <= p2.getY(); y += stride) {
                for (int x = p1.getX(); x <= p2.getX(); x += stride) {
                    for (int z = p1.getZ(); z <= p2.getZ(); z += stride) {
                        long key = BlockPos.asLong(x, y, z);

                        if (ignored.containsKey(key)) continue;

                        mutable.set(x, y, z);

                        BlockState required = schematic.getBlockState(mutable);

                        if (required.isAir()) continue;
                        if (mc.level.getBlockState(mutable) == required) continue;

                        double dx = x + 0.5 - px;
                        double dy = y + 0.5 - py;
                        double dz = z + 0.5 - pz;
                        double dist = dx * dx + dy * dy + dz * dz;

                        if (dist < bestDist) {
                            bestDist = dist;
                            best = key;
                        }
                    }
                }
            }
        }

        if (bestDist == Double.MAX_VALUE) {
            return null;
        }

        return BlockPos.of(best);
    }

    private boolean isPlaced(WorldSchematic schematic, BlockPos pos) {
        return mc.level.getBlockState(pos) == schematic.getBlockState(pos);
    }

    private void face(double x, double z) {
        double dx = x - mc.player.getX();
        double dz = z - mc.player.getZ();

        if (dx * dx + dz * dz < 0.01) return;

        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float yawDiff = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
        float newYaw = mc.player.getYRot() + Mth.clamp(yawDiff, -20, 20);

        mc.player.setYRot(newYaw);
        mc.player.yRotO = newYaw;
        mc.player.setYHeadRot(newYaw);
        mc.player.yHeadRotO = newYaw;
    }

    private int countInInventory(Item item) {
        int count = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);

            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }

        return count;
    }

    private void releaseKeys() {
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
    }
}