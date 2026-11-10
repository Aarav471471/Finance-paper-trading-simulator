package com.papertrader.fees;

import com.papertrader.interfaces.FeeCalculator;

/** Charges a percentage of the trade value, e.g. 0.1%. */
public class PercentageFeeCalculator implements FeeCalculator {

    private final double percent;

    public PercentageFeeCalculator(double percent) {
        this.percent = percent;
    }

    @Override
    public double calculateFee(double tradeValue) {
        return Math.round(tradeValue * percent) / 100.0;   // value * percent / 100, rounded to cents
    }

    @Override
    public String getDescription() {
        return percent + "% of trade value";
    }
}
