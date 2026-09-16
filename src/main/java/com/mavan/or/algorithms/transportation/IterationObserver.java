package com.mavan.or.algorithms.transportation;

import com.mavan.or.algorithms.transportation.datamodels.TransState;

/**
 * Optional callback. It can later be replaced by a browser/web layer.
 */
@FunctionalInterface
public interface IterationObserver {
    void onState(TransState state);
}
