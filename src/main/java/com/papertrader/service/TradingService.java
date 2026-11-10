package com.papertrader.service;

import com.papertrader.exception.InvalidOrderException;
import com.papertrader.execution.ExecutionModel;
import com.papertrader.execution.PerfectExecutionModel;
import com.papertrader.exception.SimulatorException;
import com.papertrader.interfaces.FeeCalculator;
import com.papertrader.interfaces.PriceListener;
import com.papertrader.interfaces.Repository;
import com.papertrader.interfaces.TransactionRepository;
import com.papertrader.model.Asset;
import com.papertrader.model.LimitOrder;
import com.papertrader.model.MarketOrder;
import com.papertrader.model.Order;
import com.papertrader.model.OrderSide;
import com.papertrader.model.Transaction;
import com.papertrader.model.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The trading engine. SINGLE RESPONSIBILITY: accept orders and execute them.
 *
 * DEPENDENCY INVERSION: every dependency is an interface (FeeCalculator, Repository,
 * TransactionRepository) passed in through the constructor.
 *
 * COMPILE-TIME POLYMORPHISM: placeOrder() is OVERLOADED three times. The compiler picks
 * the version from the arguments you pass.
 *
 * RUNTIME POLYMORPHISM: order.canExecute(price) runs the subclass's own rule.
 */
public class TradingService implements PriceListener {

    private final FeeCalculator feeCalculator;
    private final ExecutionModel executionModel;
    private final Repository<User, String> userRepository;
    private final TransactionRepository transactionRepository;

    /** Orders waiting for their price condition, remembered with the user who placed them. */
    private final Map<Order, User> pendingOrders = new LinkedHashMap<>();

    /** OVERLOADED constructor: no execution model given, so fills are perfect (handy for tests). */
    public TradingService(FeeCalculator feeCalculator, Repository<User, String> userRepository,
                          TransactionRepository transactionRepository) {
        this(feeCalculator, new PerfectExecutionModel(), userRepository, transactionRepository);
    }

