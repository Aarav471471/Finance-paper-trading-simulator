package com.papertrader.interfaces;

import com.papertrader.model.Asset;
import com.papertrader.model.Fundamentals;
import com.papertrader.model.NewsItem;

import java.io.IOException;
import java.util.List;

/** A source of company facts, ratios and news. */
public interface FundamentalsProvider {

    Fundamentals fetchFundamentals(Asset asset) throws IOException;

    List<NewsItem> fetchNews(Asset asset, int count) throws IOException;
}
