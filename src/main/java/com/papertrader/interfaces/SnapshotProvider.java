package com.papertrader.interfaces;

import com.papertrader.model.Asset;
import com.papertrader.model.MarketSnapshot;

import java.io.IOException;

/** A source of today's open / high / low / volume and 52-week range for an asset. */
public interface SnapshotProvider {

    boolean supports(Asset asset);

    MarketSnapshot fetchSnapshot(Asset asset) throws IOException;
}
