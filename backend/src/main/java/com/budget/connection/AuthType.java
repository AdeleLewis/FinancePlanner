package com.budget.connection;

/**
 * How a {@link BankProvider} is authenticated.
 */
public enum AuthType {

    /** Provider has a native OAuth2 flow we drive directly (e.g. Monzo). */
    OAUTH2,

    /** Provider issues a long-lived API key the user pastes in (e.g. Trading 212). */
    API_KEY,

    /** Reached through a licensed Open Banking aggregator such as Plaid (e.g. Santander, Amex). */
    AGGREGATOR
}
