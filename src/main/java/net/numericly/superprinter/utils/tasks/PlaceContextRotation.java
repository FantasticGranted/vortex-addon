package net.numericly.superprinter.utils.tasks;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static net.minecraft.core.Direction.*;

public class PlaceContextRotation extends BlockPlaceContext {

    private final boolean sneaking;
    private final float lookYaw;
    private final float lookPitch;

    protected PlaceContextRotation(Level level, float lookYaw, float lookPitch, InteractionHand interactionHand, ItemStack itemStack, BlockHitResult blockHitResult) {
        super(level, null, interactionHand, itemStack, blockHitResult);
        this.lookYaw = lookYaw;
        this.lookPitch = lookPitch;

        assert mc.player != null;

        this.sneaking = mc.player.isCrouching();
    }

    public float getLookYaw() {
        return lookYaw;
    }

    public float getLookPitch() {
        return lookPitch;
    }

    @Override
    public Direction getNearestLookingDirection() {
        return getEntityFacingOrder(lookPitch, lookYaw)[0];
    }

    @Override
    public Direction getNearestLookingVerticalDirection() {
        return getLookDirectionForAxis(lookPitch, lookYaw, Direction.Axis.Y);
    }

    @Override
    public Direction[] getNearestLookingDirections() {
        Direction[] directions = getEntityFacingOrder(lookPitch, lookYaw);
        if (this.replaceClicked) {
            return directions;
        } else {
            Direction direction = this.getClickedFace();
            int i = 0;

            while (i < directions.length && directions[i] != direction.getOpposite()) {
                i++;
            }

            if (i > 0) {
                System.arraycopy(directions, 0, directions, 1, i);
                directions[0] = direction.getOpposite();
            }

            return directions;
        }
    }

    @Override
    public Direction getHorizontalDirection() {
        return Direction.fromYRot(lookYaw);
    }

    @Override
    public boolean isSecondaryUseActive() {
        return sneaking;
    }

    @Override
    public float getRotation() {
        return lookYaw;
    }

    public static Direction getLookDirectionForAxis(float pitch, float yaw, Direction.Axis axis) {
        return switch (axis) {
            case X -> EAST.isFacingAngle(yaw) ? EAST : WEST;
            case Y -> pitch < 0.0F ? UP : DOWN;
            case Z -> SOUTH.isFacingAngle(yaw) ? SOUTH : NORTH;
        };
    }

    public static Direction[] getEntityFacingOrder(float pitch, float yaw) {
        float f = pitch * (float) (Math.PI / 180.0);
        float g = -yaw * (float) (Math.PI / 180.0);
        float h = Mth.sin(f);
        float i = Mth.cos(f);
        float j = Mth.sin(g);
        float k = Mth.cos(g);
        boolean bl = j > 0.0F;
        boolean bl2 = h < 0.0F;
        boolean bl3 = k > 0.0F;
        float l = bl ? j : -j;
        float m = bl2 ? -h : h;
        float n = bl3 ? k : -k;
        float o = l * i;
        float p = n * i;
        Direction direction = bl ? EAST : WEST;
        Direction direction2 = bl2 ? UP : DOWN;
        Direction direction3 = bl3 ? SOUTH : NORTH;
        if (l > n) {
            if (m > o) {
                return listClosest(direction2, direction, direction3);
            } else {
                return p > m ? listClosest(direction, direction3, direction2) : listClosest(direction, direction2, direction3);
            }
        } else if (m > p) {
            return listClosest(direction2, direction3, direction);
        } else {
            return o > m ? listClosest(direction3, direction, direction2) : listClosest(direction3, direction2, direction);
        }
    }

    /**
     * Helper function that returns the 3 directions given, followed by the 3 opposite given in opposite order.
     */
    private static Direction[] listClosest(Direction first, Direction second, Direction third) {
        return new Direction[]{first, second, third, third.getOpposite(), second.getOpposite(), first.getOpposite()};
    }
}