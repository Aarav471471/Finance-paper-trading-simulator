package com.papertrader.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The Indian (NSE) shares the app knows about: the NIFTY 50, the NIFTY Next 50 and the
 * popular recent listings people search for (Jio Financial, Swiggy, LIC, Paytm, Nykaa...).
 *
 * This is the ONE place that decides whether a symbol is an Indian share (rupee prices,
 * NSE hours, Groww / Yahoo ".NS" quotes). Shares the user looks up later by symbol
 * (see {@link #register}) are added here too.
 *
 * The prices below are only starting values shown until the first live quote arrives.
 */
public final class IndianStocks {

    /** One catalog row. {@code nseSymbol} is the symbol on NSE today, which may differ from ours. */
    public record Entry(String symbol, String nseSymbol, String name, String sector, double price) {
        public Stock toStock() {
            return new Stock(symbol, name, price, sector);
        }
    }

    /** NSE trading symbols: letters, digits, '&' and '-' (M&M, BAJAJ-AUTO). */
    private static final Pattern NSE_SYMBOL = Pattern.compile("[A-Z0-9][A-Z0-9&\\-]{0,19}");

    private static final Map<String, Entry> CATALOG = new LinkedHashMap<>();
    /** Symbols added at run time by a lookup, mapped to their NSE symbol. */
    private static final Map<String, String> EXTRA = new ConcurrentHashMap<>();

    static {
        // ------------------------------------------------------------ NIFTY 50
        add("RELIANCE", "Reliance Industries Ltd", "Energy", 1480);
        add("HDFCBANK", "HDFC Bank Ltd", "Banking", 990);
        add("TCS", "Tata Consultancy Services", "Information Tech", 3100);
        add("BHARTIARTL", "Bharti Airtel Ltd", "Telecom", 2050);
        add("ICICIBANK", "ICICI Bank Ltd", "Banking", 1380);
        add("SBIN", "State Bank of India", "Banking", 950);
        add("INFY", "Infosys Ltd", "Information Tech", 1520);
        add("BAJFINANCE", "Bajaj Finance Ltd", "Financial Services", 1000);
        add("HINDUNILVR", "Hindustan Unilever Ltd", "FMCG", 2450);
        add("LICI", "Life Insurance Corporation of India", "Insurance", 900);
        add("ITC", "ITC Ltd", "FMCG", 405);
        add("LT", "Larsen & Toubro Ltd", "Infrastructure", 3950);
        add("KOTAKBANK", "Kotak Mahindra Bank Ltd", "Banking", 2100);
        add("HCLTECH", "HCL Technologies Ltd", "Information Tech", 1550);
        add("SUNPHARMA", "Sun Pharmaceutical Industries", "Pharma", 1700);
        add("M&M", "Mahindra & Mahindra Ltd", "Automobile", 3600);
        add("MARUTI", "Maruti Suzuki India Ltd", "Automobile", 15800);
        add("AXISBANK", "Axis Bank Ltd", "Banking", 1230);
        add("ULTRACEMCO", "UltraTech Cement Ltd", "Cement", 11800);
        add("NTPC", "NTPC Ltd", "Power", 330);
        add("TITAN", "Titan Company Ltd", "Consumer Durables", 3800);
        add("ETERNAL", "Eternal Ltd (Zomato)", "Consumer Tech", 300);
        add("BAJAJFINSV", "Bajaj Finserv Ltd", "Financial Services", 2050);
        add("ONGC", "Oil & Natural Gas Corporation", "Oil & Gas", 245);
        add("ADANIPORTS", "Adani Ports & SEZ Ltd", "Infrastructure", 1450);
        add("BEL", "Bharat Electronics Ltd", "Defence", 420);
        add("ADANIENT", "Adani Enterprises Ltd", "Conglomerate", 2400);
        add("POWERGRID", "Power Grid Corporation of India", "Power", 285);
        add("JSWSTEEL", "JSW Steel Ltd", "Metals", 1150);
        add("TATAMOTORS", "TMPV", "Tata Motors Passenger Vehicles", "Automobile", 400);
        add("TMCV", "Tata Motors (Commercial Vehicles)", "Automobile", 320);
        add("WIPRO", "Wipro Ltd", "Information Tech", 245);
        add("COALINDIA", "Coal India Ltd", "Metals", 385);
        add("BAJAJ-AUTO", "Bajaj Auto Ltd", "Automobile", 8900);
        add("NESTLEIND", "Nestle India Ltd", "FMCG", 1250);
        add("ASIANPAINT", "Asian Paints Ltd", "Consumer Durables", 2500);
        add("HINDALCO", "Hindalco Industries Ltd", "Metals", 800);
        add("TATASTEEL", "Tata Steel Ltd", "Metals", 170);
        add("TRENT", "Trent Ltd", "Retail", 4700);
        add("GRASIM", "Grasim Industries Ltd", "Cement", 2800);
        add("SBILIFE", "SBI Life Insurance Company", "Insurance", 1950);
        add("HDFCLIFE", "HDFC Life Insurance Company", "Insurance", 760);
        add("EICHERMOT", "Eicher Motors Ltd", "Automobile", 6900);
        add("SHRIRAMFIN", "Shriram Finance Ltd", "Financial Services", 750);
        add("TECHM", "Tech Mahindra Ltd", "Information Tech", 1450);
        add("JIOFIN", "Jio Financial Services Ltd", "Financial Services", 310);
        add("CIPLA", "Cipla Ltd", "Pharma", 1520);
        add("APOLLOHOSP", "Apollo Hospitals Enterprise", "Healthcare", 7500);
        add("DRREDDY", "Dr. Reddy's Laboratories", "Pharma", 1250);
        add("TATACONSUM", "Tata Consumer Products", "FMCG", 1150);
        add("HEROMOTOCO", "Hero MotoCorp Ltd", "Automobile", 5500);
        add("INDUSINDBK", "IndusInd Bank Ltd", "Banking", 780);
        add("INDIGO", "InterGlobe Aviation (IndiGo)", "Aviation", 5700);
        add("MAXHEALTH", "Max Healthcare Institute", "Healthcare", 1150);

        // ------------------------------------------------------------ NIFTY Next 50
        add("HAL", "Hindustan Aeronautics Ltd", "Defence", 4700);
        add("DMART", "Avenue Supermarts (DMart)", "Retail", 4200);
        add("ADANIPOWER", "Adani Power Ltd", "Power", 150);
        add("VEDL", "Vedanta Ltd", "Metals", 500);
        add("IOC", "Indian Oil Corporation", "Oil & Gas", 165);
        add("IRFC", "Indian Railway Finance Corp", "Financial Services", 125);
        add("DLF", "DLF Ltd", "Realty", 760);
        add("HINDZINC", "Hindustan Zinc Ltd", "Metals", 480);
        add("BAJAJHLDNG", "Bajaj Holdings & Investment", "Financial Services", 12500);
        add("PFC", "Power Finance Corporation", "Financial Services", 400);
        add("VBL", "Varun Beverages Ltd", "FMCG", 470);
        add("DIVISLAB", "Divi's Laboratories", "Pharma", 6400);
        add("LTIM", "LTIMindtree Ltd", "Information Tech", 5700);
        add("BANKBARODA", "Bank of Baroda", "Banking", 280);
        add("TVSMOTOR", "TVS Motor Company", "Automobile", 3500);
        add("RECLTD", "REC Ltd", "Financial Services", 370);
        add("PIDILITIND", "Pidilite Industries", "Chemicals", 1500);
        add("CHOLAFIN", "Cholamandalam Investment & Finance", "Financial Services", 1650);
        add("MOTHERSON", "Samvardhana Motherson International", "Automobile", 105);
        add("TATAPOWER", "Tata Power Company", "Power", 390);
        add("GAIL", "GAIL (India) Ltd", "Oil & Gas", 180);
        add("PNB", "Punjab National Bank", "Banking", 120);
        add("CANBK", "Canara Bank", "Banking", 140);
        add("BPCL", "Bharat Petroleum Corporation", "Oil & Gas", 360);
        add("ADANIGREEN", "Adani Green Energy", "Power", 1000);
        add("ADANIENSOL", "Adani Energy Solutions", "Power", 950);
        add("AMBUJACEM", "Ambuja Cements", "Cement", 560);
        add("BRITANNIA", "Britannia Industries", "FMCG", 5900);
        add("SIEMENS", "Siemens Ltd", "Capital Goods", 3100);
        add("ABB", "ABB India Ltd", "Capital Goods", 5200);
        add("GODREJCP", "Godrej Consumer Products", "FMCG", 1150);
        add("SHREECEM", "Shree Cement", "Cement", 28500);
        add("TORNTPHARM", "Torrent Pharmaceuticals", "Pharma", 3600);
        add("DABUR", "Dabur India", "FMCG", 500);
        add("HAVELLS", "Havells India", "Consumer Durables", 1500);
        add("UNITDSPR", "United Spirits", "FMCG", 1350);
        add("ICICIGI", "ICICI Lombard General Insurance", "Insurance", 1950);
        add("ICICIPRULI", "ICICI Prudential Life Insurance", "Insurance", 620);
        add("JINDALSTEL", "Jindal Steel", "Metals", 1000);
        add("JSWENERGY", "JSW Energy", "Power", 520);
        add("NAUKRI", "Info Edge (Naukri.com)", "Consumer Tech", 1350);
        add("LODHA", "Lodha Developers (Macrotech)", "Realty", 1200);
        add("ZYDUSLIFE", "Zydus Lifesciences", "Pharma", 980);
        add("BOSCHLTD", "Bosch Ltd", "Automobile", 37000);
        add("CGPOWER", "CG Power & Industrial Solutions", "Capital Goods", 700);
        add("BHEL", "Bharat Heavy Electricals", "Capital Goods", 250);
        add("POLYCAB", "Polycab India", "Capital Goods", 7200);
        add("SOLARINDS", "Solar Industries India", "Defence", 13500);
        add("MAZDOCK", "Mazagon Dock Shipbuilders", "Defence", 2800);

        // ------------------------------------------------------------ popular new listings & retail favourites
        add("SWIGGY", "Swiggy Ltd", "Consumer Tech", 420);
        add("PAYTM", "One 97 Communications (Paytm)", "Consumer Tech", 1250);
        add("NYKAA", "FSN E-Commerce (Nykaa)", "Retail", 250);
        add("POLICYBZR", "PB Fintech (PolicyBazaar)", "Consumer Tech", 1800);
        add("HYUNDAI", "Hyundai Motor India", "Automobile", 2400);
        add("TATATECH", "Tata Technologies", "Information Tech", 680);
        add("OLAELEC", "Ola Electric Mobility", "Automobile", 45);
        add("BAJAJHFL", "Bajaj Housing Finance", "Financial Services", 110);
        add("IREDA", "Indian Renewable Energy Dev. Agency", "Financial Services", 150);
        add("NTPCGREEN", "NTPC Green Energy", "Power", 100);
        add("WAAREEENER", "Waaree Energies", "Power", 3200);
        add("PREMIERENE", "Premier Energies", "Power", 1000);
        add("HDBFS", "HDB Financial Services", "Financial Services", 760);
        add("TATACAP", "Tata Capital", "Financial Services", 330);
        add("LGEINDIA", "LG Electronics India", "Consumer Durables", 1650);
        add("ATHERENERG", "Ather Energy", "Automobile", 650);
        add("URBANCO", "Urban Company", "Consumer Tech", 150);
        add("LENSKART", "Lenskart Solutions", "Retail", 410);
        add("GROWW", "Billionbrains Garage Ventures (Groww)", "Financial Services", 150);
        add("PINELABS", "Pine Labs", "Consumer Tech", 240);
        add("IRCTC", "Indian Railway Catering & Tourism", "Consumer Tech", 720);
        add("RVNL", "Rail Vikas Nigam", "Infrastructure", 330);
        add("IDEA", "Vodafone Idea", "Telecom", 10);
        add("YESBANK", "Yes Bank", "Banking", 22);
        add("SUZLON", "Suzlon Energy", "Power", 58);
        add("COCHINSHIP", "Cochin Shipyard", "Defence", 1700);
        add("BDL", "Bharat Dynamics", "Defence", 1500);
        add("DIXON", "Dixon Technologies", "Consumer Durables", 15000);
        add("KALYANKJIL", "Kalyan Jewellers India", "Retail", 500);
        add("DELHIVERY", "Delhivery", "Logistics", 450);
        add("IDFCFIRSTB", "IDFC First Bank", "Banking", 75);
        add("FEDERALBNK", "Federal Bank", "Banking", 230);
        add("AUBANK", "AU Small Finance Bank", "Banking", 800);
        add("SAIL", "Steel Authority of India", "Metals", 135);
        add("NMDC", "NMDC Ltd", "Metals", 75);
        add("HINDPETRO", "Hindustan Petroleum", "Oil & Gas", 470);
        add("ASHOKLEY", "Ashok Leyland", "Automobile", 145);
        add("PERSISTENT", "Persistent Systems", "Information Tech", 6000);
        add("COFORGE", "Coforge", "Information Tech", 1800);
        add("MPHASIS", "Mphasis", "Information Tech", 2800);
        add("KPITTECH", "KPIT Technologies", "Information Tech", 1250);
        add("LUPIN", "Lupin", "Pharma", 2000);
        add("AUROPHARMA", "Aurobindo Pharma", "Pharma", 1150);
        add("MANKIND", "Mankind Pharma", "Pharma", 2400);
        add("BSE", "BSE Ltd", "Financial Services", 2500);
        add("MCX", "Multi Commodity Exchange", "Financial Services", 9000);
        add("CDSL", "Central Depository Services", "Financial Services", 1550);
        add("ANGELONE", "Angel One", "Financial Services", 2500);
        add("MUTHOOTFIN", "Muthoot Finance", "Financial Services", 3200);
        add("INDHOTEL", "Indian Hotels Company (Taj)", "Consumer Tech", 750);
        add("JUBLFOOD", "Jubilant FoodWorks (Domino's)", "FMCG", 620);
        add("PAGEIND", "Page Industries (Jockey)", "Retail", 42000);
        add("GODREJPROP", "Godrej Properties", "Realty", 2200);
        add("OBEROIRLTY", "Oberoi Realty", "Realty", 1650);
        add("ADANITOTAL", "Adani Total Gas", "Oil & Gas", 620);
        add("TATAELXSI", "Tata Elxsi", "Information Tech", 5400);
        add("TATACOMM", "Tata Communications", "Telecom", 1750);
        add("VOLTAS", "Voltas", "Consumer Durables", 1350);
        add("PATANJALI", "Patanjali Foods", "FMCG", 600);
        add("ZEEL", "Zee Entertainment", "Media", 110);
        add("SUNTV", "Sun TV Network", "Media", 560);
        add("PVRINOX", "PVR INOX", "Media", 1100);
    }

    private IndianStocks() {
    }

    private static void add(String symbol, String name, String sector, double price) {
        add(symbol, symbol, name, sector, price);
    }

    private static void add(String symbol, String nseSymbol, String name, String sector, double price) {
        CATALOG.put(symbol, new Entry(symbol, nseSymbol, name, sector, price));
    }

    /** Every catalog row, most-followed first. */
    public static List<Entry> catalog() {
        return Collections.unmodifiableList(new ArrayList<>(CATALOG.values()));
    }

    /** True for any NSE share the app knows: catalog or looked up by the user. */
    public static boolean isIndian(String symbol) {
        if (symbol == null) {
            return false;
        }
        String s = symbol.toUpperCase(Locale.ROOT);
        return CATALOG.containsKey(s) || EXTRA.containsKey(s);
    }

    /** The symbol NSE (and Groww) use for one of our symbols, e.g. TATAMOTORS -> TMPV. */
    public static String nseSymbol(String symbol) {
        String s = symbol.toUpperCase(Locale.ROOT);
        Entry e = CATALOG.get(s);
        if (e != null) {
            return e.nseSymbol();
        }
        return EXTRA.getOrDefault(s, s);
    }

    /** Remembers a share the user looked up, so it is treated as Indian everywhere. */
    public static void register(String symbol) {
        String s = normalize(symbol);
        if (s != null && !CATALOG.containsKey(s)) {
            EXTRA.put(s, s);
        }
    }

    /** Symbols looked up by the user during this run (not the built-in catalog). */
    public static Set<String> registered() {
        return Collections.unmodifiableSet(EXTRA.keySet());
    }

    /**
     * Cleans what the user typed ("jiofin", " M&M ", "tcs.ns") into an NSE symbol,
     * or returns null if it cannot be one.
     */
    public static String normalize(String typed) {
        if (typed == null) {
            return null;
        }
        String s = typed.trim().toUpperCase(Locale.ROOT);
        if (s.endsWith(".NS")) {
            s = s.substring(0, s.length() - 3);
        }
        return NSE_SYMBOL.matcher(s).matches() ? s : null;
    }
}
