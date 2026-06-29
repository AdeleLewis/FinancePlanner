package com.budget.connection;

/**
 * The banks and brokerages we can pull transactions from, and how each is reached.
 *
 * <p>Monzo and Trading 212 expose free, personal-use APIs we can call directly. Santander and
 * Amex have no hobbyist API — in the UK they are only reachable through a licensed Open Banking
 * aggregator (we target Plaid), where the aggregator holds the AISP licence and we connect under it.
 */
public enum BankProvider {

    MONZO("Monzo", AuthType.OAUTH2, "MONZO", "Current account via the Monzo Developer API (your own account)."),
    TRADING212("Trading 212", AuthType.API_KEY, "TRADING212", "Invest/ISA account via the Trading 212 public API key."),
    SANTANDER("Santander", AuthType.AGGREGATOR, "SANTANDER", "Reached via the Plaid Open Banking aggregator."),
    AMEX("American Express", AuthType.AGGREGATOR, "AMEX", "Reached via the Plaid Open Banking aggregator.");

    private final String displayName;
    private final AuthType authType;
    private final String source;
    private final String description;

    BankProvider(String displayName, AuthType authType, String source, String description) {
        this.displayName = displayName;
        this.authType = authType;
        this.source = source;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AuthType getAuthType() {
        return authType;
    }

    /** Stable tag written to {@code Transaction.source} for rows imported from this provider. */
    public String getSource() {
        return source;
    }

    public String getDescription() {
        return description;
    }
}
