package com.papertrader.execution;

import com.papertrader.model.Asset;
import com.papertrader.model.OrderSide;

/**
 * Decides the REAL price an order fills at. In a real market you rarely get exactly the
 * "last price": buyers pay the ask, sellers get the bid, and big orders move the price (slippage).
 * STRATEGY pattern: TradingService works with any ExecutionModel.
 */
public interface ExecutionModel {

    double getFillPrice(Asset asset, OrderSide side, int quantity);

    /** Expected extra cost in percent of price (spread + slippage), for the order preview. */
    double estimateCostPercent(Asset asset, int quantity);

    String getDescription();
}
