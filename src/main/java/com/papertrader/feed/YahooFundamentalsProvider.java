package com.papertrader.feed;

import com.papertrader.interfaces.FundamentalsProvider;
import com.papertrader.model.Asset;
import com.papertrader.model.FinancialYear;
import com.papertrader.model.Fundamentals;
import com.papertrader.model.NewsItem;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Real company data from Yahoo Finance (no API key):
 *  - quoteSummary: P/E, EPS, market cap, ROE, debt/equity, analyst target, profile, income statements, holders
 *  - search: the latest news headlines
 *
 * quoteSummary needs a cookie + "crumb" (a small anti-bot token), just like the website:
 *  1. visit fc.yahoo.com to receive a cookie   2. ask /v1/test/getcrumb for the crumb   3. add &crumb=... to the request
 */
public class YahooFundamentalsProvider implements FundamentalsProvider {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final String MODULES = "summaryDetail,defaultKeyStatistics,financialData,assetProfile,"
            + "incomeStatementHistory,majorHoldersBreakdown";

    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))   // keeps Yahoo's cookie
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private String crumb;

    @Override
    public Fundamentals fetchFundamentals(Asset asset) throws IOException {
        String url = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/"
                + URLEncoder.encode(YahooSymbols.of(asset), StandardCharsets.UTF_8)
                + "?modules=" + MODULES + "&crumb=" + URLEncoder.encode(getCrumb(), StandardCharsets.UTF_8);
        HttpResponse<String> response = send(url);
        if (response.statusCode() == 401) {   // crumb expired: get a new one and try once more
            crumb = null;
            response = send(url.replaceAll("&crumb=.*$", "&crumb=" + URLEncoder.encode(getCrumb(), StandardCharsets.UTF_8)));
        }
        if (response.statusCode() != 200) {
            throw new IOException("Yahoo quoteSummary HTTP " + response.statusCode());
        }
        return parseFundamentals(response.body());
    }

    @Override
    public List<NewsItem> fetchNews(Asset asset, int count) throws IOException {
        HttpResponse<String> response = send("https://query1.finance.yahoo.com/v1/finance/search?q="
                + URLEncoder.encode(YahooSymbols.of(asset), StandardCharsets.UTF_8)
                + "&quotesCount=0&newsCount=" + count);
        if (response.statusCode() != 200) {
            throw new IOException("Yahoo search HTTP " + response.statusCode());
        }
        return parseNews(response.body());
    }

    private synchronized String getCrumb() throws IOException {
        if (crumb == null) {
            send("https://fc.yahoo.com");   // answers 404, but sets the cookie we need
            HttpResponse<String> r = send("https://query1.finance.yahoo.com/v1/test/getcrumb");
            if (r.statusCode() != 200 || r.body().isBlank() || r.body().contains("<")) {
                throw new IOException("Could not get a Yahoo crumb (HTTP " + r.statusCode() + ")");
            }
            crumb = r.body().trim();
        }
        return crumb;
    }

    private HttpResponse<String> send(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json,text/plain,*/*")
                .GET().build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    // ---------------- JSON reading (static, so tests can use saved answers) ----------------

    /** Yahoo writes numbers as {"raw": 28.4, "fmt": "28.40"}; missing ones become NaN. */
    private static double raw(JSONObject module, String key) {
        if (module == null) {
            return Double.NaN;
        }
        JSONObject value = module.optJSONObject(key);
        return value == null ? Double.NaN : value.optDouble("raw", Double.NaN);
    }

    static Fundamentals parseFundamentals(String json) throws IOException {
        JSONObject root = new JSONObject(json).getJSONObject("quoteSummary");
        JSONArray results = root.optJSONArray("result");
        if (results == null || results.isEmpty()) {
            throw new IOException("Yahoo has no data for this symbol");
        }
        JSONObject r = results.getJSONObject(0);
        JSONObject summary = r.optJSONObject("summaryDetail");
        JSONObject stats = r.optJSONObject("defaultKeyStatistics");
        JSONObject fin = r.optJSONObject("financialData");
        JSONObject profile = r.optJSONObject("assetProfile");
        JSONObject holders = r.optJSONObject("majorHoldersBreakdown");

        double pe = raw(summary, "trailingPE");
        if (Double.isNaN(pe)) {
            pe = raw(summary, "forwardPE");
        }
        double debtToEquity = raw(fin, "debtToEquity") / 100.0;   // Yahoo gives it as a percent

        String ceo = "";
        if (profile != null && profile.optJSONArray("companyOfficers") != null
                && !profile.getJSONArray("companyOfficers").isEmpty()) {
            JSONObject officer = profile.getJSONArray("companyOfficers").getJSONObject(0);
            ceo = officer.optString("name", "") + (officer.has("title") ? " (" + officer.optString("title") + ")" : "");
        }

        List<FinancialYear> years = new ArrayList<>();
        JSONObject income = r.optJSONObject("incomeStatementHistory");
        if (income != null && income.optJSONArray("incomeStatementHistory") != null) {
            JSONArray list = income.getJSONArray("incomeStatementHistory");
            for (int i = 0; i < list.length(); i++) {
                JSONObject y = list.getJSONObject(i);
                JSONObject end = y.optJSONObject("endDate");
                String year = end == null ? "?" : end.optString("fmt", "?");
                years.add(new FinancialYear(year.length() >= 4 ? "FY" + year.substring(2, 4) : year,
                        raw(y, "totalRevenue"), raw(y, "costOfRevenue"), raw(y, "grossProfit"),
                        raw(y, "operatingIncome"), raw(y, "ebit"), raw(y, "interestExpense"), raw(y, "netIncome")));
            }
        }

        return new Fundamentals(
                raw(summary, "marketCap"), pe, raw(stats, "trailingEps"), raw(stats, "bookValue"),
                raw(fin, "returnOnEquity"), raw(fin, "returnOnAssets"), debtToEquity,
                raw(stats, "enterpriseValue"), raw(stats, "enterpriseToEbitda"), raw(fin, "freeCashflow"),
                raw(fin, "profitMargins"),
                raw(fin, "targetMedianPrice"), (int) zeroIfNaN(raw(fin, "numberOfAnalystOpinions")),
                fin == null ? "" : fin.optString("recommendationKey", ""),
                profile == null ? "" : profile.optString("longBusinessSummary", profile.optString("description", "")),
                profile == null ? "" : profile.optString("sector", ""),
                profile == null ? "" : profile.optString("industry", ""),
                profile == null ? 0 : profile.optInt("fullTimeEmployees", 0),
                profile == null ? "" : profile.optString("city", ""),
                profile == null ? "" : profile.optString("country", ""),
                ceo,
                raw(holders, "insidersPercentHeld"), raw(holders, "institutionsPercentHeld"),
                (int) zeroIfNaN(raw(holders, "institutionsCount")),
                years);
    }

    private static double zeroIfNaN(double v) {
        return Double.isNaN(v) ? 0 : v;
    }

    static List<NewsItem> parseNews(String json) {
        List<NewsItem> items = new ArrayList<>();
        JSONArray news = new JSONObject(json).optJSONArray("news");
        if (news == null) {
            return items;
        }
        for (int i = 0; i < news.length(); i++) {
            JSONObject n = news.getJSONObject(i);
            items.add(new NewsItem(n.optString("title", ""), n.optString("publisher", ""),
                    n.optLong("providerPublishTime", 0) * 1000L, n.optString("link", "")));
        }
        return items;
    }
}
