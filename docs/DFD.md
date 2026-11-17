# Data Flow Diagrams (DFD)

These diagrams show how data moves through PaperTrader 3D: who sends data in, what each process does with it,
and where it is stored. They are drawn from the real code (`App.java` wiring, the `service/`, `feed/`,
`backtest/`, `bot/`, `analytics/` and `repository/` packages).

**Notation** (Yourdon/DeMarco style)

| Symbol | Meaning |
|---|---|
| Yellow rectangle | External entity (outside the system) |
| Blue circle | Process |
| Green cylinder | Data store |
| Labelled arrow | Data flow |

The GitHub view below renders the Mermaid source directly. Static images are in [`docs/diagrams/`](diagrams/)
(PNG and SVG), and the sources are in [`docs/diagrams/src/`](diagrams/src/).

## Level 0: Context diagram

The whole simulator is one process. It talks to the trader, the live market data APIs, the configuration
(API keys, data mode) and the local file system (CSV export).

```mermaid
flowchart LR
    classDef ext fill:#fff3cd,stroke:#8a6d00,stroke-width:2px,color:#000
    classDef proc fill:#d6eaff,stroke:#1f5fa8,stroke-width:2px,color:#000

    T["TRADER<br/>(registered user)"]:::ext
    MD["MARKET DATA PROVIDERS<br/>Binance · Groww · Finnhub<br/>Yahoo Finance · CoinGecko"]:::ext
    CFG["SYSTEM CONFIGURATION<br/>config.properties / env vars"]:::ext
    FS["LOCAL FILE SYSTEM<br/>(exported CSV)"]:::ext

    P0(("0<br/>PaperTrader 3D<br/>Paper Trading<br/>Simulator")):::proc

    T -- "login / register details" --> P0
    T -- "order requests (asset, side, qty, type, price)" --> P0
    T -- "backtest settings, bot rules, chart range" --> P0
    P0 -- "auth result, order confirmations / rejections" --> T
    P0 -- "portfolio, P&L, charts, order book" --> T
    P0 -- "risk analytics, backtest results, leaderboard" --> T

    P0 -- "quote / history / fundamentals requests" --> MD
    MD -- "live quotes, historical candles" --> P0
    MD -- "fundamentals, news, market cap, indices" --> P0

    CFG -- "API keys, data mode, data folder" --> P0
    P0 -- "transaction history CSV" --> FS
```

PNG: [dfd-level0.png](diagrams/dfd-level0.png) · SVG: [dfd-level0.svg](diagrams/dfd-level0.svg)

## Level 1: Main processes

| # | Process | Main classes |
|---|---|---|
| 1.0 | Authenticate User | `AuthService`, `PasswordHasher`, `FileUserRepository` |
| 2.0 | Acquire Market Data | `LivePriceFeed` / `SimulatedPriceFeed`, `*QuoteProvider`, `MarketService`, `MarketDataService` |
| 3.0 | Process Orders | `TradingService`, `ExecutionModel`, `FeeCalculator`, `Order` subclasses |
| 4.0 | Value Portfolio | `PortfolioService`, `Portfolio`, `Holding`, `Account` |
| 5.0 | Build Charts & Indicators | `ChartService`, `HistoryProvider`s, `TechnicalAnalysis`, `Indicators` |
| 6.0 | Run Backtest | `BacktestEngine`, `TradingStrategy` implementations |
| 7.0 | Run Trading Bots | `BotService`, `TradingBot` |
| 8.0 | Analyse Performance | `AnalyticsService`, `PerformanceAnalyzer`, `TradeStats`, `FileEquityRepository` |
| 9.0 | Rank Leaderboard | `LeaderboardService` |
| 10.0 | Export History | `CsvExporter` (from the History page) |

| Store | Contents | Class |
|---|---|---|
| D1 `users.csv` | username, password hash, cash balance | `FileUserRepository` |
| D2 `holdings.csv` | username, symbol, quantity, average cost | `FileUserRepository` |
| D3 `transactions.csv` | append-only trade ledger | `FileTransactionRepository` |
| D4 `equity.csv` | net-worth snapshots over time | `FileEquityRepository` |
| D5 pending orders | Limit / Stop-Loss / Take-Profit orders waiting for their price (in memory) | `TradingService.pendingOrders` |

