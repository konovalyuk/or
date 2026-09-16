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

    public TransTable buildInitialBasicSolution(ETransBasicSolution method, TransTable table) {
        return switch (method) {
            case NORTHWEST_CORNER -> northwestCorner.buildNorthwestCornerSolution(table);
            case LEAST_COST -> leastCost.buildLeastCostSolution(table);
            case VOGEL_APPROXIMATION -> vogel.buildVogelSolution(table);
        };
    }
}
