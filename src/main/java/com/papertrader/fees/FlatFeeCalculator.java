package com.papertrader.fees;

import com.papertrader.interfaces.FeeCalculator;
import com.papertrader.util.Formatter;

/** Charges the same fixed fee for every trade, no matter how big. */
public class FlatFeeCalculator implements FeeCalculator {

    private final double fee;

    public FlatFeeCalculator(double fee) {
        this.fee = fee;
    }

    @Override
    public double calculateFee(double tradeValue) {
        return fee;
    }

    @Override
    public String getDescription() {
        return Formatter.money(fee) + " per trade";
    }
}
