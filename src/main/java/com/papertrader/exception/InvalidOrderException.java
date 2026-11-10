package com.papertrader.exception;

/** Thrown when an order breaks a rule, e.g. selling units you do not own. */
public class InvalidOrderException extends SimulatorException {
    public InvalidOrderException(String message) {
        super(message);
    }
}
