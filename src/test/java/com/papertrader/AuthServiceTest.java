package com.papertrader;

import com.papertrader.exception.AuthException;
import com.papertrader.repository.InMemoryUserRepository;
import com.papertrader.service.AuthService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    private final AuthService auth = new AuthService(new InMemoryUserRepository(), 50_000);

    @Test
    void registerThenLogin() throws Exception {
        auth.register("riya", "pass1234");
        auth.login("riya", "pass1234");
        assertTrue(auth.isLoggedIn());
        assertEquals(50_000, auth.getCurrentUser().getAccount().getBalance(), 0.001);
    }

    @Test
    void wrongPasswordFails() throws Exception {
        auth.register("riya", "pass1234");
        assertThrows(AuthException.class, () -> auth.login("riya", "nope"));
    }

    @Test
    void duplicateAndInvalidUsernamesAreRejected() throws Exception {
        auth.register("riya", "pass1234");
        assertThrows(AuthException.class, () -> auth.register("riya", "other123"));
        assertThrows(AuthException.class, () -> auth.register("a b", "pass1234"));
        assertThrows(AuthException.class, () -> auth.register("valid", "123"));
    }
}
