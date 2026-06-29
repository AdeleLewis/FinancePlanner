package com.budget.connection;

import java.time.LocalDate;
import java.util.List;

/**
 * A pluggable adapter that pulls transactions from one {@link BankProvider}. Each implementation
 * hides the provider's auth scheme and wire format behind {@link #fetch}, returning provider-agnostic
 * {@link NormalizedTransaction}s. Connectors are stateless Spring beans; per-connection secrets live
 * on the {@link BankConnection} passed in.
 */
public interface BankConnector {

    /** The provider this connector serves. */
    BankProvider provider();

    /**
     * Whether the deployment has the credentials this connector needs (client id/secret, aggregator
     * keys, …). When {@code false} the connector is shown but cannot be connected.
     */
    boolean isConfigured();

    /**
     * Fetch transactions on or after {@code from} for the given connection.
     *
     * @throws ConnectorException on auth or transport failure; the caller marks the connection ERROR
     *                            (or NEEDS_REAUTH for {@link ConnectorException#isReauthRequired()}).
     */
    List<NormalizedTransaction> fetch(BankConnection connection, LocalDate from);
}
