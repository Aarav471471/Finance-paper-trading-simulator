package com.papertrader.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.papertrader.bot.BotService;
import com.papertrader.execution.RealisticExecutionModel;
import com.papertrader.feed.SimulatedHistoryProvider;
import com.papertrader.feed.SimulatedPriceFeed;
import com.papertrader.fees.PercentageFeeCalculator;
import com.papertrader.interfaces.FeeCalculator;
import com.papertrader.interfaces.PriceFeed;
import com.papertrader.model.Asset;
import com.papertrader.model.AssetCatalog;
import com.papertrader.model.OrderSide;
import com.papertrader.model.User;
import com.papertrader.repository.FileEquityRepository;
import com.papertrader.repository.FileTransactionRepository;
import com.papertrader.repository.FileUserRepository;
import com.papertrader.service.AnalyticsService;
import com.papertrader.service.AppContext;
import com.papertrader.service.AuthService;
import com.papertrader.service.ChartService;
import com.papertrader.service.LeaderboardService;
import com.papertrader.service.MarketDataService;
import com.papertrader.service.MarketService;
import com.papertrader.service.PortfolioService;
import com.papertrader.service.TradingService;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class CaptureScreenshots {

    @Test
    public void captureAllPages() throws Exception {
        System.setProperty("java.awt.headless", "false");
        FlatDarkLaf.setup();
        Theme.applyDefaults();

        Path tempDir = Files.createTempDirectory("pt_test_data");
        List<Asset> assets = AssetCatalog.defaultAssets();
        PriceFeed priceFeed = new SimulatedPriceFeed(assets, 1000);

        var userRepo = new FileUserRepository(tempDir);
        var txRepo = new FileTransactionRepository(tempDir);
        FeeCalculator fees = new PercentageFeeCalculator(0.1);
        var execution = new RealisticExecutionModel();
        var chartService = new ChartService(List.of(new SimulatedHistoryProvider()));
        var marketService = new MarketService(assets);
        var authService = new AuthService(userRepo, 100000.0);
        var tradingService = new TradingService(fees, execution, userRepo, txRepo);
        var portfolioService = new PortfolioService(marketService);
        var marketData = new MarketDataService(List.of(), null, null, null, chartService);
        var analytics = new AnalyticsService(portfolioService, tradingService, new FileEquityRepository(tempDir), 100000.0);
        var leaderboard = new LeaderboardService(userRepo, portfolioService, tradingService, 100000.0);
        var bots = new BotService(tradingService, chartService, 5000);

        priceFeed.addListener(marketService);
        priceFeed.addListener(tradingService);
        priceFeed.addListener(chartService);

        User demo = authService.register("demo", "demo");
        authService.login("demo", "demo");

        // Seed some trades
        tradingService.placeOrder(demo, marketService.findBySymbol("AAPL").orElse(null), OrderSide.BUY, 25, 185.0);
        tradingService.placeOrder(demo, marketService.findBySymbol("BTC").orElse(null), OrderSide.BUY, 1, 65000.0);
        tradingService.placeOrder(demo, marketService.findBySymbol("RELIANCE").orElse(null), OrderSide.BUY, 20, 2900.0);

        AppContext ctx = new AppContext(authService, marketService, tradingService, portfolioService,
                chartService, marketData, analytics, leaderboard, bots, fees, priceFeed, 100000.0);

        SwingUtilities.invokeAndWait(() -> {
            try {
                MainFrame frame = new MainFrame(ctx);
                frame.setSize(1280, 720); // Test standard laptop resolution
                frame.doLayout();
                frame.validate();

                // Call showApp via reflection
                Method showAppMethod = MainFrame.class.getDeclaredMethod("showApp");
                showAppMethod.setAccessible(true);
                showAppMethod.invoke(frame);

                frame.doLayout();
                frame.validate();

                Field pagesField = MainFrame.class.getDeclaredField("pages");
                pagesField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, BasePanel> pages = (Map<String, BasePanel>) pagesField.get(frame);

                Method showPageMethod = MainFrame.class.getDeclaredMethod("showPage", String.class);
                showPageMethod.setAccessible(true);

                File outDir = new File("C:/Users/ASUS/.gemini/antigravity/brain/8eec8f69-e11c-4b5e-9f8e-0859cffcc369");
                outDir.mkdirs();

                frame.addNotify();
                frame.validate();

                for (String pageName : pages.keySet()) {
                    showPageMethod.invoke(frame, pageName);
                    BasePanel p = pages.get(pageName);
                    p.refresh();
                    frame.validate();
                    frame.repaint();

                    int w = frame.getWidth();
                    int h = frame.getHeight();
                    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g2 = img.createGraphics();
                    frame.getContentPane().printAll(g2);
                    g2.dispose();

                    String safeName = pageName.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
                    File outFile = new File(outDir, "screenshot_" + safeName + ".png");
                    ImageIO.write(img, "PNG", outFile);
                    System.out.println("Captured: " + outFile.getAbsolutePath());

                    if ("Dashboard".equals(pageName)) {
                        DashboardPanel dp = (DashboardPanel) p;
                        Field chartField = DashboardPanel.class.getDeclaredField("performanceChart");
                        chartField.setAccessible(true);
                        com.papertrader.ui.components.PortfolioPerformanceChart ppc = (com.papertrader.ui.components.PortfolioPerformanceChart) chartField.get(dp);
                        ppc.setMaximized(true);
                        frame.validate();
                        frame.repaint();
                        BufferedImage imgMax = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                        Graphics2D gMax = imgMax.createGraphics();
                        frame.getContentPane().printAll(gMax);
                        gMax.dispose();
                        ImageIO.write(imgMax, "PNG", new File(outDir, "screenshot_dashboard_maximized.png"));
                        ppc.setMaximized(false);
                        frame.validate();
                        frame.repaint();
                    }

                    if ("Trade".equals(pageName)) {
                        TradePanel tp = (TradePanel) p;
                        Field chartField = TradePanel.class.getDeclaredField("chartPanel");
                        chartField.setAccessible(true);
                        com.papertrader.ui.components.CandlestickChartPanel ccp = (com.papertrader.ui.components.CandlestickChartPanel) chartField.get(tp);
                        ccp.setMaximized(true);
                        frame.validate();
                        frame.repaint();
                        BufferedImage imgMax = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                        Graphics2D gMax = imgMax.createGraphics();
                        frame.getContentPane().printAll(gMax);
                        gMax.dispose();
                        ImageIO.write(imgMax, "PNG", new File(outDir, "screenshot_trade_maximized.png"));
                        ccp.setMaximized(false);
                        frame.validate();
                        frame.repaint();
                    }
                }

                frame.dispose();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
