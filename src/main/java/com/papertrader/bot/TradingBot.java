package com.papertrader.bot;

import com.papertrader.model.Asset;
import com.papertrader.model.User;
import com.papertrader.strategy.Signal;
import com.papertrader.strategy.TradingStrategy;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * An automatic trader: it watches one asset and follows one TradingStrategy,
 * placing REAL paper orders for its owner. BotService runs it every few seconds.
 */
public class TradingBot {

    private static int nextId = 1;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int id;
    private final User owner;
    private final Asset asset;
    private final TradingStrategy strategy;
    private final int quantity;
    private boolean running = true;
    private Signal lastSignal = Signal.HOLD;
    private int position = 0;   // units THIS bot bought and still holds (it never sells your own shares)
    private final List<String> log = new ArrayList<>();

    public TradingBot(User owner, Asset asset, TradingStrategy strategy, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        this.id = nextId++;
        this.owner = owner;
        this.asset = asset;
        this.strategy = strategy;
        this.quantity = quantity;
        addLog("Started: " + strategy.getDescription());
    }

    public void addLog(String message) {
        log.add(LocalTime.now().format(TIME) + "  Bot #" + id + " " + asset.getSymbol() + ": " + message);
    }

    public int getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public Asset getAsset() {
        return asset;
    }

    public TradingStrategy getStrategy() {
        return strategy;
    }

    public int getQuantity() {
        return quantity;
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        running = false;
        addLog("Stopped");
    }

    public int getPosition() {
        return position;
    }

    public void changePosition(int units) {
        position += units;
    }

    public Signal getLastSignal() {
        return lastSignal;
    }

    public void setLastSignal(Signal signal) {
        this.lastSignal = signal;
    }

    public List<String> getLog() {
        return new ArrayList<>(log);
    }
}
