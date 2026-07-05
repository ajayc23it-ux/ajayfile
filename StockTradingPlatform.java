import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.TreeMap;

/**
 * Stock Trading Platform Simulator (single-file version)
 * ========================================================
 * Compile: javac StockTradingPlatform.java
 * Run    : java StockTradingPlatform
 *
 * Demonstrates OOP design: Stock, Market, Transaction, PortfolioSnapshot,
 * Portfolio, User, and Persistence (plain-text file I/O) all working
 * together behind a simple console menu.
 */
public class StockTradingPlatform {

    // ======================================================================
    // Stock
    // ======================================================================
    static class Stock {
        private final String symbol;
        private final String name;
        private double price;
        private final double volatility;
        private final List<Double> history = new ArrayList<>();
        private static final Random RANDOM = new Random();

        Stock(String symbol, String name, double price, double volatility) {
            this.symbol = symbol.toUpperCase();
            this.name = name;
            this.price = round2(price);
            this.volatility = volatility;
            this.history.add(this.price);
        }

        void updatePrice() {
            double pctChange = (RANDOM.nextDouble() * 2 - 1) * volatility;
            double newPrice = price * (1 + pctChange);
            price = Math.max(0.01, round2(newPrice));
            history.add(price);
            if (history.size() > 500) history.remove(0);
        }

        double dayChange() {
            if (history.size() < 2) return 0.0;
            double prev = history.get(history.size() - 2);
            if (prev == 0) return 0.0;
            return round2((price - prev) / prev * 100);
        }

        private static double round2(double v) {
            return Math.round(v * 100.0) / 100.0;
        }

        String getSymbol() { return symbol; }
        String getName() { return name; }
        double getPrice() { return price; }
    }

    // ======================================================================
    // Market
    // ======================================================================
    static class Market {
        private final Map<String, Stock> stocks = new LinkedHashMap<>();

        void addStock(Stock stock) { stocks.put(stock.getSymbol(), stock); }

        Stock getStock(String symbol) {
            return symbol == null ? null : stocks.get(symbol.toUpperCase());
        }

        void tick() {
            for (Stock s : stocks.values()) s.updatePrice();
        }

        void display() {
            System.out.println("\n" + "=".repeat(60));
            System.out.printf("%-8s%-22s%12s%14s%n", "SYMBOL", "NAME", "PRICE", "CHANGE");
            System.out.println("-".repeat(60));
            for (Stock s : new TreeMap<>(stocks).values()) {
                double change = s.dayChange();
                String arrow = change > 0 ? "^" : (change < 0 ? "v" : "-");
                String name = s.getName().length() > 20 ? s.getName().substring(0, 20) : s.getName();
                System.out.printf("%-8s%-22s%12s%14s%n",
                        s.getSymbol(), name,
                        String.format("$%.2f", s.getPrice()),
                        String.format("%s %.2f%%", arrow, change));
            }
            System.out.println("=".repeat(60));
        }

        static Market defaultMarket() {
            Market market = new Market();
            Object[][] seed = {
                    {"TCOR", "TechCorp Industries", 152.30, 0.03},
                    {"GLBX", "GlobalX Logistics", 48.75, 0.02},
                    {"NVAI", "NovaAI Systems", 310.10, 0.045},
                    {"BNKR", "Bankers Trust Co", 89.40, 0.015},
                    {"ENRG", "Enerway Renewables", 22.60, 0.035},
                    {"MEDH", "MediHealth Group", 64.85, 0.02},
                    {"RETL", "Retail United", 37.20, 0.025},
                    {"AERO", "AeroSpace Dynamics", 210.00, 0.04},
            };
            for (Object[] row : seed) {
                market.addStock(new Stock((String) row[0], (String) row[1],
                        (Double) row[2], (Double) row[3]));
            }
            return market;
        }
    }

    // ======================================================================
    // Transaction
    // ======================================================================
    static final class Transaction {
        enum Action { BUY, SELL }

