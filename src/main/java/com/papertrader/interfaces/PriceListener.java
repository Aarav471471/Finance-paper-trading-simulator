package com.papertrader.interfaces;

import com.papertrader.model.Asset;

/**
 * OBSERVER pattern: anything that wants to hear about price changes implements this.
 * INTERFACE SEGREGATION: it has only ONE method, so implementers are not forced
 * to write methods they do not need.
 */
public interface PriceListener {
    void onPriceUpdate(Asset asset, double oldPrice, double newPrice);
}
