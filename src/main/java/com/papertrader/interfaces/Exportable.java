package com.papertrader.interfaces;

/** Anything that can be written as one line of a CSV file. */
public interface Exportable {
    String toCsvRow();
}
