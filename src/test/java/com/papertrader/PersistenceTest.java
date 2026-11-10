package com.papertrader;

import com.papertrader.model.OrderSide;
import com.papertrader.model.Transaction;
import com.papertrader.model.User;
import com.papertrader.repository.FileTransactionRepository;
import com.papertrader.repository.FileUserRepository;
import com.papertrader.util.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Data written by one repository object must be readable by a new one (like restarting the app). */
class PersistenceTest {

    @TempDir
    Path dir;

    @Test
    void usersAndHoldingsSurviveRestart() {
        FileUserRepository repo = new FileUserRepository(dir);
        User user = new User("sam", PasswordHasher.hash("pw12"), 1234.5);
        user.getPortfolio().addUnits("AAPL", 3, 190);
        repo.save(user);

        User loaded = new FileUserRepository(dir).findById("sam").orElseThrow();
        assertEquals(1234.5, loaded.getAccount().getBalance(), 0.001);
        assertEquals(3, loaded.getPortfolio().getQuantity("AAPL"));
        assertTrue(loaded.checkPassword("pw12"));
    }

    @Test
    void transactionsSurviveRestart() {
        FileTransactionRepository repo = new FileTransactionRepository(dir);
        repo.save(new Transaction(repo.nextId(), "sam", LocalDateTime.now(), "BTC", OrderSide.BUY, "Market", 1, 68000, 68));
        assertEquals(1, new FileTransactionRepository(dir).findByUsername("sam").size());
    }
}
