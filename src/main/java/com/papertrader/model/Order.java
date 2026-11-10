package com.papertrader.model;

import java.time.LocalDateTime;

/**
 * ABSTRACTION: an Order describes "buy/sell N units of an asset".
 * The important question, "can this order execute at the current price?",
 * is abstract: each order type answers it differently.
 *
 * RUNTIME POLYMORPHISM: TradingService only ever calls order.canExecute(price).
 * Java decides at runtime whether MarketOrder, LimitOrder or StopLossOrder's
 * version runs. TradingService never needs an if/else on the order type.
 *
 * OPEN/CLOSED PRINCIPLE: a new order type (e.g. TrailingStopOrder) can be added by
 * writing a new subclass, without changing TradingService.
 */
public abstract class Order {

    private static int nextId = 1;   // shared by all orders, gives each one a unique id

    private final int id;
    private final Asset asset;
    private final OrderSide side;
    private final int quantity;
    private final LocalDateTime createdAt;
    private OrderStatus status;
    private double filledPrice;
    private String message = "";

    protected Order(Asset asset, OrderSide side, int quantity) {
        if (asset == null) {
            throw new IllegalArgumentException("Asset is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        this.id = nextId++;
        this.asset = asset;
        this.side = side;
        this.quantity = quantity;
        this.createdAt = LocalDateTime.now();
        this.status = OrderStatus.PENDING;
    }

    /** Should this order execute now, given the current market price? */
    public abstract boolean canExecute(double marketPrice);

    /** Name shown in the UI, e.g. "Market", "Limit", "Stop-Loss". */
    public abstract String getOrderType();

    /** Short description of the condition, e.g. "at market" or "when price <= $180.00". */
    public abstract String getConditionText();

    // ---- state changes (only the order itself can change its status: encapsulation) ----

    public void markFilled(double price) {
        this.status = OrderStatus.FILLED;
        this.filledPrice = price;
    }

    public void markCancelled() {
        this.status = OrderStatus.CANCELLED;
    }

    public void markRejected(String reason) {
        this.status = OrderStatus.REJECTED;
        this.message = reason;
    }

    // ---- getters ----

    public int getId() {
        return id;
    }

    public Asset getAsset() {
        return asset;
    }

    public OrderSide getSide() {
        return side;
    }

    public int getQuantity() {
        return quantity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public double getFilledPrice() {
        return filledPrice;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "#" + id + " " + getOrderType() + " " + side + " " + quantity + " " + asset.getSymbol()
                + " (" + getConditionText() + ") " + status;
    }
}
