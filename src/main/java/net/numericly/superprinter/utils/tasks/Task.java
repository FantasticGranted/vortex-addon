package net.numericly.superprinter.utils.tasks;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

import java.util.Comparator;

public abstract class Task {
    public static Comparator<Task> NEAREST = Comparator.comparingDouble((task) ->
        Utils.squaredDistance(
            MeteorClient.mc.player.getX(),
            MeteorClient.mc.player.getY(),
            MeteorClient.mc.player.getZ(),
            task.location.getX() + 0.5,
            task.location.getY() + 0.5,
            task.location.getZ() + 0.5));

    BlockPos location;
    BlockState current;
    BlockState required;

    Task(BlockPos location, BlockState current, BlockState required) {
        this.location = location;
        this.current = current;
        this.required = required;
    }

    abstract public boolean execute();

    public BlockPos getLocation() {
        return location;
    }
}