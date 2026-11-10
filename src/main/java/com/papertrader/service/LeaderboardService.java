package com.papertrader.service;

import com.papertrader.interfaces.Repository;
import com.papertrader.model.User;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Ranks every registered trader by net worth, to make paper trading a friendly competition. */
public class LeaderboardService {

    /** One row of the leaderboard. */
    public record Entry(int rank, String username, double netWorth, double returnPercent,
                        double realizedProfit, int trades) {
    }

    private final Repository<User, String> userRepository;
    private final PortfolioService portfolioService;
    private final TradingService tradingService;
    private final double startingCash;

    public LeaderboardService(Repository<User, String> userRepository, PortfolioService portfolioService,
                              TradingService tradingService, double startingCash) {
        this.userRepository = userRepository;
        this.portfolioService = portfolioService;
        this.tradingService = tradingService;
        this.startingCash = startingCash;
    }

    public List<Entry> getRanking() {
        List<User> users = userRepository.findAll();
        // sort by net worth, highest first (a Comparator built from a method reference)
        users.sort(Comparator.comparingDouble(portfolioService::getNetWorth).reversed());

        List<Entry> ranking = new ArrayList<>();
        int rank = 1;
        for (User user : users) {
            double netWorth = portfolioService.getNetWorth(user);
            ranking.add(new Entry(rank++, user.getUsername(), netWorth,
                    (netWorth / startingCash - 1) * 100,
                    tradingService.getRealizedProfitLoss(user),
                    tradingService.getTransactionHistory(user).size()));
        }
        return ranking;
    }
}