    public TradingService(FeeCalculator feeCalculator, ExecutionModel executionModel,
                          Repository<User, String> userRepository, TransactionRepository transactionRepository) {
        this.feeCalculator = feeCalculator;
        this.executionModel = executionModel;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    // ------------------------------------------------------------------
    // Overloaded placeOrder methods (compile-time polymorphism)
    // ------------------------------------------------------------------

    /** Overload 1: a market order. */
    public Order placeOrder(User user, Asset asset, OrderSide side, int quantity) throws SimulatorException {
        return placeOrder(user, new MarketOrder(asset, side, quantity));
    }

    /** Overload 2: a limit order (one extra argument: the limit price). */
    public Order placeOrder(User user, Asset asset, OrderSide side, int quantity, double limitPrice)
            throws SimulatorException {
        return placeOrder(user, new LimitOrder(asset, side, quantity, limitPrice));
    }

    /** Overload 3: any ready-made order (Market, Limit, Stop-Loss or a future type). */
    public Order placeOrder(User user, Order order) throws SimulatorException {
        if (order.getSide() == OrderSide.SELL) {
            int owned = user.getPortfolio().getQuantity(order.getAsset().getSymbol());
            int alreadyQueued = queuedSellQuantity(user, order.getAsset());
            if (order.getQuantity() > owned - alreadyQueued) {
                throw new InvalidOrderException("You can only sell " + Math.max(0, owned - alreadyQueued)
                        + " unit(s) of " + order.getAsset().getSymbol());
            }
        }

        // Runtime polymorphism: which canExecute() runs depends on the real order type.
        if (order.canExecute(order.getAsset().getPrice())) {
            execute(user, order);
        } else {
            pendingOrders.put(order, user);
        }
        return order;
    }

    // ------------------------------------------------------------------

    public double calculateFee(double tradeValue) {
        return feeCalculator.calculateFee(tradeValue);
    }

    public String getFeeDescription() {
        return feeCalculator.getDescription();
    }

    public ExecutionModel getExecutionModel() {
        return executionModel;
    }

    /** Total realised profit/loss = sum of the P&L locked in by every SELL. */
    public double getRealizedProfitLoss(User user) {
        double total = 0;
        for (Transaction t : getTransactionHistory(user)) {
            total += t.getRealizedPnl();
        }
        return total;
    }

    /**
     * Start again: cancel waiting orders, sell nothing, just wipe holdings and history
     * and give back the starting cash.
     */
    public void resetAccount(User user, double startingCash) {
        for (Order order : getPendingOrders(user)) {
            cancelOrder(order);
        }
        user.getPortfolio().clear();
        user.getAccount().resetTo(startingCash);
        transactionRepository.deleteByUsername(user.getUsername());
        userRepository.save(user);
    }

    /** Moves the cash and units, records the transaction and saves the user. */
    private void execute(User user, Order order) throws SimulatorException {
        Asset asset = order.getAsset();
        // 1. the market decides the real fill price (spread + slippage) ...
        double marketFill = executionModel.getFillPrice(asset, order.getSide(), order.getQuantity());
        // 2. ... and the order type may improve it (a limit order never fills worse than its limit)
        double price = Math.round(order.adjustFillPrice(marketFill) * 10000) / 10000.0;
        double value = price * order.getQuantity();
        double fee = feeCalculator.calculateFee(value);
        double realizedPnl = 0;

        if (order.getSide() == OrderSide.BUY) {
            user.getAccount().withdraw(value + fee);   // throws if not enough cash
            user.getPortfolio().addUnits(asset.getSymbol(), order.getQuantity(), price);
        } else {
            if (user.getPortfolio().getQuantity(asset.getSymbol()) < order.getQuantity()) {
                throw new InvalidOrderException("Not enough " + asset.getSymbol() + " to sell");
            }
            double averageCost = averageCostOf(user, asset.getSymbol());
            realizedPnl = (price - averageCost) * order.getQuantity() - fee;   // profit locked in by this sale
            user.getPortfolio().removeUnits(asset.getSymbol(), order.getQuantity());
            double proceeds = value - fee;
            if (proceeds > 0) {
                user.getAccount().deposit(proceeds);
            }
        }

        order.markFilled(price);
        transactionRepository.save(new Transaction(transactionRepository.nextId(), user.getUsername(),
                LocalDateTime.now(), asset.getSymbol(), order.getSide(), order.getOrderType(),
                order.getQuantity(), price, fee, realizedPnl));
        userRepository.save(user);
    }

    private double averageCostOf(User user, String symbol) {
        for (com.papertrader.model.Holding holding : user.getPortfolio().getHoldings()) {
            if (holding.getSymbol().equals(symbol)) {
                return holding.getAverageCost();
            }
        }
        return 0;
    }

    /** Called by the price feed on every price change: fill any pending orders that are now allowed. */
    @Override
    public void onPriceUpdate(Asset asset, double oldPrice, double newPrice) {
        Iterator<Map.Entry<Order, User>> it = pendingOrders.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Order, User> entry = it.next();
            Order order = entry.getKey();
            if (order.getAsset() == asset && order.canExecute(newPrice)) {
                it.remove();
                try {
                    execute(entry.getValue(), order);
                } catch (SimulatorException e) {
                    order.markRejected(e.getMessage());
                }
            }
        }
    }

    public List<Order> getPendingOrders(User user) {
        List<Order> result = new ArrayList<>();
        for (Map.Entry<Order, User> entry : pendingOrders.entrySet()) {
            if (entry.getValue() == user) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public void cancelOrder(Order order) {
        if (pendingOrders.remove(order) != null) {
            order.markCancelled();
        }
    }

    public List<Transaction> getTransactionHistory(User user) {
        return transactionRepository.findByUsername(user.getUsername());
    }

    private int queuedSellQuantity(User user, Asset asset) {
        int total = 0;
        for (Order order : getPendingOrders(user)) {
            if (order.getSide() == OrderSide.SELL && order.getAsset() == asset) {
                total += order.getQuantity();
            }
        }
        return total;
    }
}
