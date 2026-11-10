package com.papertrader.model;

import com.papertrader.exception.InsufficientFundsException;

/**
 * ENCAPSULATION example: the cash balance is private.
 * Nobody can write "account.balance = 1000000". The only way to change it is
 * deposit() and withdraw(), which check the amount first.
 */
public class Account {

    private double balance;

    public Account(double openingBalance) {
        if (openingBalance < 0) {
            throw new IllegalArgumentException("Opening balance cannot be negative");
        }
        this.balance = openingBalance;
    }

    public double getBalance() {
        return balance;
    }

    public void deposit(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit must be positive");
        }
        balance += amount;
    }

    public void withdraw(double amount) throws InsufficientFundsException {
        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal must be positive");
        }
        if (amount > balance) {
            throw new InsufficientFundsException(amount, balance);
        }
        balance -= amount;
    }
}