        private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        private final String timestamp;
        private final Action action;
        private final String symbol;
        private final int quantity;
        private final double price;
        private final double total;

        Transaction(String timestamp, Action action, String symbol, int quantity, double price) {
            this.timestamp = timestamp;
            this.action = action;
            this.symbol = symbol;
            this.quantity = quantity;
            this.price = price;
            this.total = Math.round(price * quantity * 100.0) / 100.0;
        }

        static Transaction create(Action action, String symbol, int quantity, double price) {
            return new Transaction(LocalDateTime.now().format(FMT), action, symbol, quantity, price);
        }

        String getTimestamp() { return timestamp; }

        String toCsvLine() {
            return String.join(",", timestamp, action.name(), symbol,
                    String.valueOf(quantity), String.valueOf(price), String.valueOf(total));
        }

        static Transaction fromCsvLine(String line) {
            String[] p = line.split(",");
            return new Transaction(p[0], Action.valueOf(p[1]), p[2], Integer.parseInt(p[3]), Double.parseDouble(p[4]));
        }

        @Override
        public String toString() {
            return String.format("[%s] %-4s %5d x %-6s @ $%8.2f  = $%10.2f",
                    timestamp, action, quantity, symbol, price, total);
        }
    }

    // ======================================================================
    // PortfolioSnapshot
    // ======================================================================
    static final class PortfolioSnapshot {
        private final String timestamp;
        private final double cash;
        private final double holdingsValue;
        private final double totalValue;

        PortfolioSnapshot(String timestamp, double cash, double holdingsValue, double totalValue) {
            this.timestamp = timestamp;
            this.cash = cash;
            this.holdingsValue = holdingsValue;
            this.totalValue = totalValue;
        }

        String getTimestamp() { return timestamp; }
        double getTotalValue() { return totalValue; }

        String toCsvLine() {
            return String.join(",", timestamp, String.valueOf(cash),
                    String.valueOf(holdingsValue), String.valueOf(totalValue));
        }

        static PortfolioSnapshot fromCsvLine(String line) {
            String[] p = line.split(",");
            return new PortfolioSnapshot(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
        }
    }

    // ======================================================================
    // Portfolio
    // ======================================================================
    static class Portfolio {
        private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        private double cash;
        private final double startingCash;
        private final Map<String, Integer> holdings = new LinkedHashMap<>();
        private final Map<String, Double> avgCost = new LinkedHashMap<>();
        private final List<Transaction> transactions = new ArrayList<>();
        private final List<PortfolioSnapshot> snapshots = new ArrayList<>();

        Portfolio(double startingCash) {
            this.cash = startingCash;
            this.startingCash = startingCash;
        }

        String buy(Market market, String symbol, int quantity) {
            Stock stock = market.getStock(symbol);
            if (stock == null) return "No such stock: " + symbol;
            if (quantity <= 0) return "Quantity must be positive.";

            double cost = stock.getPrice() * quantity;
            if (cost > cash) return String.format("Insufficient cash. Need $%.2f, have $%.2f.", cost, cash);

            int prevQty = holdings.getOrDefault(stock.getSymbol(), 0);
            double prevAvg = avgCost.getOrDefault(stock.getSymbol(), 0.0);
            int newQty = prevQty + quantity;
            double newAvg = ((prevAvg * prevQty) + (stock.getPrice() * quantity)) / newQty;

            cash -= cost;
            holdings.put(stock.getSymbol(), newQty);
            avgCost.put(stock.getSymbol(), Math.round(newAvg * 10000.0) / 10000.0);
            transactions.add(Transaction.create(Transaction.Action.BUY, stock.getSymbol(), quantity, stock.getPrice()));

            return String.format("Bought %d shares of %s at $%.2f (total $%.2f).",
                    quantity, stock.getSymbol(), stock.getPrice(), cost);
        }

