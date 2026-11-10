package com.papertrader.execution;

import com.papertrader.model.Asset;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simulates an order book (market depth): the waiting buy orders (bids) below the price
 * and waiting sell orders (asks) above it. Free APIs do not give full depth, so we build
 * a realistic one around the real bid/ask: levels get further apart and bigger the further
 * they are from the price.
 */
public class OrderBookSimulator {

    /** One price level: price and how many units wait there. */
    public record Level(double price, double size) {
    }

    /** The whole book: bids sorted high-to-low, asks sorted low-to-high. */
    public record OrderBook(List<Level> bids, List<Level> asks) {
        public double spread() {
            return asks.get(0).price() - bids.get(0).price();
        }
    }

    public OrderBook simulate(Asset asset, int levels) {
        // Seed from the price so the book stays steady until the price changes.
        Random random = new Random(Double.doubleToLongBits(asset.getPrice()));
        double tick = asset.getPrice() * asset.getSpreadPercent() / 100.0;
        double dollarsPerLevel = 40_000;   // about $40k waiting at the best level

        List<Level> bids = new ArrayList<>();
        List<Level> asks = new ArrayList<>();
        for (int i = 0; i < levels; i++) {
            double depthFactor = 1 + i * 0.35;
            double bidPrice = asset.getBid() - i * tick;
            double askPrice = asset.getAsk() + i * tick;
            bids.add(new Level(bidPrice, size(dollarsPerLevel * depthFactor, bidPrice, random)));
            asks.add(new Level(askPrice, size(dollarsPerLevel * depthFactor, askPrice, random)));
        }
        return new OrderBook(bids, asks);
    }

    private double size(double dollars, double price, Random random) {
        double units = dollars / price * (0.6 + random.nextDouble() * 0.8);
        return price > 1000 ? Math.round(units * 1000) / 1000.0 : Math.round(units);
    }
}
