package com.papertrader;

import com.papertrader.exception.InsufficientFundsException;
import com.papertrader.model.Account;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests the encapsulation rules of Account. */
class AccountTest {

    @Test
    void depositAndWithdrawChangeTheBalance() throws Exception {
        Account account = new Account(1000);
        account.deposit(500);
        account.withdraw(300);
        assertEquals(1200, account.getBalance(), 0.001);
    }

    @Test
    void cannotWithdrawMoreThanBalance() {
        Account account = new Account(100);
        assertThrows(InsufficientFundsException.class, () -> account.withdraw(150));
        assertEquals(100, account.getBalance(), 0.001);   // balance unchanged
    }

    @Test
    void rejectsNegativeAmounts() {
        Account account = new Account(100);
        assertThrows(IllegalArgumentException.class, () -> account.deposit(-5));
        assertThrows(IllegalArgumentException.class, () -> account.withdraw(-5));
    }
}
