package com.papertrader.exception;

import com.papertrader.util.Formatter;

/** Thrown when a user tries to spend more cash than they have. */
public class InsufficientFundsException extends SimulatorException {
    public InsufficientFundsException(double needed, double available) {
        super("Not enough cash. Needed " + Formatter.money(needed) + " but only "
                + Formatter.money(available) + " is available.");
    }
}