        String sell(Market market, String symbol, int quantity) {
            Stock stock = market.getStock(symbol);
            if (stock == null) return "No such stock: " + symbol;
            int owned = holdings.getOrDefault(stock.getSymbol(), 0);
            if (quantity <= 0) return "Quantity must be positive.";
            if (quantity > owned) return String.format("You only own %d shares of %s.", owned, stock.getSymbol());

            double proceeds = stock.getPrice() * quantity;
            cash += proceeds;
            int remaining = owned - quantity;
            if (remaining == 0) {
                holdings.remove(stock.getSymbol());
                avgCost.remove(stock.getSymbol());
            } else {
                holdings.put(stock.getSymbol(), remaining);
            }
            transactions.add(Transaction.create(Transaction.Action.SELL, stock.getSymbol(), quantity, stock.getPrice()));

            return String.format("Sold %d shares of %s at $%.2f (total $%.2f).",
                    quantity, stock.getSymbol(), stock.getPrice(), proceeds);
        }

        double holdingsValue(Market market) {
            double total = 0.0;
            for (Map.Entry<String, Integer> e : holdings.entrySet()) {
                Stock s = market.getStock(e.getKey());
                if (s != null) total += s.getPrice() * e.getValue();
            }
            return Math.round(total * 100.0) / 100.0;
        }

        double totalValue(Market market) {
            return Math.round((cash + holdingsValue(market)) * 100.0) / 100.0;
        }

        PortfolioSnapshot recordSnapshot(Market market) {
            PortfolioSnapshot snap = new PortfolioSnapshot(
                    LocalDateTime.now().format(FMT),
                    Math.round(cash * 100.0) / 100.0,
                    holdingsValue(market),
                    totalValue(market));
            snapshots.add(snap);
            return snap;
        }

        String performanceReport(Market market) {
            double currentValue = totalValue(market);
            double gain = currentValue - startingCash;
            double gainPct = startingCash != 0 ? (gain / startingCash) * 100 : 0;

            StringBuilder sb = new StringBuilder();
            sb.append("\n").append("=".repeat(60)).append("\n");
            sb.append("PORTFOLIO PERFORMANCE\n");
            sb.append("-".repeat(60)).append("\n");
            sb.append(String.format("Starting Capital : $%,.2f%n", startingCash));
            sb.append(String.format("Cash Balance     : $%,.2f%n", cash));
            sb.append(String.format("Holdings Value   : $%,.2f%n", holdingsValue(market)));
            sb.append(String.format("Total Value      : $%,.2f%n", currentValue));
            sb.append(String.format("Total Gain/Loss  : %s$%,.2f (%+.2f%%)%n",
                    gain >= 0 ? "+" : "", gain, gainPct));
            if (!snapshots.isEmpty()) {
                sb.append("-".repeat(60)).append("\n");
                sb.append("History (last 10 snapshots):\n");
                int start = Math.max(0, snapshots.size() - 10);
                for (PortfolioSnapshot snap : snapshots.subList(start, snapshots.size())) {
                    sb.append(String.format("  %s  total=$%,.2f%n", snap.getTimestamp(), snap.getTotalValue()));
                }
            }
            sb.append("=".repeat(60));
            return sb.toString();
        }

        String holdingsReport(Market market) {
            if (holdings.isEmpty()) return "No current holdings.";
            StringBuilder sb = new StringBuilder();
            sb.append("\n").append("=".repeat(70)).append("\n");
            sb.append(String.format("%-8s%6s%12s%12s%14s%16s%n",
                    "SYMBOL", "QTY", "AVG COST", "PRICE", "MKT VALUE", "GAIN/LOSS"));
            sb.append("-".repeat(70)).append("\n");
            for (Map.Entry<String, Integer> e : holdings.entrySet()) {
                String symbol = e.getKey();
                int qty = e.getValue();
                Stock stock = market.getStock(symbol);
                double price = stock != null ? stock.getPrice() : 0.0;
                double avg = avgCost.getOrDefault(symbol, 0.0);
                double mktValue = price * qty;
                double gain = (price - avg) * qty;
                sb.append(String.format("%-8s%6d%12s%12s%14s%16s%n",
                        symbol, qty,
                        String.format("$%.2f", avg),
                        String.format("$%.2f", price),
                        String.format("$%.2f", mktValue),
                        String.format("%s$%.2f", gain >= 0 ? "+" : "", gain)));
            }
            sb.append("=".repeat(70));
            return sb.toString();
        }

