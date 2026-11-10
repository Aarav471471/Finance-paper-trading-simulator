package com.papertrader.model;

import com.papertrader.util.Formatter;

/**
 * A take-profit order locks in a gain automatically.
 * SELL: sell when the price RISES to the target or above (bank the profit).
 * BUY:  buy when the price FALLS to the target or below (buy the dip).
 * It is the mirror image of StopLossOrder: same structure, opposite condition.
 */
public class TakeProfitOrder extends Order {

    private final double targetPrice;

    public TakeProfitOrder(Asset asset, OrderSide side, int quantity, double targetPrice) {
        super(asset, side, quantity);
        if (targetPrice <= 0) {
            throw new IllegalArgumentException("Target price must be positive");
        }
        this.targetPrice = targetPrice;
    }

    public double getTargetPrice() {
        return targetPrice;
    }

    @Override
    public boolean canExecute(double marketPrice) {
        if (getSide() == OrderSide.SELL) {
            return marketPrice >= targetPrice;
        }
        return marketPrice <= targetPrice;
    }

    @Override
    public String getOrderType() {
        return "Take-Profit";
    }

    @Override
    public String getConditionText() {
        String sign = getSide() == OrderSide.SELL ? ">=" : "<=";
        return "takes profit when price " + sign + " " + Formatter.money(targetPrice);
    }
}
