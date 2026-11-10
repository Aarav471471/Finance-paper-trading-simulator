package com.papertrader.model;

/** A market order executes immediately at whatever the current price is. */
public class MarketOrder extends Order {

    public MarketOrder(Asset asset, OrderSide side, int quantity) {
        super(asset, side, quantity);
    }

    @Override
    public boolean canExecute(double marketPrice) {
        return true;   // always executes right away
    }

    @Override
    public String getOrderType() {
        return "Market";
    }

    @Override
    public String getConditionText() {
        return "at market price";
    }
}
