package com.papertrader.model;

/** How risky an asset is. Each Asset subclass returns its own level (runtime polymorphism). */
public enum RiskLevel {
    LOW, MEDIUM, HIGH
}
