package com.papertrader.model;

import com.papertrader.interfaces.Exportable;

import java.time.LocalDateTime;

/** A record of one completed trade. All fields are final, so a transaction can never be changed. */
public class Transaction implements Exportable {

    public static final String CSV_HEADER = "id,username,timestamp,symbol,side,orderType,quantity,price,fee";

    private final int id;
    private final String username;
    private final LocalDateTime timestamp;
    private final String symbol;
    private final OrderSide side;
    private final String orderType;
    private final int quantity;
    private final double price;
    private final double fee;

    public Transaction(int id, String username, LocalDateTime timestamp, String symbol, OrderSide side,
                       String orderType, int quantity, double price, double fee) {
        this.id = id;
        this.username = username;
        this.timestamp = timestamp;
        this.symbol = symbol;
        this.side = side;
        this.orderType = orderType;
        this.quantity = quantity;
        this.price = price;
        this.fee = fee;
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

    @Override
    public String toCsvRow() {
        return id + "," + username + "," + timestamp + "," + symbol + "," + side + "," + orderType + ","
                + quantity + "," + price + "," + fee;
    }
}
