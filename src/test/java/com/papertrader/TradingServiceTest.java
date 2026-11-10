package com.papertrader;

import com.papertrader.exception.InsufficientFundsException;
import com.papertrader.exception.InvalidOrderException;
import com.papertrader.fees.FlatFeeCalculator;
import com.papertrader.model.Asset;
import com.papertrader.model.Order;
import com.papertrader.model.OrderSide;
import com.papertrader.model.OrderStatus;
import com.papertrader.model.Stock;
import com.papertrader.model.StopLossOrder;
import com.papertrader.model.User;
import com.papertrader.repository.InMemoryTransactionRepository;
import com.papertrader.repository.InMemoryUserRepository;
import com.papertrader.service.TradingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the trading engine with in-memory repositories instead of files.
 * This is possible because TradingService depends on interfaces (Dependency Inversion).
 */
class TradingServiceTest {

    private Asset stock;
    private User user;
    private TradingService trading;

    @BeforeEach
    void setUp() {
        stock = new Stock("TEST", "Test Corp", 100, "Tech");
        user = new User("tester", "hash", 10_000);
        trading = new TradingService(new FlatFeeCalculator(10), new InMemoryUserRepository(),
                new InMemoryTransactionRepository());
    }

    @Test
    void marketBuyTakesCashAndAddsUnits() throws Exception {
        Order order = trading.placeOrder(user, stock, OrderSide.BUY, 5);
        assertEquals(OrderStatus.FILLED, order.getStatus());
        assertEquals(5, user.getPortfolio().getQuantity("TEST"));
        assertEquals(10_000 - 500 - 10, user.getAccount().getBalance(), 0.001);
        assertEquals(1, trading.getTransactionHistory(user).size());
    }

    @Test
    void marketSellGivesCashBack() throws Exception {
        trading.placeOrder(user, stock, OrderSide.BUY, 5);
        stock.setPrice(120);
        trading.placeOrder(user, stock, OrderSide.SELL, 5);
        assertEquals(0, user.getPortfolio().getQuantity("TEST"));
        assertEquals(10_000 - 510 + 600 - 10, user.getAccount().getBalance(), 0.001);
    }

    @Test
    void cannotBuyWithoutEnoughCash() {
        assertThrows(InsufficientFundsException.class, () -> trading.placeOrder(user, stock, OrderSide.BUY, 1000));
        assertEquals(10_000, user.getAccount().getBalance(), 0.001);
    }

    @Test
    void cannotSellWhatYouDoNotOwn() {
        assertThrows(InvalidOrderException.class, () -> trading.placeOrder(user, stock, OrderSide.SELL, 1));
    }

    @Test
    void limitOrderWaitsThenFillsWhenPriceDrops() throws Exception {
        Order order = trading.placeOrder(user, stock, OrderSide.BUY, 2, 90.0);   // limit overload
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(1, trading.getPendingOrders(user).size());

        stock.setPrice(95);
        trading.onPriceUpdate(stock, 100, 95);
        assertEquals(OrderStatus.PENDING, order.getStatus());

        stock.setPrice(89);
        trading.onPriceUpdate(stock, 95, 89);
        assertEquals(OrderStatus.FILLED, order.getStatus());
        assertEquals(2, user.getPortfolio().getQuantity("TEST"));
        assertEquals(0, trading.getPendingOrders(user).size());
    }

    @Test
    void stopLossSellsWhenPriceFalls() throws Exception {
        trading.placeOrder(user, stock, OrderSide.BUY, 3);
        Order stop = trading.placeOrder(user, new StopLossOrder(stock, OrderSide.SELL, 3, 80));
        stock.setPrice(79);
        trading.onPriceUpdate(stock, 100, 79);
        assertEquals(OrderStatus.FILLED, stop.getStatus());
        assertEquals(0, user.getPortfolio().getQuantity("TEST"));
    }

    @Test
    void cancelledOrderNeverFills() throws Exception {
        Order order = trading.placeOrder(user, stock, OrderSide.BUY, 1, 50.0);
        trading.cancelOrder(order);
        stock.setPrice(40);
        trading.onPriceUpdate(stock, 100, 40);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals(0, user.getPortfolio().getQuantity("TEST"));
    }
}
