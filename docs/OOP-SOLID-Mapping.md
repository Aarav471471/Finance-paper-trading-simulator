# OOP & SOLID Viva Cheat Sheet

Every concept below points to the exact file to open during the viva.
(All paths are under `src/main/java/com/papertrader/`.)

## 1. The four pillars of OOP

### Abstraction: show *what* something does, hide *how*
| Where | What to say |
|---|---|
| `model/Asset.java` | Abstract class. You can't write `new Asset(...)`. It declares `getType()`, `getRiskLevel()`, `getVolatility()` without a body. |
| `model/Order.java` | Abstract `canExecute(price)`. Each order type fills in its own rule. |
| `interfaces/PriceFeed.java` | The UI and services only know "something that gives prices". They don't know if the prices are live or simulated. |
| `interfaces/QuoteProvider.java` | `LivePriceFeed` asks "give me quotes" without knowing which website answers. |
| `ui/three/Base3DView.java` | Hides all the Swing↔JavaFX bridge work; subclasses only write `createScene()`. |

### Encapsulation: keep data private, change it only through methods that check it
| Where | What to say |
|---|---|
| `model/Account.java` | `balance` is `private`. Only `deposit()` / `withdraw()` change it, and both validate. `withdraw()` throws `InsufficientFundsException`. |
| `model/Asset.java` | `setPrice()` rejects zero/negative prices. |
| `model/Portfolio.java` | `getHoldings()` returns a **copy**, so outside code can't change the internal map. |
| `model/Order.java` | Status only changes via `markFilled()`, `markCancelled()`, `markRejected()`. |
| `model/User.java` | Password stored only as a hash; checked with `checkPassword()`. |

### Inheritance: a child class reuses and extends a parent class (IS-A)
| Parent | Children |
|---|---|
| `Asset` | `Stock`, `Crypto`, `ETF` |
| `Order` | `MarketOrder`, `LimitOrder`, `StopLossOrder`, `TakeProfitOrder` |
| `SimulatorException` (extends `Exception`) | `InsufficientFundsException`, `InvalidOrderException`, `AuthException` |
| `InMemoryUserRepository` | `FileUserRepository` (calls `super.save()` then writes the file) |
| `BasePanel` (extends `JPanel`) | `DashboardPanel`, `MarketPanel`, `TradePanel`, `PortfolioPanel`, `HistoryPanel` |
| `Base3DView` | `Coin3DView`, `Portfolio3DView` |
| `HttpQuoteProvider` (abstract) | `BinanceQuoteProvider`, `FinnhubQuoteProvider`, `YahooQuoteProvider` (all reuse the download method `get(url)`) |
| `DefaultTableCellRenderer` | `MoneyRenderer`, `ColoredNumberRenderer` |

### Polymorphism: one name, many forms

**Compile-time (static) polymorphism = method OVERLOADING.** Same method name, different parameters; the compiler picks.
- `service/TradingService.java`: three `placeOrder(...)` methods:
  - `placeOrder(user, asset, side, qty)` → market order
  - `placeOrder(user, asset, side, qty, limitPrice)` → limit order
  - `placeOrder(user, order)` → any order
- Called from `ui/TradePanel.java` → `placeOrder()`.
- Also: constructors of `SimulatedPriceFeed` are overloaded (with / without a `Random`).

**Runtime (dynamic) polymorphism = method OVERRIDING + parent reference.** Java decides at run time which version runs.
- `TradingService.placeOrder()` and `onPriceUpdate()` call `order.canExecute(price)` on an `Order` reference. The object may be a `MarketOrder`, `LimitOrder` or `StopLossOrder`.
- `SimulatedPriceFeed.tick()` calls `asset.getVolatility()`, so Crypto moves more than ETF.
- `LivePriceFeed` calls `provider.fetchQuotes(...)` on a `QuoteProvider` reference; Binance, Finnhub or Yahoo code runs.
- `TradingService.execute()` calls `order.adjustFillPrice(fill)`: only `LimitOrder` overrides it, so a limit order never fills worse than its limit.
- `BacktestEngine.step()` and `BotService.runBot()` call `strategy.decide(...)` on a `TradingStrategy` reference: SMA, RSI, Buy & Hold or Price Rule code runs.
- `TradingService` calls `executionModel.getFillPrice(...)`: realistic (spread + slippage) in the app, perfect in tests.
- `App` holds a `PriceFeed` reference that is a `LivePriceFeed` or a `SimulatedPriceFeed`; the rest of the app can't tell.
- `MainFrame.refreshCurrentPage()` calls `currentPage.refresh()` on a `BasePanel` reference.
- `LineChartPanel.paintComponent()` overrides Swing's method; Swing calls our version.
- Test that proves it: `OrderTest.sameCallDifferentBehaviour()`.

