package com.papertrader;

import com.formdev.flatlaf.FlatDarkLaf;
import com.papertrader.exception.SimulatorException;
import com.papertrader.feed.SimulatedPriceFeed;
import com.papertrader.fees.PercentageFeeCalculator;
import com.papertrader.interfaces.FeeCalculator;
import com.papertrader.interfaces.PriceFeed;
import com.papertrader.interfaces.Repository;
import com.papertrader.interfaces.TransactionRepository;
import com.papertrader.model.Asset;
import com.papertrader.model.AssetCatalog;
import com.papertrader.model.OrderSide;
import com.papertrader.model.User;
import com.papertrader.repository.FileTransactionRepository;
import com.papertrader.repository.FileUserRepository;
import com.papertrader.service.AuthService;
import com.papertrader.service.MarketService;
import com.papertrader.service.PortfolioService;
import com.papertrader.service.TradingService;
import com.papertrader.ui.MainFrame;
import com.papertrader.ui.Theme;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Entry point. This is the "composition root": the ONE place where concrete classes
 * are created and plugged together. Every other class only knows interfaces.
 *
 * Want flat fees instead of percentage fees? Change ONE line here:
 *     FeeCalculator fees = new FlatFeeCalculator(5.0);
 * Nothing else in the project changes (Open/Closed + Dependency Inversion).
 */
public class App {

    public static final double STARTING_CASH = 100_000.0;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::start);   // Swing must be used from its own UI thread
    }

    private static void start() {
        FlatDarkLaf.setup();
        Theme.applyDefaults();

        // 1. Data and storage
        List<Asset> assets = AssetCatalog.defaultAssets();
        Path dataDir = Paths.get("data");
        Repository<User, String> userRepository = new FileUserRepository(dataDir);
        TransactionRepository transactionRepository = new FileTransactionRepository(dataDir);

        // 2. Strategy objects
        FeeCalculator fees = new PercentageFeeCalculator(0.1);
        PriceFeed priceFeed = new SimulatedPriceFeed(assets, 1000);

        // 3. Services (dependencies are passed in through constructors)
        MarketService marketService = new MarketService(assets);
        AuthService authService = new AuthService(userRepository, STARTING_CASH);
        TradingService tradingService = new TradingService(fees, userRepository, transactionRepository);
        PortfolioService portfolioService = new PortfolioService(marketService);

        // 4. Observer wiring: these services hear about every price change
        priceFeed.addListener(marketService);
        priceFeed.addListener(tradingService);

        createDemoAccount(authService, tradingService, marketService);

        // 5. Show the window and start the market
        MainFrame frame = new MainFrame(authService, marketService, tradingService, portfolioService, STARTING_CASH);
        frame.setVisible(true);
        priceFeed.start();
    }

    /** Creates "demo / demo" with a few holdings the first time the app runs, so there is something to show. */
    private static void createDemoAccount(AuthService auth, TradingService trading, MarketService market) {
        if (auth.userExists("demo")) {
            return;
        }
        try {
            User demo = auth.register("demo", "demo");
            trading.placeOrder(demo, market.findBySymbol("AAPL").orElseThrow(), OrderSide.BUY, 30);
            trading.placeOrder(demo, market.findBySymbol("MSFT").orElseThrow(), OrderSide.BUY, 10);
            trading.placeOrder(demo, market.findBySymbol("TSLA").orElseThrow(), OrderSide.BUY, 20);
            trading.placeOrder(demo, market.findBySymbol("ETH").orElseThrow(), OrderSide.BUY, 3);
            trading.placeOrder(demo, market.findBySymbol("SOL").orElseThrow(), OrderSide.BUY, 40);
            trading.placeOrder(demo, market.findBySymbol("SPY").orElseThrow(), OrderSide.BUY, 15);
        } catch (SimulatorException e) {
            System.err.println("Could not create demo account: " + e.getMessage());
        }
    }
}
