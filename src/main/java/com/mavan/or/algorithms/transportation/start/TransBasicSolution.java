package com.mavan.or.algorithms.transportation.start;

import com.mavan.or.algorithms.transportation.datamodels.TransTable;
import org.springframework.stereotype.Service;

@Service
public class TransBasicSolution {

    private final NorthwestCorner northwestCorner;
    private final LeastCost leastCost;
    private final Vogel vogel;

    public TransBasicSolution(NorthwestCorner northwestCorner, LeastCost leastCost, Vogel vogel) {
        this.northwestCorner = northwestCorner;
        this.leastCost = leastCost;
        this.vogel = vogel;
    }

    public void buildInitialBasicSolution(ETransBasicSolution method, TransTable table) {
        buildInitialBasicSolution(method, table, null);
    }

    public void buildInitialBasicSolution(ETransBasicSolution method, TransTable table, InitialStepObserver observer) {
        switch (method) {
            case NORTHWEST_CORNER -> northwestCorner.buildNorthwestCornerSolution(table, observer);
            case LEAST_COST -> leastCost.buildLeastCostSolution(table, observer);
            case VOGEL_APPROXIMATION -> vogel.buildVogelSolution(table, observer);
        }
    }
}
