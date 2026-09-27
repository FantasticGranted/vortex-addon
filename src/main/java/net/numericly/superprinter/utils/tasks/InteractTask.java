package net.numericly.superprinter.utils.tasks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.CopperGolemStatueBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.numericly.superprinter.utils.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class InteractTask extends Task {

    BlockHitResult hitResult;
    @Nullable
    Vec2 rotation;

    @Nullable
    static InteractTask tryCreate(BlockPos location, BlockState current, BlockState required) {
        if (current.getBlock() != required.getBlock()) {
            return null;
        }

        if (current.getBlock() instanceof TrapDoorBlock || current.getBlock() instanceof DoorBlock) {
            if (current.getValue(BlockStateProperties.OPEN) != required.getValue(BlockStateProperties.OPEN)) {
                BlockHitResult result = new BlockHitResult(location.getCenter(), Direction.NORTH, location, false);
                return new InteractTask(location, current, required, result);
            }
        }

        if (current.getBlock() instanceof FenceGateBlock) {
            if (current.getValue(BlockStateProperties.OPEN) != required.getValue(BlockStateProperties.OPEN)) {
                BlockHitResult result = new BlockHitResult(location.getCenter(), Direction.NORTH, location, false);

                float yaw = required.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot();

                return new InteractTask(location, current, required, result, new Vec2(yaw, 0));
            }
        }

        if (current.getBlock() instanceof ComparatorBlock) {
            if (current.getValue(BlockStateProperties.MODE_COMPARATOR) != required.getValue(BlockStateProperties.MODE_COMPARATOR)) {
                BlockHitResult result = new BlockHitResult(location.getCenter(), Direction.NORTH, location, false);
                return new InteractTask(location, current, required, result);
            }
        }

        if (current.getBlock() instanceof RedStoneWireBlock) {
            if ((isFullyConnected(current) && isNotConnected(required)) || (isNotConnected(current) && isFullyConnected(required))) {
                BlockHitResult result = new BlockHitResult(location.getCenter(), Direction.NORTH, location, false);
                return new InteractTask(location, current, required, result);
            }
        }

        if (current.getBlock() instanceof CopperGolemStatueBlock) {
            if (current.getValue(BlockStateProperties.COPPER_GOLEM_POSE) != required.getValue(BlockStateProperties.COPPER_GOLEM_POSE)) {
                BlockHitResult result = new BlockHitResult(location.getCenter(), Direction.NORTH, location, false);
                return new InteractTask(location, current, required, result);
            }
        }

        if (current.getBlock() instanceof RepeaterBlock) {
            if (current.getValue(BlockStateProperties.DELAY).intValue() != required.getValue(BlockStateProperties.DELAY).intValue()) {
                BlockHitResult result = new BlockHitResult(location.getCenter(), Direction.NORTH, location, false);
                return new InteractTask(location, current, required, result);
            }
        }

        if (current.getBlock() instanceof LeverBlock) {
            if (current.getValue(BlockStateProperties.POWERED) != required.getValue(BlockStateProperties.POWERED)) {
                BlockHitResult result = new BlockHitResult(location.getCenter(), Direction.NORTH, location, false);
                return new InteractTask(location, current, required, result);
            }
        }

        if (current.getBlock() instanceof DaylightDetectorBlock) {
            if (current.getValue(BlockStateProperties.INVERTED) != required.getValue(BlockStateProperties.INVERTED)) {
                BlockHitResult result = new BlockHitResult(location.getCenter(), Direction.NORTH, location, false);
                return new InteractTask(location, current, required, result);
            }
        }

        return null;
    }

    InteractTask(BlockPos location, BlockState current, BlockState required, BlockHitResult hitResult) {
        super(location, current, required);
        this.hitResult = hitResult;
    }

    InteractTask(BlockPos location, BlockState current, BlockState required, BlockHitResult hitResult, @NotNull Vec2 rotation) {
        super(location, current, required);
        this.hitResult = hitResult;
        this.rotation = rotation;
    }

    @Override
    public boolean execute() {
        assert mc.gameMode != null;
        assert mc.player != null;
        assert mc.level != null;

        if (mc.level.getBlockState(location) != current) return false;

        if (!Utils.isWithinBlockInteractionRange(location)) return false;

        if (rotation != null) {
            Utils.rotate(rotation.x, rotation.y);
        }

        Utils.setSneaking(false);

        Utils.interactBlock(hitResult, InteractionHand.MAIN_HAND);

        return true;
    }

    private static boolean isFullyConnected(BlockState state) {
        return isConnected(state, Direction.NORTH) &&
            isConnected(state, Direction.EAST) &&
            isConnected(state, Direction.SOUTH) &&
            isConnected(state, Direction.WEST);
    }

    private static boolean isNotConnected(BlockState state) {
        return !isConnected(state, Direction.NORTH) &&
            !isConnected(state, Direction.EAST) &&
            !isConnected(state, Direction.SOUTH) &&
            !isConnected(state, Direction.WEST);
    }

    private static boolean isConnected(BlockState state, Direction direction) {
        RedstoneSide side = state.getValue(RedStoneWireBlock.PROPERTY_BY_DIRECTION.get(direction));
        return side.isConnected();
    }
}