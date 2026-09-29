package me.aleksilassila.litematica.printer.handler.scan;

import me.aleksilassila.litematica.printer.printer.PrinterBox;
import net.minecraft.core.BlockPos;

import java.util.List;

final class SynchronousPositionCursor implements PositionCursor {
    private final PlayerDistanceCursor delegate;

    SynchronousPositionCursor(List<PrinterBox> boxes, int centerX, int centerY, int centerZ, int maxDistanceBand) {
        this.delegate = new PlayerDistanceCursor(boxes, centerX, centerY, centerZ, maxDistanceBand);
    }

    @Override
    public PollResult poll(BlockPos.MutableBlockPos target) {
        return this.delegate.next(target) ? PollResult.AVAILABLE : PollResult.COMPLETE;
    }

    @Override
    public long peekDistanceSqr() {
        return this.delegate.peekDistanceSqr();
    }

    @Override
    public boolean isComplete() {
        return this.delegate.isComplete();
    }
}
