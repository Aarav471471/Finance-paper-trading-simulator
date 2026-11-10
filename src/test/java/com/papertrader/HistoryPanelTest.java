package com.papertrader;

import com.papertrader.fees.PercentageFeeCalculator;
import com.papertrader.model.Asset;
import com.papertrader.model.AssetCatalog;
import com.papertrader.model.OrderSide;
import com.papertrader.model.Stock;
import com.papertrader.model.User;
import com.papertrader.repository.InMemoryTransactionRepository;
import com.papertrader.repository.InMemoryUserRepository;
import com.papertrader.service.AuthService;
import com.papertrader.service.MarketService;
import com.papertrader.service.PortfolioService;
import com.papertrader.service.TradingService;
import com.papertrader.ui.HistoryPanel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HistoryPanelTest {

    private AuthService authService;
    private TradingService tradingService;
    private MarketService marketService;
    private PortfolioService portfolioService;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryUserRepository userRepo = new InMemoryUserRepository();
        InMemoryTransactionRepository txRepo = new InMemoryTransactionRepository();

        authService = new AuthService(userRepo, 100_000);
        authService.register("trader1", "password123");
        authService.login("trader1", "password123");
        user = authService.getCurrentUser();

        tradingService = new TradingService(new PercentageFeeCalculator(0.001), userRepo, txRepo);
        marketService = new MarketService(AssetCatalog.defaultAssets());
        portfolioService = new PortfolioService(marketService);

        // Seed transactions
        Asset asset1 = marketService.findBySymbol("RELIANCE").orElse(new Stock("RELIANCE", "Reliance Industries", 2500, "Energy"));
        Asset asset2 = marketService.findBySymbol("AAPL").orElse(new Stock("AAPL", "Apple Inc", 180, "Tech"));

        tradingService.placeOrder(user, asset1, OrderSide.BUY, 10);
        tradingService.placeOrder(user, asset2, OrderSide.BUY, 5);
        tradingService.placeOrder(user, asset1, OrderSide.SELL, 5);
    }

    @Test
    void testHistoryPanelInstantiationAndRefresh() {
        AtomicReference<String> navigatedTo = new AtomicReference<>();
        assertDoesNotThrow(() -> {
            HistoryPanel panel = new HistoryPanel(authService, tradingService, marketService, portfolioService, navigatedTo::set);
            assertNotNull(panel);
            panel.refresh();
        });
    }
}
