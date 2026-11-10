package com.papertrader.service;

import com.papertrader.interfaces.PriceListener;
import com.papertrader.model.Asset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SINGLE RESPONSIBILITY: knows which assets exist and remembers recent prices for charts.
 * It is a PriceListener, so the price feed tells it about every new price.
 */
public class MarketService implements PriceListener {

    private static final int HISTORY_SIZE = 90;   // number of points kept for the line chart

    private final Map<String, Asset> assets = new LinkedHashMap<>();
    private final Map<String, LinkedList<Double>> priceHistory = new HashMap<>();

    public MarketService(List<Asset> assetList) {
        for (Asset asset : assetList) {
            assets.put(asset.getSymbol(), asset);
            LinkedList<Double> history = new LinkedList<>();
            history.add(asset.getPrice());
            priceHistory.put(asset.getSymbol(), history);
        }
    }

    public List<Asset> getAllAssets() {
        return new ArrayList<>(assets.values());
    }

    public Optional<Asset> findBySymbol(String symbol) {
        return Optional.ofNullable(assets.get(symbol));
    }

    public List<Double> getPriceHistory(String symbol) {
        LinkedList<Double> history = priceHistory.get(symbol);
        return history == null ? new ArrayList<>() : new ArrayList<>(history);
    }

    @Override
    public void onPriceUpdate(Asset asset, double oldPrice, double newPrice) {
        LinkedList<Double> history = priceHistory.get(asset.getSymbol());
        if (history == null) {
            return;
        }
        history.addLast(newPrice);
        if (history.size() > HISTORY_SIZE) {
            history.removeFirst();
        }
    }
}
