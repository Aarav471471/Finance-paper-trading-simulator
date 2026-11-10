package com.papertrader;

import com.papertrader.model.Asset;
import com.papertrader.model.Crypto;
import com.papertrader.model.ETF;
import com.papertrader.model.LimitOrder;
import com.papertrader.model.MarketOrder;
import com.papertrader.model.Order;
import com.papertrader.model.OrderSide;
import com.papertrader.model.RiskLevel;
import com.papertrader.model.Stock;
import com.papertrader.model.StopLossOrder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests runtime polymorphism: each Order / Asset subclass answers differently. */
class OrderTest {

    private final Asset stock = new Stock("TEST", "Test Corp", 100, "Tech");

    @Test
    void marketOrderAlwaysExecutes() {
        Order order = new MarketOrder(stock, OrderSide.BUY, 1);
        assertTrue(order.canExecute(1));
        assertTrue(order.canExecute(1_000_000));
    }

    @Test
    void buyLimitExecutesOnlyAtOrBelowLimit() {
        Order order = new LimitOrder(stock, OrderSide.BUY, 1, 95);
        assertFalse(order.canExecute(100));
        assertTrue(order.canExecute(95));
        assertTrue(order.canExecute(90));
    }

    @Test
    void sellLimitExecutesOnlyAtOrAboveLimit() {
        Order order = new LimitOrder(stock, OrderSide.SELL, 1, 110);
        assertFalse(order.canExecute(100));
        assertTrue(order.canExecute(110));
    }

    @Test
    void sellStopLossTriggersWhenPriceFalls() {
        Order order = new StopLossOrder(stock, OrderSide.SELL, 1, 90);
        assertFalse(order.canExecute(100));
        assertTrue(order.canExecute(89.99));
    }

    @Test
    void sameCallDifferentBehaviour() {
        // One list of the PARENT type holding different CHILD objects
        List<Order> orders = List.of(
                new MarketOrder(stock, OrderSide.BUY, 1),
                new LimitOrder(stock, OrderSide.BUY, 1, 50),
                new StopLossOrder(stock, OrderSide.BUY, 1, 150));
        int executable = 0;
        for (Order o : orders) {
            if (o.canExecute(100)) {   // which method runs is decided at runtime
                executable++;
            }
        }
        assertEquals(1, executable);
    }

    @Test
    void assetSubclassesReportTheirOwnRisk() {
        assertEquals(RiskLevel.MEDIUM, stock.getRiskLevel());
        assertEquals(RiskLevel.HIGH, new Crypto("C", "Coin", 10, "Chain").getRiskLevel());
        assertEquals(RiskLevel.LOW, new ETF("E", "Fund", 10, "Index").getRiskLevel());
    }

    @Test
    void invalidOrdersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MarketOrder(stock, OrderSide.BUY, 0));
        assertThrows(IllegalArgumentException.class, () -> new LimitOrder(stock, OrderSide.BUY, 1, -5));
    }
}
