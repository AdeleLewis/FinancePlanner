package com.budget.connection;

import org.springframework.stereotype.Component;

/** American Express, reached through Plaid Open Banking. */
@Component
public class AmexConnector extends PlaidConnector {

    public AmexConnector(ConnectorProperties properties) {
        super(properties);
    }

    @Override
    public BankProvider provider() {
        return BankProvider.AMEX;
    }
}
