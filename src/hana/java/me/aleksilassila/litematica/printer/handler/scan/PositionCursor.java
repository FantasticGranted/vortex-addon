package me.aleksilassila.litematica.printer.handler.scan;

import net.minecraft.core.BlockPos;

interface PositionCursor extends AutoCloseable {
    enum PollResult {
        AVAILABLE,
        COMPLETE
    }

    PollResult poll(BlockPos.MutableBlockPos target);

    long peekDistanceSqr();

    boolean isComplete();

    @Override
    default void close() {
    }
}
