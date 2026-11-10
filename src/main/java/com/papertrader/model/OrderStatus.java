package com.papertrader.model;

/** Life cycle of an order: PENDING -> FILLED / CANCELLED / REJECTED. */
public enum OrderStatus {
    PENDING, FILLED, CANCELLED, REJECTED
}
