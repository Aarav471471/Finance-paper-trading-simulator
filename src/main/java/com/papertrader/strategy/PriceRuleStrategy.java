package com.papertrader.strategy;

import com.papertrader.model.Candle;
import com.papertrader.util.Formatter;

import java.util.List;

/** A custom rule the user types in: "buy when price <= X, sell when price >= Y". */
public class PriceRuleStrategy implements TradingStrategy {

    private final double buyBelow;
    private final double sellAbove;

    public PriceRuleStrategy(double buyBelow, double sellAbove) {
        if (buyBelow <= 0 || sellAbove <= buyBelow) {
            throw new IllegalArgumentException("Sell price must be higher than buy price");
        }
        this.buyBelow = buyBelow;
        this.sellAbove = sellAbove;
    }

    @Override
    public String getName() {
        return "Price Rule (" + Formatter.money(buyBelow) + " / " + Formatter.money(sellAbove) + ")";
    }

    @Override
    public String getDescription() {
        return "IF price <= " + Formatter.money(buyBelow) + " THEN buy.  IF price >= "
                + Formatter.money(sellAbove) + " THEN sell.";
    }

    @Override
    public Signal decide(List<Candle> candles, boolean holding) {
        if (candles.isEmpty()) {
            return Signal.HOLD;
        }
        double price = candles.get(candles.size() - 1).close();
        if (price <= buyBelow && !holding) {
            return Signal.BUY;
        }
        if (price >= sellAbove && holding) {
            return Signal.SELL;
        }
        return Signal.HOLD;
    }
}
