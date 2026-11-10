package com.papertrader.execution;

import com.papertrader.model.Asset;
import com.papertrader.model.OrderSide;

import java.util.Random;

/**
 * Realistic fills:
 *  1. SPREAD:   a BUY fills at the ask, a SELL at the bid (each half a spread away from the last price).
 *  2. SLIPPAGE: the price also moves a little against you, randomly, between 0 and maxSlippagePercent.
 *  3. MARKET IMPACT: bigger orders move the price more (impactPercentPerMillion per $1M traded).
 * So you always pay a bit more when buying and get a bit less when selling, like in a real market.
 */
public class RealisticExecutionModel implements ExecutionModel {

    private final double maxSlippagePercent;
    private final double impactPercentPerMillion;
    private final Random random;

    public RealisticExecutionModel(double maxSlippagePercent, double impactPercentPerMillion, Random random) {
        this.maxSlippagePercent = maxSlippagePercent;
        this.impactPercentPerMillion = impactPercentPerMillion;
        this.random = random;
    }

    /** Default settings: up to 0.05% random slippage, plus 0.1% per $1M of order size. */
    public RealisticExecutionModel() {
        this(0.05, 0.1, new Random());
    }

    @Override
    public double getFillPrice(Asset asset, OrderSide side, int quantity) {
        double base = side == OrderSide.BUY ? asset.getAsk() : asset.getBid();
        double orderValue = asset.getPrice() * quantity;
        double slippagePercent = random.nextDouble() * maxSlippagePercent
                + orderValue / 1_000_000.0 * impactPercentPerMillion;
        double direction = side == OrderSide.BUY ? 1 : -1;   // always AGAINST the trader
        double price = base * (1 + direction * slippagePercent / 100.0);
        return Math.round(price * 10000) / 10000.0;
    }

    /** Worst-case extra cost in percent, shown in the order preview. */
    @Override
    public double estimateCostPercent(Asset asset, int quantity) {
        return asset.getSpreadPercent() / 2 + maxSlippagePercent
                + asset.getPrice() * quantity / 1_000_000.0 * impactPercentPerMillion;
    }

    @Override
    public String getDescription() {
        return "Bid/ask spread + up to " + maxSlippagePercent + "% slippage";
    }
}
