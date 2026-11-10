package com.papertrader.backtest;

import com.papertrader.analytics.EquityPoint;
import com.papertrader.analytics.PerformanceAnalyzer;
import com.papertrader.analytics.PerformanceReport;
import com.papertrader.interfaces.FeeCalculator;
import com.papertrader.model.Candle;
import com.papertrader.model.OrderSide;
import com.papertrader.model.TradeMarker;
import com.papertrader.strategy.Signal;
import com.papertrader.strategy.TradingStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * Replays past candles one by one and lets a strategy trade them with virtual cash.
 * It uses its OWN cash and units (not the user's real paper account), so testing
 * a strategy never touches your portfolio.
 *
 * Rules: all-in. On BUY, spend all cash; on SELL, sell everything. Trades fill at the
 * candle's close, made slightly worse by slippage, and pay the normal brokerage fee.
 * step() moves one candle forward, so the UI can replay at 1x / 2x / 5x speed.
 */
public class BacktestEngine {

    /** One finished round trip: bought at entryPrice, sold at exitPrice. */
    public record ClosedTrade(long entryTime, long exitTime, double entryPrice, double exitPrice, double profit) {
    }

    private final List<Candle> candles;
    private final TradingStrategy strategy;
    private final FeeCalculator fees;
    private final double slippagePercent;
    private final double startingCash;

    private int index = 0;
    private double cash;
    private double units = 0;
    private double cashAtEntry;
    private long entryTime;
    private double entryPrice;

    private final List<EquityPoint> equity = new ArrayList<>();
    private final List<ClosedTrade> trades = new ArrayList<>();
    private final List<TradeMarker> markers = new ArrayList<>();

    public BacktestEngine(List<Candle> candles, TradingStrategy strategy, double startingCash,
                          FeeCalculator fees, double slippagePercent) {
        this.candles = candles;
        this.strategy = strategy;
        this.fees = fees;
        this.slippagePercent = slippagePercent;
        this.startingCash = startingCash;
        this.cash = startingCash;
    }

    public boolean hasNext() {
        return index < candles.size();
    }

    /** Process the next candle. */
    public void step() {
        Candle candle = candles.get(index);
        List<Candle> history = candles.subList(0, index + 1);   // the strategy can only see the past
        Signal signal = strategy.decide(history, units > 0);    // runtime polymorphism

        if (signal == Signal.BUY && units == 0 && cash > 0) {
            double price = candle.close() * (1 + slippagePercent / 100);
            double fee = fees.calculateFee(cash);
            cashAtEntry = cash;
            units = (cash - fee) / price;
            cash = 0;
            entryTime = candle.time();
            entryPrice = price;
            markers.add(new TradeMarker(candle.time(), OrderSide.BUY));
        } else if (signal == Signal.SELL && units > 0) {
            double price = candle.close() * (1 - slippagePercent / 100);
            double proceeds = units * price;
            cash = proceeds - fees.calculateFee(proceeds);
            units = 0;
            trades.add(new ClosedTrade(entryTime, candle.time(), entryPrice, price, cash - cashAtEntry));
            markers.add(new TradeMarker(candle.time(), OrderSide.SELL));
        }

        equity.add(new EquityPoint(candle.time(), getEquity(candle.close())));
        index++;
    }

    public void runToEnd() {
        while (hasNext()) {
            step();
        }
    }

    private double getEquity(double price) {
        return cash + units * price;
    }

    public PerformanceReport getReport() {
        List<Double> profits = new ArrayList<>();
        for (ClosedTrade t : trades) {
            profits.add(t.profit());
        }
        // an open position at the end counts as a trade closed at the last price
        if (units > 0 && index > 0) {
            profits.add(getEquity(candles.get(index - 1).close()) - cashAtEntry);
        }
        List<EquityPoint> curve = new ArrayList<>();
        curve.add(new EquityPoint(candles.get(0).time() - 1, startingCash));
        curve.addAll(equity);
        return PerformanceAnalyzer.analyze(curve, profits);
    }

    /** Return of simply buying at the first candle and holding to the current one. */
    public double getBuyAndHoldReturnPercent() {
        if (index == 0) {
            return 0;
        }
        return (candles.get(index - 1).close() / candles.get(0).close() - 1) * 100;
    }

    public List<Candle> getCandlesSoFar() {
        return new ArrayList<>(candles.subList(0, index));
    }

    public List<EquityPoint> getEquityCurve() {
        return new ArrayList<>(equity);
    }

    public List<ClosedTrade> getTrades() {
        return new ArrayList<>(trades);
    }

    public List<TradeMarker> getMarkers() {
        return new ArrayList<>(markers);
    }

    public double getCurrentEquity() {
        return index == 0 ? startingCash : getEquity(candles.get(index - 1).close());
    }

    public int getProgress() {
        return index;
    }

    public int getTotalCandles() {
        return candles.size();
    }

    public boolean isHolding() {
        return units > 0;
    }
}
