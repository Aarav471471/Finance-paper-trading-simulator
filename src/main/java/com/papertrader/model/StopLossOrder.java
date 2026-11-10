package com.papertrader.model;

import com.papertrader.util.Formatter;

/**
 * A stop-loss order protects you from big moves.
 * SELL stop: sell when the price drops to the stop price or below (limits your loss).
 * BUY  stop: buy when the price rises to the stop price or above (breakout entry).
 */
public class StopLossOrder extends Order {

    private final double stopPrice;

    public StopLossOrder(Asset asset, OrderSide side, int quantity, double stopPrice) {
        super(asset, side, quantity);
        if (stopPrice <= 0) {
            throw new IllegalArgumentException("Stop price must be positive");
        }
        this.stopPrice = stopPrice;
    }

    public double getStopPrice() {
        return stopPrice;
    }

    @Override
    public boolean canExecute(double marketPrice) {
        if (getSide() == OrderSide.SELL) {
            return marketPrice <= stopPrice;
        }
        return marketPrice >= stopPrice;
    }

    @Override
    public String getOrderType() {
        return "Stop-Loss";
    }

    @Override
    public String getConditionText() {
        String sign = getSide() == OrderSide.SELL ? "<=" : ">=";
        return "triggers when price " + sign + " " + Formatter.money(stopPrice);
    }
}
