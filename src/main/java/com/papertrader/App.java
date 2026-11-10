package com.papertrader;

import com.formdev.flatlaf.FlatDarkLaf;
import com.papertrader.bot.BotService;
import com.papertrader.execution.ExecutionModel;
import com.papertrader.execution.RealisticExecutionModel;
import com.papertrader.feed.SimulatedHistoryProvider;
import com.papertrader.interfaces.HistoryProvider;
import com.papertrader.repository.FileEquityRepository;
import com.papertrader.service.AnalyticsService;
import com.papertrader.service.AppContext;
import com.papertrader.service.ChartService;
import com.papertrader.service.LeaderboardService;
import com.papertrader.exception.SimulatorException;
import com.papertrader.feed.BinanceQuoteProvider;
import com.papertrader.feed.FinnhubQuoteProvider;
import com.papertrader.feed.LivePriceFeed;
import com.papertrader.feed.SimulatedPriceFeed;
import com.papertrader.feed.YahooQuoteProvider;
import com.papertrader.fees.PercentageFeeCalculator;
import com.papertrader.interfaces.FeeCalculator;
import com.papertrader.interfaces.PriceFeed;
import com.papertrader.interfaces.QuoteProvider;
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
import com.papertrader.util.AppConfig;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point. This is the "composition root": the ONE place where concrete classes
 * are created and plugged together. Every other class only knows interfaces.
 *
 * Want flat fees instead of percentage fees? Change ONE line here:
 *     FeeCalculator fees = new FlatFeeCalculator(5.0);
 * Same idea for prices: LivePriceFeed or SimulatedPriceFeed, both are a PriceFeed.
 * Nothing else in the project changes (Open/Closed + Dependency Inversion).
 */
public class App {

    public static final double STARTING_CASH = 100_000.0;

    public static void main(String[] args) {
        List<Asset> assets = AssetCatalog.defaultAssets();
        PriceFeed priceFeed = createPriceFeed(AppConfig.load(), assets);   // may take a few seconds (internet)
        SwingUtilities.invokeLater(() -> start(assets, priceFeed));      // Swing must be used from its own UI thread
    }

    /**
     * Live prices by default: Binance for crypto, Finnhub (with a free key) or Yahoo Finance
     * (no key) for stocks and ETFs. Falls back to simulated prices when there is no internet.
     */
    private static PriceFeed createPriceFeed(AppConfig config, List<Asset> assets) {
        if (!config.useLiveData()) {
            return new SimulatedPriceFeed(assets, 1000);
        }
        List<QuoteProvider> providers = new ArrayList<>();
        providers.add(new BinanceQuoteProvider());
        String finnhubKey = config.finnhubApiKey();
        providers.add(finnhubKey.isEmpty() ? new YahooQuoteProvider() : new FinnhubQuoteProvider(finnhubKey));

        System.out.println("Downloading live prices...");
        LivePriceFeed live = new LivePriceFeed(assets, providers);
        if (!live.loadInitialPrices()) {
            System.out.println("No market data reachable (" + live.getStatusText() + "). Using simulated prices.");
            return new SimulatedPriceFeed(assets, 1000);
        }
        System.out.println(live.getStatusText());
        return live;
    }

    private static void start(List<Asset> assets, PriceFeed priceFeed) {
        FlatDarkLaf.setup();
        Theme.applyDefaults();

        // 1. Data and storage
        Path dataDir = Paths.get("data");
        Repository<User, String> userRepository = new FileUserRepository(dataDir);
        TransactionRepository transactionRepository = new FileTransactionRepository(dataDir);

        // 2. Strategy objects (the price feed was chosen in createPriceFeed)
        FeeCalculator fees = new PercentageFeeCalculator(0.1);
        ExecutionModel execution = new RealisticExecutionModel();   // bid/ask spread + slippage
        List<HistoryProvider> historyProviders = new ArrayList<>();
        if (priceFeed instanceof LivePriceFeed) {
            historyProviders.add(new BinanceQuoteProvider());   // crypto candles
            historyProviders.add(new YahooQuoteProvider());     // stock / ETF candles
        }
        historyProviders.add(new SimulatedHistoryProvider());  // always works (offline fallback)

        // 3. Services (dependencies are passed in through constructors)
        MarketService marketService = new MarketService(assets);
        AuthService authService = new AuthService(userRepository, STARTING_CASH);
        TradingService tradingService = new TradingService(fees, execution, userRepository, transactionRepository);
        PortfolioService portfolioService = new PortfolioService(marketService);
        ChartService chartService = new ChartService(historyProviders);
        AnalyticsService analyticsService = new AnalyticsService(portfolioService, tradingService,
                new FileEquityRepository(dataDir), STARTING_CASH);
        LeaderboardService leaderboardService = new LeaderboardService(userRepository, portfolioService,
                tradingService, STARTING_CASH);
        BotService botService = new BotService(tradingService, chartService, 5000);

        // 4. Observer wiring: these services hear about every price change
        priceFeed.addListener(marketService);
        priceFeed.addListener(tradingService);
        priceFeed.addListener(chartService);

        createDemoAccount(authService, tradingService, marketService);

        // 5. Show the window and start the market
        AppContext context = new AppContext(authService, marketService, tradingService, portfolioService,
                chartService, analyticsService, leaderboardService, botService, fees, priceFeed, STARTING_CASH);
        MainFrame frame = new MainFrame(context);
        frame.setVisible(true);
        botService.start();
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
            trading.placeOrder(demo, market.findBySymbol("ETH").orElseThrow(), OrderSide.BUY, 2);
            trading.placeOrder(demo, market.findBySymbol("SOL").orElseThrow(), OrderSide.BUY, 40);
            trading.placeOrder(demo, market.findBySymbol("SPY").orElseThrow(), OrderSide.BUY, 15);
        } catch (SimulatorException e) {
            System.err.println("Could not create demo account: " + e.getMessage());
        }
    }
}
