package com.papertrader;

import com.papertrader.fees.FlatFeeCalculator;
import com.papertrader.fees.PercentageFeeCalculator;
import com.papertrader.interfaces.FeeCalculator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Both classes are used through the same FeeCalculator interface (Strategy pattern). */
class FeeCalculatorTest {

    @Test
    void percentageFee() {
        FeeCalculator fees = new PercentageFeeCalculator(0.1);
        assertEquals(1.00, fees.calculateFee(1000), 0.001);
    }

    @Test
    void flatFee() {
        FeeCalculator fees = new FlatFeeCalculator(5);
        assertEquals(5, fees.calculateFee(10), 0.001);
        assertEquals(5, fees.calculateFee(1_000_000), 0.001);
    }
}