        String transactionHistory() {
            if (transactions.isEmpty()) return "No transactions yet.";
            StringBuilder sb = new StringBuilder();
            for (Transaction t : transactions) sb.append(t).append("\n");
            return sb.toString().stripTrailing();
        }

        // getters used by Persistence
        double getCash() { return cash; }
        void setCash(double cash) { this.cash = cash; }
        double getStartingCash() { return startingCash; }
        Map<String, Integer> getHoldings() { return holdings; }
        Map<String, Double> getAvgCost() { return avgCost; }
        List<Transaction> getTransactions() { return transactions; }
        List<PortfolioSnapshot> getSnapshots() { return snapshots; }
    }

    // ======================================================================
    // User
    // ======================================================================
    static class User {
        private final String username;
        private final Portfolio portfolio;

        User(String username, Portfolio portfolio) {
            this.username = username;
            this.portfolio = portfolio;
        }

        String getUsername() { return username; }
        Portfolio getPortfolio() { return portfolio; }
    }

    // ======================================================================
    // Persistence (plain-text file I/O)
    // ======================================================================
    static class Persistence {
        private final String filepath;

        Persistence(String filepath) { this.filepath = filepath; }

        void save(User user) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filepath))) {
                Portfolio p = user.getPortfolio();
                writer.write("USERNAME=" + user.getUsername()); writer.newLine();
                writer.write("CASH=" + p.getCash()); writer.newLine();
                writer.write("STARTING_CASH=" + p.getStartingCash()); writer.newLine();

                writer.write("HOLDINGS_BEGIN"); writer.newLine();
                for (Map.Entry<String, Integer> e : p.getHoldings().entrySet()) {
                    double avg = p.getAvgCost().getOrDefault(e.getKey(), 0.0);
                    writer.write(e.getKey() + "," + e.getValue() + "," + avg); writer.newLine();
                }
                writer.write("HOLDINGS_END"); writer.newLine();

                writer.write("TRANSACTIONS_BEGIN"); writer.newLine();
                for (Transaction t : p.getTransactions()) {
                    writer.write(t.toCsvLine()); writer.newLine();
                }
                writer.write("TRANSACTIONS_END"); writer.newLine();

                writer.write("SNAPSHOTS_BEGIN"); writer.newLine();
                for (PortfolioSnapshot s : p.getSnapshots()) {
                    writer.write(s.toCsvLine()); writer.newLine();
                }
                writer.write("SNAPSHOTS_END"); writer.newLine();
            } catch (IOException e) {
                System.out.println("Failed to save portfolio: " + e.getMessage());
            }
        }

        User load() {
            File file = new File(filepath);
            if (!file.exists()) return null;

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String username = null;
                double cash = 0.0;
                double startingCash = 10_000.0;

                List<String> lines = reader.lines().toList();
                int i = 0;
                for (; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.startsWith("USERNAME=")) {
                        username = line.substring("USERNAME=".length());
                    } else if (line.startsWith("CASH=")) {
                        cash = Double.parseDouble(line.substring("CASH=".length()));
                    } else if (line.startsWith("STARTING_CASH=")) {
                        startingCash = Double.parseDouble(line.substring("STARTING_CASH=".length()));
                        i++;
                        break;
                    }
                }

                Portfolio portfolio = new Portfolio(startingCash);
                portfolio.setCash(cash);

                String section = null;
                for (; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.equals("HOLDINGS_BEGIN")) { section = "HOLDINGS"; continue; }
                    if (line.equals("TRANSACTIONS_BEGIN")) { section = "TRANSACTIONS"; continue; }
                    if (line.equals("SNAPSHOTS_BEGIN")) { section = "SNAPSHOTS"; continue; }
                    if (line.endsWith("_END")) { section = null; continue; }
                    if (line.isBlank()) continue;

                    if ("HOLDINGS".equals(section)) {
                        String[] parts = line.split(",");
                        portfolio.getHoldings().put(parts[0], Integer.parseInt(parts[1]));
                        portfolio.getAvgCost().put(parts[0], Double.parseDouble(parts[2]));
                    } else if ("TRANSACTIONS".equals(section)) {
                        portfolio.getTransactions().add(Transaction.fromCsvLine(line));
                    } else if ("SNAPSHOTS".equals(section)) {
                        portfolio.getSnapshots().add(PortfolioSnapshot.fromCsvLine(line));
                    }
                }

                if (username == null) return null;
                return new User(username, portfolio);
            } catch (IOException | NumberFormatException | ArrayIndexOutOfBoundsException e) {
                System.out.println("Failed to load portfolio: " + e.getMessage());
                return null;
            }
        }
    }

    // ======================================================================
    // Main / CLI menu
    // ======================================================================
    private static final String MENU = """

            ------------------------------------------------------------
             STOCK TRADING PLATFORM
            ------------------------------------------------------------
             1) View market data
             2) Buy stock
             3) Sell stock
             4) View holdings
             5) View portfolio performance
             6) View transaction history
             7) Advance market (simulate next tick)
             8) Save portfolio
             9) Save & Exit
            ------------------------------------------------------------
            """;

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            Market market = Market.defaultMarket();
            Persistence persistence = new Persistence("portfolio_data.txt");

            User user = persistence.load();
            if (user == null) {
                System.out.print("New account - enter a username: ");
                String username = scanner.nextLine().trim();
                if (username.isEmpty()) username = "trader";
                user = new User(username, new Portfolio(10_000.0));
                System.out.printf("Welcome, %s! You start with $%,.2f in cash.%n",
                        user.getUsername(), user.getPortfolio().getCash());
            } else {
                System.out.println("Welcome back, " + user.getUsername() + "!");
            }

            while (true) {
                System.out.println(MENU);
                System.out.print("Choose an option: ");
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1" -> market.display();
                    case "2" -> {
                        System.out.print("Symbol to buy: ");
                        String symbol = scanner.nextLine().trim();
                        System.out.print("Quantity: ");
                        try {
                            int qty = Integer.parseInt(scanner.nextLine().trim());
                            System.out.println(user.getPortfolio().buy(market, symbol, qty));
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid quantity.");
                        }
                    }
                    case "3" -> {
                        System.out.print("Symbol to sell: ");
                        String symbol = scanner.nextLine().trim();
                        System.out.print("Quantity: ");
                        try {
                            int qty = Integer.parseInt(scanner.nextLine().trim());
                            System.out.println(user.getPortfolio().sell(market, symbol, qty));
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid quantity.");
                        }
                    }
                    case "4" -> System.out.println(user.getPortfolio().holdingsReport(market));
                    case "5" -> System.out.println(user.getPortfolio().performanceReport(market));
                    case "6" -> System.out.println("\n" + user.getPortfolio().transactionHistory());
                    case "7" -> {
                        market.tick();
                        PortfolioSnapshot snap = user.getPortfolio().recordSnapshot(market);
                        System.out.printf("Market ticked. Portfolio total value: $%,.2f%n", snap.getTotalValue());
                    }
                    case "8" -> {
                        persistence.save(user);
                        System.out.println("Portfolio saved.");
                    }
                    case "9" -> {
                        persistence.save(user);
                        System.out.println("Goodbye!");
                        return;
                    }
                    default -> System.out.println("Invalid option, please try again.");
                }
            }
        }
    }
}