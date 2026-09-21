package com.mavan.or.algorithms.transportation.start;

import com.mavan.or.algorithms.transportation.datamodels.TransTable;

@FunctionalInterface
public interface InitialStepObserver {
    void onStep(int row, int column, double allocatedAmount, TransTable table);
}
