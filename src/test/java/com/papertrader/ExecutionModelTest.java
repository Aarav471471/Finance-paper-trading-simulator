package com.papertrader;

import com.papertrader.execution.ExecutionModel;
import com.papertrader.execution.OrderBookSimulator;
import com.papertrader.execution.RealisticExecutionModel;
import com.papertrader.fees.FlatFeeCalculator;
import com.papertrader.model.Asset;
import com.papertrader.model.Order;
import com.papertrader.model.OrderSide;
import com.papertrader.model.Stock;
import com.papertrader.model.User;
import com.papertrader.repository.InMemoryTransactionRepository;
import com.papertrader.repository.InMemoryUserRepository;
import com.papertrader.service.TradingService;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spread, slippage and order book behave like a real market. */
class ExecutionModelTest {

    private final Asset stock = new Stock("TEST", "Test Corp", 100, "Tech");

    @Test
    void bidIsBelowAndAskIsAboveLastPrice() {
        assertTrue(stock.getBid() < 100);
        assertTrue(stock.getAsk() > 100);
        assertEquals(stock.getSpreadPercent(), (stock.getAsk() - stock.getBid()) / 100 * 100, 1e-9);
    }

    @Test
    void buyersPayMoreAndSellersGetLess() {
        ExecutionModel model = new RealisticExecutionModel(0.05, 0.1, new Random(1));
        for (int i = 0; i < 50; i++) {
            assertTrue(model.getFillPrice(stock, OrderSide.BUY, 10) >= stock.getAsk());
            assertTrue(model.getFillPrice(stock, OrderSide.SELL, 10) <= stock.getBid());
        }
    }

    @Test
    void biggerOrdersGetMoreSlippage() {
        ExecutionModel noRandom = new RealisticExecutionModel(0, 0.1, new Random(1));
        assertTrue(noRandom.getFillPrice(stock, OrderSide.BUY, 50_000) > noRandom.getFillPrice(stock, OrderSide.BUY, 1));
    }

    @Test
    void limitOrderNeverFillsWorseThanItsLimit() throws Exception {
        TradingService trading = new TradingService(new FlatFeeCalculator(0), new RealisticExecutionModel(5, 0, new Random(2)),
                new InMemoryUserRepository(), new InMemoryTransactionRepository());
        User user = new User("u", "h", 100_000);
        Order order = trading.placeOrder(user, stock, OrderSide.BUY, 1, 100.0);   // limit at the last price
        assertTrue(order.getFilledPrice() <= 100.0);
    }

    @Test
    void orderBookHasBidsBelowAsksAndGrowsWithDepth() {
        OrderBookSimulator.OrderBook book = new OrderBookSimulator().simulate(stock, 10);
        assertEquals(10, book.bids().size());
        assertTrue(book.bids().get(0).price() < book.asks().get(0).price());
        assertTrue(book.bids().get(9).price() < book.bids().get(0).price());
        assertTrue(book.asks().get(9).price() > book.asks().get(0).price());
    }
}
