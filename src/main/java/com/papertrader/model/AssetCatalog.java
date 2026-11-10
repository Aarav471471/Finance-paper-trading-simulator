package com.papertrader.model;

import java.util.ArrayList;
import java.util.List;

/** The list of assets the simulator starts with. Kept in one place so it is easy to change. */
public final class AssetCatalog {

    private AssetCatalog() {
        // utility class, no objects needed
    }

    public static List<Asset> defaultAssets() {
        List<Asset> assets = new ArrayList<>();
        assets.add(new Stock("AAPL", "Apple Inc.", 190.00, "Technology"));
        assets.add(new Stock("MSFT", "Microsoft Corp.", 420.00, "Technology"));
        assets.add(new Stock("GOOGL", "Alphabet Inc.", 170.00, "Technology"));
        assets.add(new Stock("TSLA", "Tesla Inc.", 250.00, "Automobile"));
        assets.add(new Stock("AMZN", "Amazon.com Inc.", 185.00, "E-Commerce"));
        assets.add(new Crypto("BTC", "Bitcoin", 68000.00, "Bitcoin"));
        assets.add(new Crypto("ETH", "Ethereum", 3500.00, "Ethereum"));
        assets.add(new Crypto("SOL", "Solana", 150.00, "Solana"));
        assets.add(new ETF("SPY", "SPDR S&P 500 ETF", 520.00, "S&P 500"));
        assets.add(new ETF("QQQ", "Invesco QQQ Trust", 445.00, "NASDAQ-100"));
        return assets;
    }
}
