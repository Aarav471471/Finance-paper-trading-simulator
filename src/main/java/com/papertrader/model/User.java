package com.papertrader.model;

import com.papertrader.util.PasswordHasher;

/**
 * A registered trader. COMPOSITION: a User HAS-AN Account (cash) and HAS-A Portfolio (assets).
 * The password is never stored in plain text, only its hash.
 */
public class User {

    private final String username;
    private final String passwordHash;
    private final Account account;
    private final Portfolio portfolio;

    public User(String username, String passwordHash, double cashBalance) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.account = new Account(cashBalance);
        this.portfolio = new Portfolio();
    }

    public boolean checkPassword(String plainPassword) {
        return passwordHash.equals(PasswordHasher.hash(plainPassword));
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Account getAccount() {
        return account;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }
}
