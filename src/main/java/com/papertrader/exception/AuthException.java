package com.papertrader.exception;

/** Thrown for login / registration problems. */
public class AuthException extends SimulatorException {
    public AuthException(String message) {
        super(message);
    }
}