## 2. Interfaces
| Interface | Implemented by | Why |
|---|---|---|
| `PriceFeed` | `LivePriceFeed`, `SimulatedPriceFeed` | live or offline prices, same code everywhere else |
| `QuoteProvider` | `BinanceQuoteProvider`, `FinnhubQuoteProvider`, `YahooQuoteProvider` (via `HttpQuoteProvider`) | one class per data website |
| `PriceListener` | `MarketService`, `TradingService` | Observer pattern |
| `FeeCalculator` | `PercentageFeeCalculator`, `FlatFeeCalculator` | Strategy pattern |
| `Repository<T, ID>` (generic) | `InMemoryUserRepository`, `FileUserRepository` | storage independence |
| `TransactionRepository` (extends `Repository`) | `InMemoryTransactionRepository`, `FileTransactionRepository` | interface inheritance |
| `Exportable` | `Transaction`, `Holding` | `CsvExporter` works with any of them |
| `ExecutionModel` | `RealisticExecutionModel`, `PerfectExecutionModel` | how orders fill (spread, slippage) |
| `TradingStrategy` | `SmaCrossoverStrategy`, `RsiStrategy`, `BuyAndHoldStrategy`, `PriceRuleStrategy` | one interface reused by backtests AND bots |
| `HistoryProvider` | `BinanceQuoteProvider`, `YahooQuoteProvider`, `SimulatedHistoryProvider` | candles for charts/backtests. Binance and Yahoo implement **two interfaces** (`QuoteProvider` + `HistoryProvider`); Finnhub implements only one (ISP) |
| `EquityRepository` | `InMemoryEquityRepository`, `FileEquityRepository` | equity curve storage |
| `Refreshable` | `BasePanel` | every page can refresh itself |
| `SnapshotProvider` | `YahooQuoteProvider`, `BinanceQuoteProvider` | day / 52-week stats for any asset |
| `FundamentalsProvider` | `YahooFundamentalsProvider` | company data and news, swappable for another source |

## 3. SOLID principles
| Principle | Where it shows |
|---|---|
| **S** Single Responsibility | `AuthService` = login only; `TradingService` = orders only; `PortfolioService` = calculations only; `MarketService` = assets & history only; `Portfolio` stores units but does no money maths. |
| **O** Open/Closed | Real market data was added as **new classes only** (`LivePriceFeed`, providers); `MarketService`, `TradingService` and the pages didn't change. Add `TrailingStopOrder extends Order` or `class Bond extends Asset` without editing `TradingService`. Change fee rule with one line in `App.java`. |
| **L** Liskov Substitution | `InMemoryUserRepository` and `FileUserRepository` are interchangeable (tests use one, the app uses the other). Any `Order` subclass works wherever `Order` is expected. |
| **I** Interface Segregation | Small interfaces: `PriceListener` has 1 method, `Exportable` has 1, `FeeCalculator` has 2. `TransactionRepository` adds extra methods only where needed instead of bloating `Repository`. |
| **D** Dependency Inversion | `LivePriceFeed` depends on `QuoteProvider`, so `LivePriceFeedTest` uses a fake provider without internet. `TradingService` and `AuthService` depend on interfaces, received through the constructor. Concrete classes are created only in `App.java` (the composition root). |

