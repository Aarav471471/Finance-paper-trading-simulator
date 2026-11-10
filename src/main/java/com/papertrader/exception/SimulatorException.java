package com.papertrader.exception;

/** Parent of all expected errors in the simulator. The UI catches this and shows the message. */
public class SimulatorException extends Exception {
    public SimulatorException(String message) {
        super(message);
    }
}
