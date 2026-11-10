package com.papertrader.interfaces;

import com.papertrader.analytics.EquityPoint;

import java.util.List;

/** Stores each user's account value over time (the "equity curve") for risk analytics. */
public interface EquityRepository {

    void add(String username, EquityPoint point);

    List<EquityPoint> findByUsername(String username);

    void deleteByUsername(String username);
}