## 4. Cohesion and coupling
- **High cohesion:** each package has one theme (`model`, `service`, `repository`, `fees`, `feed`, `ui`, `ui/three`, `ui/components`), and each class does one job.
- **Low coupling:**
  - The UI only talks to services, never to files or the price feed.
  - Services talk to interfaces, not concrete classes.
  - `LoginPanel` gets a `Runnable onLoginSuccess` instead of a reference to `MainFrame`.
  - The Observer pattern means the price feed doesn't know who is listening.
  - Proof: `ConsoleDemo.java` runs the full engine with **no UI at all**.

## 5. Design patterns used (bonus points)
| Pattern | Where |
|---|---|
| Observer | `PriceFeed` → `PriceListener` |
| Strategy | `FeeCalculator` |
| Repository | `Repository<T, ID>` |
| Template Method | `Base3DView.start3D()` + abstract `createScene()` |
| Composition / fallback | `LivePriceFeed` HAS-A `SimulatedPriceFeed` for assets no API covers |
| Strategy (again) | `TradingStrategy`, `ExecutionModel` |
| Factory method | `StrategyPicker.createStrategy()` builds the chosen strategy object |
| Parameter object | `AppContext` record carries all services to `MainFrame` |
| Composition root / Dependency Injection | `App.start()` |

## 6. Likely viva questions
- **Abstract class vs interface?** `Asset` and `Order` share fields and code, so they're abstract classes. `FeeCalculator` and `PriceFeed` only describe behaviour, so they're interfaces. A class can implement many interfaces but extend only one class.
- **Why is `placeOrder` overloaded and not overridden?** Overloading = same class, different parameters (compile time). Overriding = subclass replaces a parent method (run time), like `canExecute`.
- **How does a limit order execute later?** `TradingService` is a `PriceListener`. Every tick, `onPriceUpdate()` checks waiting orders with `canExecute(newPrice)`.
- **Why a Swing Timer for simulated prices?** It runs on the UI thread, so the UI and engine never run at the same time. No locks needed.
- **Live prices come from the internet. Doesn't that freeze the UI?** No. `LivePriceFeed` downloads on a background thread (`ScheduledExecutorService`), then hands results to the UI thread with `SwingUtilities.invokeLater`. So listeners still run on the UI thread, like with the simulator.
- **Which API and why?** Crypto: Binance, the exchange itself, so it's the actual traded price. Stocks/ETFs: Finnhub, an official real-time US exchange feed (free key), or Yahoo Finance when there's no key. Each website is one `QuoteProvider` class.
- **How do you stop a backtest from cheating (look-ahead bias)?** `BacktestEngine.step()` passes `candles.subList(0, index + 1)` to the strategy, so it only ever sees the past.
- **What's the difference between Sharpe and Sortino?** Sharpe divides the average return by the volatility of ALL returns. Sortino only counts the volatility of NEGATIVE returns, because upside moves aren't really "risk". Both use daily returns × √(days per year).
- **What is max drawdown?** The biggest fall from a peak to a later low: `(peak − value) / peak`, the worst value seen.
- **Profit factor?** Total money won ÷ total money lost. Above 1 means the strategy makes money overall.
- **Why does a buy cost more than the last price?** `RealisticExecutionModel`: buyers pay the ask (half a spread above), plus random slippage and a size-based market impact. A limit order is protected by `LimitOrder.adjustFillPrice()`.
- **Realized vs unrealized P&L?** Realized = profit locked in by a sale: `(sell price − average cost) × units − fee`, stored on the SELL transaction. Unrealized = paper profit on holdings you still own.
- **What if the internet drops?** The last price is kept, the sidebar shows "offline", and it retries on the next refresh. With no internet at start-up, the app uses the simulator.
- **How does 3D work in a Swing app?** `JFXPanel` is a Swing component that hosts a JavaFX scene. JavaFX 3D shapes (`Box`, `Cylinder`), `PhongMaterial`, `PointLight` and `PerspectiveCamera` draw the 3D view.
- **How is data saved?** CSV files in `data/`. `FileUserRepository` extends the in-memory repository and writes files after each save.
