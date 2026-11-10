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
        // US Equities
        assets.add(new Stock("AAPL", "Apple Inc.", 190.00, "Technology"));
        assets.add(new Stock("MSFT", "Microsoft Corp.", 420.00, "Technology"));
        assets.add(new Stock("GOOGL", "Alphabet Inc.", 170.00, "Technology"));
        assets.add(new Stock("TSLA", "Tesla Inc.", 250.00, "Automobile"));
        assets.add(new Stock("AMZN", "Amazon.com Inc.", 185.00, "E-Commerce"));

        // Crypto
        assets.add(new Crypto("BTC", "Bitcoin", 68000.00, "Bitcoin"));
        assets.add(new Crypto("ETH", "Ethereum", 3500.00, "Ethereum"));
        assets.add(new Crypto("SOL", "Solana", 150.00, "Solana"));

        // US ETFs
        assets.add(new ETF("SPY", "SPDR S&P 500 ETF", 520.00, "S&P 500"));
        assets.add(new ETF("QQQ", "Invesco QQQ Trust", 445.00, "NASDAQ-100"));

        // India NSE/BSE Bluechips
        assets.add(new Stock("RELIANCE", "Reliance Industries Ltd", 2945.50, "Energy"));
        assets.add(new Stock("TCS", "Tata Consultancy Services", 4280.00, "Technology"));
        assets.add(new Stock("HDFCBANK", "HDFC Bank Ltd", 1642.10, "Banking"));
        assets.add(new Stock("INFY", "Infosys Ltd", 1915.25, "Technology"));
        assets.add(new Stock("ICICIBANK", "ICICI Bank Ltd", 1210.80, "Banking"));
        assets.add(new Stock("BHARTIARTL", "Bharti Airtel Ltd", 1560.40, "Telecom"));
        assets.add(new Stock("SBIN", "State Bank of India", 825.60, "Banking"));
        assets.add(new Stock("ITC", "ITC Limited", 498.20, "FMCG"));
        assets.add(new Stock("LT", "Larsen & Toubro Ltd", 3620.00, "Infrastructure"));
        assets.add(new Stock("TATAMOTORS", "Tata Motors Ltd", 1024.10, "Automobile"));
        assets.add(new Stock("TRENT", "Trent Ltd", 7140.20, "Retail"));
        assets.add(new Stock("ETERNAL", "Eternal Ltd (Zomato)", 265.40, "Consumer Tech"));

        return assets;
    }
}
