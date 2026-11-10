package com.papertrader;

import com.papertrader.exception.SimulatorException;
import com.papertrader.feed.SimulatedPriceFeed;
import com.papertrader.fees.FlatFeeCalculator;
import com.papertrader.model.Asset;
import com.papertrader.model.AssetCatalog;
import com.papertrader.model.Order;
import com.papertrader.model.OrderSide;
import com.papertrader.model.StopLossOrder;
import com.papertrader.model.User;
import com.papertrader.repository.InMemoryTransactionRepository;
import com.papertrader.repository.InMemoryUserRepository;
import com.papertrader.service.AuthService;
import com.papertrader.service.MarketService;
import com.papertrader.service.PortfolioService;
import com.papertrader.service.TradingService;
import com.papertrader.util.Formatter;

import java.util.List;

/**
 * Spiral model, loop 1 prototype: the whole trading engine running in the console, no UI at all.
 * It proves the UI is only a "layer on top": the same services work without Swing (low coupling).
 *
 * Run with:  mvn compile exec:java -Dexec.mainClass=com.papertrader.ConsoleDemo
 */
public class ConsoleDemo {

    public static void main(String[] args) throws SimulatorException {
        List<Asset> assets = AssetCatalog.defaultAssets();
        MarketService market = new MarketService(assets);
        InMemoryUserRepository users = new InMemoryUserRepository();
        TradingService trading = new TradingService(new FlatFeeCalculator(2.0), users, new InMemoryTransactionRepository());
        AuthService auth = new AuthService(users, 10_000);
        PortfolioService portfolio = new PortfolioService(market);
        SimulatedPriceFeed feed = new SimulatedPriceFeed(assets, 1000);
        feed.addListener(market);
        feed.addListener(trading);

        User alice = auth.register("alice", "secret");
        Asset apple = market.findBySymbol("AAPL").orElseThrow();

        trading.placeOrder(alice, apple, OrderSide.BUY, 10);                                   // market order
        Order stop = trading.placeOrder(alice, new StopLossOrder(apple, OrderSide.SELL, 10, apple.getPrice() * 0.99));
        System.out.println("Bought 10 AAPL. Cash left: " + Formatter.money(alice.getAccount().getBalance()));
        System.out.println("Waiting order: " + stop);

        for (int i = 1; i <= 15; i++) {
            feed.tick();   // move prices once (normally the timer does this every second)
            System.out.printf("Tick %2d  AAPL %s   net worth %s   stop-loss %s%n", i, Formatter.money(apple.getPrice()),
                    Formatter.money(portfolio.getNetWorth(alice)), stop.getStatus());
        }
        System.out.println("Transactions: " + trading.getTransactionHistory(alice).size());
    }
}
