package com.vortex.printer.interfaces;

import com.vortex.printer.utils.Pair;
import net.minecraft.core.BlockPos;

public interface MapPrinter {

    void setInterval(Pair<Integer, Integer> interval);

    void mineLine(int minedLines);

    void addError(BlockPos relativeBlockPos);

    void pause();

    void start();

    boolean isActive();

    void toggle();

    boolean getActivationReset();

    void skipBuilding();

    void slaveFinished(String slave);
}
