package net.numericly.superprinter.utils.tasks;

import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.numericly.superprinter.modules.ModulePrinter;
import net.numericly.superprinter.utils.InventoryManager;
import net.numericly.superprinter.utils.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class PlaceTask extends Task {

    public static boolean airPlace = false;
    public static boolean accurateRotations = true;

    PlaceContextRotation context;

    BlockItem item;

    @Nullable
    static PlaceTask tryCreate(BlockPos location, BlockState current, BlockState required) {
        if (!current.canBeReplaced() && current.getBlock() != required.getBlock()) {
            return null;
        }
        assert mc.player != null;

        ItemStack itemStack;

        if (mc.player.gameMode() == GameType.CREATIVE) {
            itemStack = new ItemStack(required.getBlock().asItem(), 1);
        } else {
            FindItemResult result = InvUtils.find(required.getBlock().asItem());

            if (result.found()) {
                itemStack = mc.player.getInventory().getItem(result.slot());
            } else {
                // No stack available yet. Use a placeholder so the task still gets
                // created; execute() will then pull the item from a shulker via
                // InventoryManager.switchItem -> ShulkerUtils.withdraw.
                itemStack = new ItemStack(required.getBlock().asItem(), 1);
            }
        }

        if (!(itemStack.getItem() instanceof BlockItem blockItem)) return null;

        if (!airPlace && !required.canSurvive(mc.level, location)) {
            return null;
        }

        if (required.getBlock() instanceof FallingBlock &&
            FallingBlock.isFree(mc.level.getBlockState(location.below())) &&
            location.getY() >= mc.level.getMinY()) {
            return null;
        }

        InteractionHand hand = InteractionHand.MAIN_HAND;

        PlaceContextRotation context;

        if (accurateRotations) {
            context = findExactContext(location, current, required, itemStack, blockItem, hand);
        } else {
            context = findBruteForceContext(location, current, required, itemStack, blockItem, hand);
        }

        if (context == null) {
            return null;
        }

        return new PlaceTask(location, current, required, context, blockItem);
    }

    @Nullable
    private static PlaceContextRotation findBruteForceContext(BlockPos location, BlockState current, BlockState required, ItemStack itemStack, BlockItem blockItem, InteractionHand hand) {
        for (float yaw : createYaws(required)) {
            for (float pitch : createPitches(blockItem, required)) {
                for (Vec3 offset : createOffsets(required)) {
                    for (Direction direction : Direction.values()) {
                        BlockHitResult blockHitResult = new BlockHitResult(location.getCenter().add(offset), direction, location, false);
                        PlaceContextRotation context = new PlaceContextRotation(mc.level, yaw, pitch, hand, itemStack, blockHitResult);

                        BlockState newState = blockItem.getPlacementState(context);

                        if (newState == null) continue;

                        boolean valid = validateNewState(current, required, newState);

                        if (valid) {
                            return context;
                        }
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    private static PlaceContextRotation findExactContext(BlockPos location, BlockState current, BlockState required, ItemStack itemStack, BlockItem blockItem, InteractionHand hand) {
        PlaceContextRotation partial = null;

        for (float yaw : createAccurateYaws(required)) {
            for (float pitch : createPitches(blockItem, required)) {
                for (Vec3 offset : createOffsets(required)) {
                    for (Direction direction : Direction.values()) {
                        BlockHitResult blockHitResult = new BlockHitResult(location.getCenter().add(offset), direction, location, false);
                        PlaceContextRotation context = new PlaceContextRotation(mc.level, yaw, pitch, hand, itemStack, blockHitResult);

                        BlockState newState = blockItem.getPlacementState(context);

                        if (newState == null) continue;

                        if (newState == required) {
                            return context;
                        }

                        if (partial == null && validateNewState(current, required, newState)) {
                            partial = context;
                        }
                    }
                }
            }
        }

        return partial;
    }

    PlaceTask(BlockPos location, BlockState current, BlockState required, PlaceContextRotation context, BlockItem item) {
        super(location, current, required);
        this.context = context;
        this.item = item;
    }

    public static List<Float> createYaws(BlockState state) {
        if (state.hasProperty(BlockStateProperties.ROTATION_16)) {
            return List.of(0.0F, 22.5F, 45.0F, 67.5F, 90.0F, 112.5F, 135.0F, 157.5F, 180.0F, 202.5F, 225.0F, 247.5F, 270.0F, 292.5F, 315.0F, 337.5F);
        } else if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ||
            state.hasProperty(BlockStateProperties.FACING) ||
            state.hasProperty(BlockStateProperties.ATTACH_FACE) ||
            state.hasProperty(BlockStateProperties.ORIENTATION)) {
            return List.of(0F, 90F, 180F, 270F);
        } else {
            return List.of(0F);
        }
    }

    public static List<Float> createAccurateYaws(BlockState state) {
        List<Float> yaws = new ArrayList<>(createYaws(state));

        for (float yaw = 0.0F; yaw < 360.0F; yaw += 15.0F) {
            if (!yaws.contains(yaw)) {
                yaws.add(yaw);
            }
        }

        return yaws;
    }

    public static List<Float> createPitches(BlockItem item, BlockState state) {
        if (item instanceof StandingAndWallBlockItem) {
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                return List.of(0F);
            } else if (state.hasProperty(BlockStateProperties.ROTATION_16)) {
                return List.of(90F, -90F);
            }
        }

        if (state.hasProperty(BlockStateProperties.VERTICAL_DIRECTION) ||
            state.hasProperty(BlockStateProperties.FACING) ||
            state.hasProperty(BlockStateProperties.SLAB_TYPE) ||
            state.hasProperty(BlockStateProperties.HALF) ||
            state.hasProperty(BlockStateProperties.ORIENTATION) ||
            state.hasProperty(BlockStateProperties.ATTACH_FACE) ||
            state.hasProperty(BlockStateProperties.ROTATION_16) ||
            state.hasProperty(BlockStateProperties.HANGING) ||
            state.getBlock() instanceof MultifaceBlock
        ) {
            return List.of(0F, 90F, -90F);
        } else {
            return List.of(0F);
        }
    }

    public static List<Vec3> createOffsets(BlockState state) {
        if (state.getBlock() instanceof TrapDoorBlock) {
            return List.of(
                Vec3.ZERO,
                new Vec3(0.5, 0, 0.5),
                new Vec3(0.5, 0, -0.5),
                new Vec3(-0.5, 0, 0.5),
                new Vec3(-0.5, 0, -0.5)
            );
        } else if (state.getBlock() instanceof SnowLayerBlock) {
            return List.of(new Vec3(0, 0.5, 0));
        } else {
            return List.of(Vec3.ZERO);
        }
    }

    private static boolean validateNewState(BlockState old, BlockState required, BlockState newState) {
        if (old == newState) {
            return false;
        }

        if (newState.getBlock() != required.getBlock()) {
            return false;
        }

        if (old.getBlock() == newState.getBlock()) {
            if (old.hasProperty(BlockStateProperties.SLAB_TYPE)) {
                SlabType oldType = old.getValue(BlockStateProperties.SLAB_TYPE);
                SlabType placedType = newState.getValue(BlockStateProperties.SLAB_TYPE);
                SlabType reqType = required.getValue(BlockStateProperties.SLAB_TYPE);

                // If the slab type has changed from top or bottom to double
                return oldType != SlabType.DOUBLE && placedType == reqType;
            }

            if (old.hasProperty(BlockStateProperties.LAYERS)) {
                int oldCount = old.getValue(BlockStateProperties.LAYERS);
                int placedCount = newState.getValue(BlockStateProperties.LAYERS);
                int reqCount = required.getValue(BlockStateProperties.LAYERS);

                return reqCount > oldCount && placedCount > oldCount;
            }

            if (old.hasProperty(BlockStateProperties.CANDLES)) {
                int oldCount = old.getValue(BlockStateProperties.CANDLES);
                int placedCount = newState.getValue(BlockStateProperties.CANDLES);
                int reqCount = required.getValue(BlockStateProperties.CANDLES);

                return reqCount > oldCount && placedCount > oldCount;
            }

            if (old.hasProperty(BlockStateProperties.FLOWER_AMOUNT)) {
                int oldCount = old.getValue(BlockStateProperties.FLOWER_AMOUNT);
                int placedCount = newState.getValue(BlockStateProperties.FLOWER_AMOUNT);
                int reqCount = required.getValue(BlockStateProperties.FLOWER_AMOUNT);

                return reqCount > oldCount && placedCount > oldCount;
            }

            if (old.hasProperty(BlockStateProperties.SEGMENT_AMOUNT)) {
                int oldCount = old.getValue(BlockStateProperties.SEGMENT_AMOUNT);
                int placedCount = newState.getValue(BlockStateProperties.SEGMENT_AMOUNT);
                int reqCount = required.getValue(BlockStateProperties.SEGMENT_AMOUNT);

                return reqCount > oldCount && placedCount > oldCount;
            }

            return false;
        }

        if (required.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            SlabType req = required.getValue(BlockStateProperties.SLAB_TYPE);
            SlabType placed = newState.getValue(BlockStateProperties.SLAB_TYPE);

            if (req != SlabType.DOUBLE && req != placed) {
                return false;
            }
        }

        if (required.hasProperty(BlockStateProperties.CHEST_TYPE)) {
            ChestType req = required.getValue(BlockStateProperties.CHEST_TYPE);
            ChestType placed = newState.getValue(BlockStateProperties.CHEST_TYPE);

            if (req != placed && !(req == ChestType.LEFT && placed == ChestType.SINGLE )) {
                return false;
            }
        }

        if (required.getBlock() instanceof MultifaceBlock) {
            if (newState.getValue(BlockStateProperties.UP) && !required.getValue(BlockStateProperties.UP)) return false;
            if (newState.getValue(BlockStateProperties.DOWN) && !required.getValue(BlockStateProperties.DOWN)) return false;
            if (newState.getValue(BlockStateProperties.EAST) && !required.getValue(BlockStateProperties.EAST)) return false;
            if (newState.getValue(BlockStateProperties.NORTH) && !required.getValue(BlockStateProperties.NORTH)) return false;
            if (newState.getValue(BlockStateProperties.SOUTH) && !required.getValue(BlockStateProperties.SOUTH)) return false;
            if (newState.getValue(BlockStateProperties.WEST) && !required.getValue(BlockStateProperties.WEST)) return false;
        }

        if (propertyMismatch(required, newState, BlockStateProperties.HALF)) return false;
        if (propertyMismatch(required, newState, BlockStateProperties.AXIS)) return false;
        if (propertyMismatch(required, newState, BlockStateProperties.FACING)) return false;
        if (propertyMismatch(required, newState, BlockStateProperties.FACING_HOPPER)) return false;
        if (propertyMismatch(required, newState, BlockStateProperties.HORIZONTAL_FACING)) return false;
        if (propertyMismatch(required, newState, BlockStateProperties.ATTACH_FACE)) return false;
        if (propertyMismatch(required, newState, BlockStateProperties.BED_PART)) return false;
        if (propertyMismatch(required, newState, BlockStateProperties.DOUBLE_BLOCK_HALF)) return false;
        if (propertyMismatch(required, newState, BlockStateProperties.DOOR_HINGE)) return false;
        if (propertyMismatch(required, newState, BlockStateProperties.BELL_ATTACHMENT)) return false;
        if (propertyMismatch(required, newState, BlockStateProperties.ATTACHED)) return false;
        if (propertyMismatch(required, newState, BlockStateProperties.HANGING)) return false;
        if (propertyMismatch(required, newState, BlockStateProperties.ORIENTATION)) return false;
        if (propertyMismatch(required, newState, BlockStateProperties.VERTICAL_DIRECTION)) return false;
        if (propertyMismatch(required, newState, BlockStateProperties.ROTATION_16)) return false;

        return true;
    }

    private static <T extends Comparable<T>> boolean propertyMismatch(BlockState bs1, BlockState bs2, Property<@NotNull T> property) {
        if (bs1.hasProperty(property) || bs2.hasProperty(property)) {
            if (bs1.hasProperty(property) != bs2.hasProperty(property)) {
                return true;
            }
            return bs1.getValue(property) != bs2.getValue(property);
        } else {
            return false;
        }
    }


    @Override
    public boolean execute() {
        assert mc.player != null;
        assert mc.level != null;

        if (mc.level.getBlockState(location) != current) return false;

        if (!InventoryManager.switchItem(item)) {
            return false;
        }

        WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();

        if (worldSchematic == null) {
            return false;
        }

        BlockState state = worldSchematic.getBlockState(location);

        if (!mc.level.isUnobstructed(state, location, CollisionContext.empty())) {
            return false;
        }

        if (context instanceof PlaceContextRotation contextRotation) {
            Utils.rotate(contextRotation.getLookYaw(), contextRotation.getLookPitch());
        }

        Utils.setSneaking(context.isSecondaryUseActive());

        if (required.getBlock() instanceof SignBlock) {
            ModulePrinter.lastSignPlaceTime = System.currentTimeMillis();
        }

        Utils.interactBlock(context.getHitResult(), InteractionHand.MAIN_HAND, context.getLookPitch(), context.getLookYaw(), context.isSecondaryUseActive());

        return true;
    }

}