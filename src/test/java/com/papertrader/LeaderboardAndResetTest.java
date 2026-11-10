package com.papertrader;

import com.papertrader.fees.FlatFeeCalculator;
import com.papertrader.model.Asset;
import com.papertrader.model.OrderSide;
import com.papertrader.model.Stock;
import com.papertrader.model.TakeProfitOrder;
import com.papertrader.model.Order;
import com.papertrader.model.OrderStatus;
import com.papertrader.model.User;
import com.papertrader.repository.InMemoryTransactionRepository;
import com.papertrader.repository.InMemoryUserRepository;
import com.papertrader.service.LeaderboardService;
import com.papertrader.service.MarketService;
import com.papertrader.service.PortfolioService;
import com.papertrader.service.TradingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardAndResetTest {

    private Asset stock;
    private InMemoryUserRepository users;
    private TradingService trading;
    private PortfolioService portfolio;

    @BeforeEach
    void setUp() {
        stock = new Stock("TEST", "Test", 100, "Tech");
        users = new InMemoryUserRepository();
        trading = new TradingService(new FlatFeeCalculator(0), users, new InMemoryTransactionRepository());
        portfolio = new PortfolioService(new MarketService(List.of(stock)));
    }

    @Test
    void realizedProfitIsRecordedOnSell() throws Exception {
        User user = new User("a", "h", 10_000);
        trading.placeOrder(user, stock, OrderSide.BUY, 10);
        stock.setPrice(120);
        trading.placeOrder(user, stock, OrderSide.SELL, 4);
        assertEquals(80, trading.getRealizedProfitLoss(user), 1e-9);   // (120-100) x 4
    }

    @Test
    void takeProfitSellsWhenTargetIsReached() throws Exception {
        User user = new User("a", "h", 10_000);
        trading.placeOrder(user, stock, OrderSide.BUY, 5);
        Order tp = trading.placeOrder(user, new TakeProfitOrder(stock, OrderSide.SELL, 5, 115));
        assertEquals(OrderStatus.PENDING, tp.getStatus());
        stock.setPrice(116);
        trading.onPriceUpdate(stock, 100, 116);
        assertEquals(OrderStatus.FILLED, tp.getStatus());
        assertTrue(trading.getRealizedProfitLoss(user) > 0);
    }

    @Test
    void leaderboardRanksByNetWorth() throws Exception {
        User rich = new User("rich", "h", 10_000);
        User poor = new User("poor", "h", 10_000);
        users.save(rich);
        users.save(poor);
        trading.placeOrder(poor, stock, OrderSide.BUY, 50);
        stock.setPrice(50);   // poor's shares halve in value
        List<LeaderboardService.Entry> ranking = new LeaderboardService(users, portfolio, trading, 10_000).getRanking();
        assertEquals("rich", ranking.get(0).username());
        assertEquals(2, ranking.get(1).rank());
        assertTrue(ranking.get(1).returnPercent() < 0);
    }

    @Test
    void resetGivesFreshAccount() throws Exception {
        User user = new User("a", "h", 10_000);
        trading.placeOrder(user, stock, OrderSide.BUY, 10);
        trading.placeOrder(user, stock, OrderSide.BUY, 1, 50.0);   // waiting limit order
        trading.resetAccount(user, 10_000);
        assertEquals(10_000, user.getAccount().getBalance(), 1e-9);
        assertTrue(user.getPortfolio().isEmpty());
        assertTrue(trading.getTransactionHistory(user).isEmpty());
        assertTrue(trading.getPendingOrders(user).isEmpty());
    }
}
