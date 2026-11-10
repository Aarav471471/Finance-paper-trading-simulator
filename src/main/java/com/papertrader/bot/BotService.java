package com.papertrader.bot;

import com.papertrader.exception.SimulatorException;
import com.papertrader.model.Candle;
import com.papertrader.model.OrderSide;
import com.papertrader.model.Timeframe;
import com.papertrader.model.User;
import com.papertrader.service.ChartService;
import com.papertrader.service.TradingService;
import com.papertrader.strategy.Signal;

import javax.swing.Timer;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs all trading bots. Every few seconds each running bot:
 *  1. gets the latest 1-minute candles of its asset,
 *  2. asks its strategy BUY / SELL / HOLD,
 *  3. places a market order through TradingService (same rules, fees and slippage as a human).
 */
public class BotService {

    private final TradingService tradingService;
    private final ChartService chartService;
    private final List<TradingBot> bots = new ArrayList<>();
    private final Timer timer;

    public BotService(TradingService tradingService, ChartService chartService, int intervalMillis) {
        this.tradingService = tradingService;
        this.chartService = chartService;
        this.timer = new Timer(intervalMillis, e -> runAll());
    }

    public void start() {
        timer.start();
    }

    public TradingBot addBot(TradingBot bot) {
        bots.add(bot);
        runBot(bot);   // first decision straight away
        return bot;
    }

    public List<TradingBot> getBots(User user) {
        List<TradingBot> result = new ArrayList<>();
        for (TradingBot bot : bots) {
            if (bot.getOwner() == user) {
                result.add(bot);
            }
        }
        return result;
    }

    public void stopAll(User user) {
        for (TradingBot bot : getBots(user)) {
            if (bot.isRunning()) {
                bot.stop();
            }
        }
    }

    void runAll() {
        for (TradingBot bot : bots) {
            if (bot.isRunning()) {
                runBot(bot);
            }
        }
    }

    /** One decision for one bot. */
    void runBot(TradingBot bot) {
        List<Candle> candles = chartService.getCandles(bot.getAsset(), Timeframe.ONE_MINUTE);
        if (candles.isEmpty()) {
            return;   // history still loading
        }
        User user = bot.getOwner();
        String symbol = bot.getAsset().getSymbol();   // used when selling
        Signal signal = bot.getStrategy().decide(candles, bot.getPosition() > 0);
        bot.setLastSignal(signal);

        try {
            if (signal == Signal.BUY) {
                tradingService.placeOrder(user, bot.getAsset(), OrderSide.BUY, bot.getQuantity());
                bot.changePosition(bot.getQuantity());
                bot.addLog("BUY " + bot.getQuantity() + " at about " + bot.getAsset().getPrice());
            } else if (signal == Signal.SELL && bot.getPosition() > 0) {
                // only sell what the bot itself bought (and what is still in the account)
                int toSell = Math.min(bot.getPosition(), user.getPortfolio().getQuantity(symbol));
                if (toSell > 0) {
                    tradingService.placeOrder(user, bot.getAsset(), OrderSide.SELL, toSell);
                }
                bot.changePosition(-bot.getPosition());
                bot.addLog("SELL " + toSell + " at about " + bot.getAsset().getPrice());
            }
        } catch (SimulatorException e) {
            bot.addLog("order refused: " + e.getMessage());
        }
    }
}
