package com.papertrader.service;

import com.papertrader.exception.AuthException;
import com.papertrader.interfaces.Repository;
import com.papertrader.model.User;
import com.papertrader.util.PasswordHasher;

/**
 * SINGLE RESPONSIBILITY: only handles registering, logging in and logging out.
 * It receives its Repository through the constructor (dependency injection),
 * so it never knows whether users are stored in memory or in files.
 */
public class AuthService {

    private final Repository<User, String> userRepository;
    private final double startingCash;
    private User currentUser;

    public AuthService(Repository<User, String> userRepository, double startingCash) {
        this.userRepository = userRepository;
        this.startingCash = startingCash;
    }

    public User register(String username, String password) throws AuthException {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,20}")) {
            throw new AuthException("Username must be 3-20 letters, digits or _");
        }
        if (password == null || password.length() < 4) {
            throw new AuthException("Password must be at least 4 characters");
        }
        if (userRepository.findById(username).isPresent()) {
            throw new AuthException("Username '" + username + "' is already taken");
        }
        User user = new User(username, PasswordHasher.hash(password), startingCash);
        userRepository.save(user);
        return user;
    }

    public User login(String username, String password) throws AuthException {
        User user = userRepository.findById(username == null ? "" : username)
                .orElseThrow(() -> new AuthException("No account found for '" + username + "'"));
        if (!user.checkPassword(password)) {
            throw new AuthException("Wrong password");
        }
        currentUser = user;
        return user;
    }

    public void logout() {
        currentUser = null;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public boolean userExists(String username) {
        return userRepository.findById(username).isPresent();
    }
}
