package com.vortex.printer.mixins;

import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static meteordevelopment.meteorclient.utils.world.BlockUtils.isClickable;

@Mixin(value = BlockUtils.class, remap = false)
public class BlockUtilsMixin {

    @Inject(method = "isClickable", at = @At("HEAD"), cancellable = true)
    private static void injectedIsClickable(Block block, CallbackInfoReturnable<Boolean> cir) {
        if (block instanceof CartographyTableBlock) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getPlaceSide", at = @At("HEAD"), cancellable = true)
    private static void injectedGetPlaceSide(BlockPos blockPos, CallbackInfoReturnable<Direction> cir) {
        ArrayList<Direction> placeableDirections = new ArrayList<>(6);
        for (Direction side : Direction.values()) {
            BlockPos neighbor = blockPos.relative(side);
            BlockState state = mc.level.getBlockState(neighbor);
            if (state.isAir() || isClickable(state.getBlock())) continue;
            if (!state.getFluidState().isEmpty()) continue;
            placeableDirections.add(side);
        }

        if (placeableDirections.size() == 1) {
            cir.setReturnValue(placeableDirections.get(0));
            return;
        }

        Vec3 lookVec = blockPos.getCenter().subtract(mc.player.getEyePosition());
        Direction bestDirection = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        List<DirectionScore> directionScores = List.of(
            new DirectionScore(Direction.WEST, -lookVec.x),
            new DirectionScore(Direction.EAST, lookVec.x),
            new DirectionScore(Direction.DOWN, -lookVec.y),
            new DirectionScore(Direction.UP, lookVec.y),
            new DirectionScore(Direction.NORTH, -lookVec.z),
            new DirectionScore(Direction.SOUTH, lookVec.z)
        );

        for (DirectionScore ds : directionScores) {
            if (placeableDirections.contains(ds.direction) && ds.score > bestScore) {
                bestScore = ds.score;
                bestDirection = ds.direction;
            }
        }

        cir.setReturnValue(bestDirection);
    }

    private record DirectionScore(Direction direction, double score) {}
}