```mermaid
flowchart LR
    classDef ext fill:#fff3cd,stroke:#8a6d00,stroke-width:2px,color:#000
    classDef proc fill:#d6eaff,stroke:#1f5fa8,stroke-width:2px,color:#000
    classDef store fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px,color:#000

    T["TRADER"]:::ext
    MD["MARKET DATA<br/>PROVIDERS"]:::ext
    CFG["SYSTEM<br/>CONFIGURATION"]:::ext
    FS["LOCAL FILE<br/>SYSTEM"]:::ext

    P1(("1.0<br/>Authenticate<br/>User")):::proc
    P2(("2.0<br/>Acquire<br/>Market Data")):::proc
    P3(("3.0<br/>Process<br/>Orders")):::proc
    P4(("4.0<br/>Value<br/>Portfolio")):::proc
    P5(("5.0<br/>Build Charts &<br/>Indicators")):::proc
    P6(("6.0<br/>Run<br/>Backtest")):::proc
    P7(("7.0<br/>Run Trading<br/>Bots")):::proc
    P8(("8.0<br/>Analyse<br/>Performance")):::proc
    P9(("9.0<br/>Rank<br/>Leaderboard")):::proc
    P10(("10.0<br/>Export<br/>History")):::proc

    D1[("D1 users.csv")]:::store
    D2[("D2 holdings.csv")]:::store
    D3[("D3 transactions.csv")]:::store
    D4[("D4 equity.csv")]:::store
    D5[("D5 pending orders<br/>(in memory)")]:::store

    %% 1.0 Authenticate
    T -- "username, password" --> P1
    P1 -- "session / auth error" --> T
    D1 -- "stored user, password hash" --> P1
    P1 -- "new user, starting cash" --> D1

    %% 2.0 Market data
    CFG -- "API keys, data mode" --> P2
    P2 -- "symbol requests" --> MD
    MD -- "live quotes, fundamentals, news" --> P2
    P2 -- "prices, snapshots, fundamentals, news" --> T

    %% 3.0 Orders
    T -- "order request / cancel / reset" --> P3
    P2 -- "price update" --> P3
    D2 -- "owned quantity, avg cost" --> P3
    P3 -- "updated holdings" --> D2
    P3 -- "updated cash balance" --> D1
    P3 -- "transaction record" --> D3
    P3 <-- "waiting orders" --> D5
    P3 -- "fill confirmation / rejection" --> T

    %% 4.0 Portfolio
    D1 -- "cash balance" --> P4
    D2 -- "holdings" --> P4
    P2 -- "current prices" --> P4
    P4 -- "market value, P&L, net worth" --> T

    %% 5.0 Charts
    T -- "asset, chart range" --> P5
    MD -- "historical candles" --> P5
    P2 -- "latest price" --> P5
    P5 -- "candles, SMA/EMA/RSI/MACD/VWAP" --> T

    %% 6.0 Backtest
    T -- "asset, strategy, replay speed" --> P6
    P5 -- "historical candles" --> P6
    P6 -- "equity curve, trades, buy/sell markers" --> T

    %% 7.0 Bots
    T -- "bot asset, strategy / rule, qty" --> P7
    P5 -- "recent candles" --> P7
    P7 -- "bot buy / sell orders" --> P3
    P7 -- "bot activity log" --> T

    %% 8.0 Analytics
    P4 -- "net worth snapshot" --> P8
    P8 -- "equity point" --> D4
    D4 -- "equity curve" --> P8
    D3 -- "closed trades, realised P&L" --> P8
    P8 -- "Sharpe, Sortino, drawdown, win rate, profit factor" --> T

    %% 9.0 Leaderboard
    D1 -- "all users" --> P9
    P4 -- "net worth per user" --> P9
    D3 -- "realised P&L, trade count" --> P9
    P9 -- "ranking" --> T

    %% 10.0 Export
    T -- "export request, file name" --> P10
    D3 -- "transactions" --> P10
    P10 -- "CSV file" --> FS
```

