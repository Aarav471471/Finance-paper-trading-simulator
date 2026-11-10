package com.papertrader.feed;

import com.papertrader.interfaces.HistoryProvider;
import com.papertrader.model.Asset;
import com.papertrader.model.Candle;
import com.papertrader.model.Timeframe;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Makes up realistic-looking past candles when no real history is available (offline mode).
 * It walks BACKWARDS from today's price, so the last candle ends at the current price.
 * The random seed depends on the symbol, so the same asset always gets the same chart.
 */
public class SimulatedHistoryProvider implements HistoryProvider {

    @Override
    public String getName() {
        return "Simulated";
    }

    @Override
    public boolean supports(Asset asset) {
        return true;
    }

    @Override
    public List<Candle> fetchCandles(Asset asset, Timeframe timeframe, int count) {
        Random random = new Random(asset.getSymbol().hashCode() * 31L + timeframe.ordinal());
        double scale = switch (timeframe) {          // bigger candles for longer periods
            case ONE_MINUTE -> 1.5;
            case FIVE_MINUTES -> 3.0;
            case ONE_HOUR -> 4.0;
            case ONE_DAY -> 5.0;
            case ONE_WEEK -> 8.0;
        };
        double sigma = asset.getVolatility() * scale;

        double[] closes = new double[count];
        closes[count - 1] = asset.getPrice();
        for (int i = count - 1; i > 0; i--) {
            closes[i - 1] = closes[i] / (1 + random.nextGaussian() * sigma);
        }

        long step = timeframe.getMillis();
        long lastStart = System.currentTimeMillis() / step * step;
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double open = i == 0 ? closes[0] * (1 - random.nextGaussian() * sigma / 2) : closes[i - 1];
            double close = closes[i];
            double high = Math.max(open, close) * (1 + Math.abs(random.nextGaussian()) * sigma / 2);
            double low = Math.min(open, close) * (1 - Math.abs(random.nextGaussian()) * sigma / 2);
            double volume = 1000 + random.nextInt(9000);
            candles.add(new Candle(lastStart - (long) (count - 1 - i) * step, open, high, low, close, volume));
        }
        return candles;
    }
}
