package com.papertrader.execution;

import com.papertrader.model.Asset;
import com.papertrader.model.OrderSide;

/** Idealised market: every order fills exactly at the last price. Used in unit tests. */
public class PerfectExecutionModel implements ExecutionModel {

    @Override
    public double getFillPrice(Asset asset, OrderSide side, int quantity) {
        return asset.getPrice();
    }

    @Override
    public double estimateCostPercent(Asset asset, int quantity) {
        return 0;
    }

    @Override
    public String getDescription() {
        return "Perfect fills (no spread, no slippage)";
    }
}
