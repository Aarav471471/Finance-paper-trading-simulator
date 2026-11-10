# SDLC: Spiral Model

We built PaperTrader 3D with the **Spiral model** (Boehm, 1988). The project went around the
spiral five times. Each loop has the same four quadrants:

1. **Determine objectives** (planning): what this loop must deliver
2. **Identify and resolve risks** (risk analysis): what could go wrong, and a prototype or test to reduce it
3. **Develop and test** (engineering): build that increment
4. **Plan the next iteration** (evaluation): review with the team and the "customer" (faculty)

The spiral was a good fit because the riskiest parts (3D inside Swing, live updates, money
calculations) could be tried early in small prototypes before the full app was built on them.

```
                     Planning  |  Risk analysis
                               |
          Loop 5 -----------------------------------------.
          Loop 4 ----------------------------------.      |
          Loop 3 ---------------------------.      |      |
          Loop 2 --------------------.      |      |      |
          Loop 1 -------------.      |      |      |      |
                              *      |      |      |      |
                     ---------+------+------+------+------+-->  cost / time
                              |
                    Evaluation | Engineering
```

---

## Loop 1: Concept and requirements

| Quadrant | Work done |
|---|---|
| Objectives | Understand the paper-trading domain, choose the requirements method, agree scope. |
| Risks | *Scope creep* (real APIs, options, margin). *Unclear requirements.* *Team only knows Java.* |
| Risk resolution | Chose **FODA** to list features as mandatory / optional / alternative, and fixed the scope. Decided on all-Java (Swing + JavaFX) so the whole team can explain the code. Simulated prices remove the dependency on an external API. |
| Develop | [SRS.md](SRS.md) with the FODA feature model; first draft of the class diagram; **console prototype** of the core classes (`ConsoleDemo.java`). |
| Evaluate | Prototype showed buying, a stop-loss triggering and prices moving with no UI. Approved to build the real engine. |

## Loop 2: Core trading engine

| Quadrant | Work done |
|---|---|
| Objectives | Asset hierarchy, order hierarchy, account, portfolio, price feed, fees, trading service, storage. |
| Risks | *Wrong money maths* (average cost, fees, P&L). *Orders executing at the wrong time.* *Selling units you don't own.* *Data lost on exit.* |
| Risk resolution | Wrote **26 JUnit tests** (`src/test`) for every rule. Used interfaces (`Repository`, `FeeCalculator`) so tests run against in-memory versions. |
| Develop | `model`, `interfaces`, `feed`, `fees`, `repository`, `service` packages. |
| Evaluate | All tests green. Class and sequence diagrams updated to match the code. |

## Loop 3: Swing user interface

| Quadrant | Work done |
|---|---|
| Objectives | Login, Dashboard, Market, Trade, Portfolio and History screens with live data. |
| Risks | *UI freezing* or threading bugs between the price timer and the UI. *Tables losing the selected row every second.* *Ugly default Swing look.* |
| Risk resolution | Price ticks use a **Swing `Timer`**, so they run on the UI thread (no locking). Tables use `fireTableRowsUpdated` to keep selection. **FlatLaf** dark theme. |
| Develop | `ui` package: `MainFrame`, `BasePanel` + 5 pages, `StatCard`, `LineChartPanel`, `TickerPanel`, renderers. |
| Evaluate | Usability check: a new user placed a first trade in under 2 minutes (NFR-2). |

## Loop 4: 3D, persistence and documentation

| Quadrant | Work done |
|---|---|
| Objectives | 3D portfolio chart and 3D login coin, saving to disk, all UML diagrams, final testing. |
| Risks | *3D not working on some laptops' graphics drivers.* *JavaFX and Swing threads mixing.* |
| Risk resolution | `Base3DView` checks `ConditionalFeature.SCENE3D` and shows a message instead of crashing. All JavaFX work goes through `Platform.runLater`. |
| Develop | `ui/three` package, `FileUserRepository`, `FileTransactionRepository`, 4 UML diagrams, this document. |
| Evaluate | Demo; screenshots in [screenshots/](screenshots/). Customer feedback: "use real market data". That became loop 5. |

## Loop 5: Real market data

| Quadrant | Work done |
|---|---|
| Objectives | Replace simulated prices with the most accurate free real-time sources. |
| Risks | *Inaccurate or delayed data.* *API keys leaking into Git.* *Rate limits (HTTP 429).* *Slow or no internet freezing the UI.* *An API changing or going down.* |
| Risk resolution | Compared sources and picked **Binance** for crypto (exchange's own prices, no key) and **Finnhub** for stocks/ETFs (official real-time US quotes, free key), with **Yahoo Finance** as a no-key option. Key read from env var or git-ignored `config.properties`. Refresh rates kept under free limits (3 s / 15 s). Downloads run on a background thread and results go back to the UI thread. If an API fails, the last price is kept; if none answer at start-up, the app uses the simulator. JSON parsing is unit-tested with sample answers and the feed is tested with a fake provider. |
| Develop | `QuoteProvider` interface, `HttpQuoteProvider` + 3 providers, `LivePriceFeed`, `AppConfig`, sidebar data-status label. **No changes were needed in `MarketService`, `TradingService` or the pages**, because they only depend on the `PriceFeed` interface (Open/Closed principle proven in practice). |
| Evaluate | 32 tests green. Future loop ideas: database storage (new `Repository`), trailing-stop order (new `Order` subclass), Indian NSE stocks (new `QuoteProvider`). |
