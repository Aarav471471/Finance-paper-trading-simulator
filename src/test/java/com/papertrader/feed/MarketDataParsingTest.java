package com.papertrader.feed;

import com.papertrader.model.Fundamentals;
import com.papertrader.model.MarketSnapshot;
import com.papertrader.model.NewsItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reads saved example answers of the real APIs, so the tests need no internet. */
class MarketDataParsingTest {

    @Test
    void readsYahooDaySnapshot() throws Exception {
        String json = "{\"chart\":{\"result\":[{\"meta\":{\"regularMarketPrice\":1500.5,\"chartPreviousClose\":1480.0,"
                + "\"regularMarketDayHigh\":1510.0,\"regularMarketDayLow\":1475.2,\"regularMarketVolume\":6420000,"
                + "\"fiftyTwoWeekHigh\":1608.8,\"fiftyTwoWeekLow\":1114.85},"
                + "\"indicators\":{\"quote\":[{\"open\":[1482.0]}]}}]}}";
        MarketSnapshot s = YahooQuoteProvider.parseSnapshot(json);
        assertEquals(1500.5, s.price());
        assertEquals(1482.0, s.open());
        assertEquals(1510.0, s.dayHigh());
        assertEquals(6_420_000, s.volume());
        assertEquals(1114.85, s.week52Low());
        assertEquals(20.5, s.change(), 1e-9);
    }

    @Test
    void readsBinance24hSnapshot() {
        String json = "{\"symbol\":\"BTCUSDT\",\"lastPrice\":\"68000\",\"openPrice\":\"67000\","
                + "\"highPrice\":\"68500\",\"lowPrice\":\"66500\",\"volume\":\"12345.6\"}";
        MarketSnapshot s = BinanceQuoteProvider.parseSnapshot(json);
        assertEquals(68000, s.price());
        assertEquals(66500, s.dayLow());
        assertEquals(12345.6, s.volume());
        assertTrue(Double.isNaN(s.week52High()));   // filled from daily candles later
    }

    @Test
    void readsYahooFundamentalsAndMissingValuesBecomeNaN() throws Exception {
        String json = "{\"quoteSummary\":{\"result\":[{"
                + "\"summaryDetail\":{\"trailingPE\":{\"raw\":28.4},\"marketCap\":{\"raw\":3.4E12}},"
                + "\"defaultKeyStatistics\":{\"trailingEps\":{\"raw\":6.1},\"bookValue\":{\"raw\":4.4}},"
                + "\"financialData\":{\"debtToEquity\":{\"raw\":150.0},\"returnOnEquity\":{\"raw\":1.5},"
                + "\"targetMedianPrice\":{\"raw\":240.0},\"numberOfAnalystOpinions\":{\"raw\":38},\"recommendationKey\":\"buy\"},"
                + "\"assetProfile\":{\"sector\":\"Technology\",\"fullTimeEmployees\":161000,\"city\":\"Cupertino\","
                + "\"companyOfficers\":[{\"name\":\"Mr. Timothy D. Cook\",\"title\":\"CEO & Director\"}]},"
                + "\"incomeStatementHistory\":{\"incomeStatementHistory\":[{\"endDate\":{\"fmt\":\"2024-09-28\"},"
                + "\"totalRevenue\":{\"raw\":3.9E11},\"ebit\":{\"raw\":1.2E11},\"interestExpense\":{\"raw\":-3.0E9},"
                + "\"netIncome\":{\"raw\":9.4E10}}]},"
                + "\"majorHoldersBreakdown\":{\"insidersPercentHeld\":{\"raw\":0.02},\"institutionsPercentHeld\":{\"raw\":0.62}}"
                + "}]}}";
        Fundamentals f = YahooFundamentalsProvider.parseFundamentals(json);
        assertEquals(28.4, f.peRatio());
        assertEquals(1.5, f.debtToEquity(), 1e-9);   // 150 % -> 1.5
        assertEquals(38, f.analystCount());
        assertEquals("Mr. Timothy D. Cook (CEO & Director)", f.ceo());
        assertEquals("FY24", f.years().get(0).year());
        assertEquals(40.0, f.interestCoverage(), 1e-9);  // 1.2E11 / 3.0E9
        assertTrue(Double.isNaN(f.freeCashFlow()));      // not in the answer -> N/A, never invented
    }

    @Test
    void readsYahooNews() {
        String json = "{\"news\":[{\"title\":\"Apple beats estimates\",\"publisher\":\"Reuters\","
                + "\"providerPublishTime\":1731200000,\"link\":\"https://example.com/a\"}]}";
        List<NewsItem> news = YahooFundamentalsProvider.parseNews(json);
        assertEquals(1, news.size());
        assertEquals("Reuters", news.get(0).publisher());
        assertEquals(1731200000000L, news.get(0).timeMillis());
    }

    @Test
    void readsCoinGeckoGlobal() {
        double[] cap = CoinGeckoGlobalProvider.parse(
                "{\"data\":{\"total_market_cap\":{\"usd\":2.48E12},\"market_cap_change_percentage_24h_usd\":-0.85}}");
        assertEquals(2.48E12, cap[0]);
        assertEquals(-0.85, cap[1]);
    }

    @Test
    void yahooSymbolsForIndiaAndCrypto() {
        assertEquals("RELIANCE.NS", YahooSymbols.of("RELIANCE"));
        assertEquals("AAPL", YahooSymbols.of("AAPL"));
        assertEquals("BTC-USD", YahooSymbols.of(new com.papertrader.model.Crypto("BTC", "Bitcoin", 1, "Bitcoin")));
    }
}
