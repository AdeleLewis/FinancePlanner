package com.budget.connection;

import org.springframework.stereotype.Component;

/** Santander, reached through Plaid Open Banking. */
@Component
public class SantanderConnector extends PlaidConnector {

    public SantanderConnector(ConnectorProperties properties) {
        super(properties);
    }

    @Override
    public BankProvider provider() {
        return BankProvider.SANTANDER;
    }
}
