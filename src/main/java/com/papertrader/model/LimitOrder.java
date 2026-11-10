package com.papertrader.model;

import com.papertrader.util.Formatter;

/**
 * A limit order only executes at the limit price or better.
 * BUY  limit: execute when price falls to the limit or below.
 * SELL limit: execute when price rises to the limit or above.
 */
public class LimitOrder extends Order {

    private final double limitPrice;

    public LimitOrder(Asset asset, OrderSide side, int quantity, double limitPrice) {
        super(asset, side, quantity);
        if (limitPrice <= 0) {
            throw new IllegalArgumentException("Limit price must be positive");
        }
        this.limitPrice = limitPrice;
    }

    public double getLimitPrice() {
        return limitPrice;
    }

    @Override
    public boolean canExecute(double marketPrice) {
        if (getSide() == OrderSide.BUY) {
            return marketPrice <= limitPrice;
        }
        return marketPrice >= limitPrice;
    }

    @Override
    public String getOrderType() {
        return "Limit";
    }

    @Override
    public String getConditionText() {
        String sign = getSide() == OrderSide.BUY ? "<=" : ">=";
        return "when price " + sign + " " + Formatter.money(limitPrice);
    }
}
