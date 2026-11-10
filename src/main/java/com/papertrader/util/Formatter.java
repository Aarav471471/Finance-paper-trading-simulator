package com.papertrader.util;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Turns numbers and dates into display text, so formatting is consistent everywhere. */
public final class Formatter {

    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss");

    private Formatter() {
    }

    public static String money(double amount) {
        return MONEY.format(amount);
    }

    /** e.g. +1.25% or -0.40% */
    public static String percent(double value) {
        return String.format("%+.2f%%", value);
    }

    public static String signedMoney(double amount) {
        return (amount >= 0 ? "+" : "-") + money(Math.abs(amount));
    }

    public static String dateTime(LocalDateTime time) {
        return time.format(DATE_TIME);
    }
}
