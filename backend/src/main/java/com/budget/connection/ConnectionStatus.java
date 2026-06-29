package com.budget.connection;

/**
 * Lifecycle state of a {@link BankConnection}.
 */
public enum ConnectionStatus {

    /** Created but not yet authorised (e.g. waiting for the OAuth callback). */
    PENDING,

    /** Authorised and able to sync. */
    CONNECTED,

    /** Credentials/consent expired — the user must re-authorise (Open Banking consent lasts 90 days). */
    NEEDS_REAUTH,

    /** Last sync failed; see {@link BankConnection#getLastError()}. */
    ERROR
}
