package com.papertrader.service;

import com.papertrader.bot.BotService;
import com.papertrader.interfaces.FeeCalculator;
import com.papertrader.interfaces.PriceFeed;

/**
 * Bundles every service the UI needs, so MainFrame does not need a constructor with 12 parameters.
 * It is created once in App.java (the composition root) and passed to the window.
 */
public record AppContext(
        AuthService auth,
        MarketService market,
        TradingService trading,
        PortfolioService portfolio,
        ChartService charts,
        AnalyticsService analytics,
        LeaderboardService leaderboard,
        BotService bots,
        FeeCalculator fees,
        PriceFeed priceFeed,
        double startingCash) {
}
