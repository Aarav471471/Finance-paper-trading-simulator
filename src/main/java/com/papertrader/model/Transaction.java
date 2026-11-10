package com.papertrader.model;

import com.papertrader.interfaces.Exportable;

import java.time.LocalDateTime;

/** A record of one completed trade. All fields are final, so a transaction can never be changed. */
public class Transaction implements Exportable {

    public static final String CSV_HEADER = "id,username,timestamp,symbol,side,orderType,quantity,price,fee,realizedPnl";

    private final int id;
    private final String username;
    private final LocalDateTime timestamp;
    private final String symbol;
    private final OrderSide side;
    private final String orderType;
    private final int quantity;
    private final double price;
    private final double fee;
    private final double realizedPnl;   // profit/loss locked in by a SELL (0 for a BUY)

    /** Constructor for a BUY (nothing is realised when you buy). */
    public Transaction(int id, String username, LocalDateTime timestamp, String symbol, OrderSide side,
                       String orderType, int quantity, double price, double fee) {
        this(id, username, timestamp, symbol, side, orderType, quantity, price, fee, 0);
    }

    /** Full constructor (OVERLOADED): a SELL also records the realised profit or loss. */
    public Transaction(int id, String username, LocalDateTime timestamp, String symbol, OrderSide side,
                       String orderType, int quantity, double price, double fee, double realizedPnl) {
        this.id = id;
        this.username = username;
        this.timestamp = timestamp;
        this.symbol = symbol;
        this.side = side;
        this.orderType = orderType;
        this.quantity = quantity;
        this.price = price;
        this.fee = fee;
        this.realizedPnl = realizedPnl;
    }

    /** Money that left (BUY) or entered (SELL) the account, including the fee. */
    public double getTotal() {
        double value = price * quantity;
        return side == OrderSide.BUY ? value + fee : value - fee;
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public String getOrderType() {
        return orderType;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    public double getFee() {
        return fee;
    }

    public double getRealizedPnl() {
        return realizedPnl;
    }

    @Override
    public String toCsvRow() {
        return id + "," + username + "," + timestamp + "," + symbol + "," + side + "," + orderType + ","
                + quantity + "," + price + "," + fee + "," + realizedPnl;
    }
}