PNG: [dfd-level1.png](diagrams/dfd-level1.png) · SVG: [dfd-level1.svg](diagrams/dfd-level1.svg)

## Level 2: Process 3.0 Process Orders

Process 3.0 is the core of the simulator, so it is exploded one level further. It follows
`TradingService.placeOrder()`, `execute()` and `onPriceUpdate()`.

- **3.1 Validate Order**: a sell can't exceed owned units minus units already queued for sale.
- **3.2 Check Trigger Price**: `Order.canExecute(price)` (runtime polymorphism over Market / Limit / SL-M / TP). Not yet triggered orders go to D5.
- **3.3 Determine Fill Price**: `ExecutionModel.getFillPrice()` (bid/ask spread + slippage), then `Order.adjustFillPrice()` so a limit order never fills worse than its limit.
- **3.4 Calculate Fee**: `FeeCalculator.calculateFee()` (0.1% by default).
- **3.5 Settle Cash & Holdings**: `Account.withdraw/deposit`, `Portfolio.addUnits/removeUnits`, realised P&L from average cost.
- **3.6 Record Transaction**: `TransactionRepository.save()`.
- **3.7 Monitor Pending Orders**: on every price update (Observer pattern) waiting orders are re-checked and filled.

```mermaid
flowchart LR
    classDef ext fill:#fff3cd,stroke:#8a6d00,stroke-width:2px,color:#000
    classDef proc fill:#d6eaff,stroke:#1f5fa8,stroke-width:2px,color:#000
    classDef store fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px,color:#000
    classDef other fill:#f3e5f5,stroke:#6a1b9a,stroke-width:2px,color:#000

    T["TRADER"]:::ext
    P2(("2.0<br/>Acquire<br/>Market Data")):::other
    P7(("7.0<br/>Run Trading<br/>Bots")):::other

    P31(("3.1<br/>Validate<br/>Order")):::proc
    P32(("3.2<br/>Check<br/>Trigger Price")):::proc
    P33(("3.3<br/>Determine<br/>Fill Price")):::proc
    P34(("3.4<br/>Calculate<br/>Fee")):::proc
    P35(("3.5<br/>Settle Cash &<br/>Holdings")):::proc
    P36(("3.6<br/>Record<br/>Transaction")):::proc
    P37(("3.7<br/>Monitor<br/>Pending Orders")):::proc

    D1[("D1 users.csv")]:::store
    D2[("D2 holdings.csv")]:::store
    D3[("D3 transactions.csv")]:::store
    D5[("D5 pending orders")]:::store

    T -- "order (Market / Limit / SL-M / TP)" --> P31
    P7 -- "bot order" --> P31
    D2 -- "owned quantity" --> P31
    D5 -- "queued sell quantity" --> P31
    P31 -- "invalid order error" --> T
    P31 -- "valid order" --> P32

    P32 -- "order not yet triggered" --> D5
    P32 -- "executable order" --> P33

    P2 -- "price update" --> P37
    D5 -- "waiting orders" --> P37
    P37 -- "triggered order" --> P33

    P33 -- "fill price (bid/ask + slippage, limit-adjusted)" --> P34
    P34 -- "trade value, fee" --> P35
    D2 -- "average cost" --> P35
    P35 -- "cash withdrawn / deposited" --> D1
    P35 -- "units added / removed" --> D2
    P35 -- "insufficient funds error" --> T
    P35 -- "filled order, realised P&L" --> P36
    P36 -- "transaction record" --> D3
    P36 -- "fill confirmation" --> T
```

PNG: [dfd-level2-orders.png](diagrams/dfd-level2-orders.png) · SVG: [dfd-level2-orders.svg](diagrams/dfd-level2-orders.svg)

## Balancing check

Every flow on the Level 0 diagram appears on Level 1: trader inputs go to 1.0, 3.0, 5.0, 6.0, 7.0 and 10.0;
provider data enters through 2.0 and 5.0; configuration enters through 2.0; the CSV leaves through 10.0.
Level 2 keeps the same inputs and outputs as process 3.0 on Level 1 (order request, bot orders, price update,
D1/D2/D3/D5, confirmation or rejection).
