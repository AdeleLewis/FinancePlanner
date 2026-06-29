package com.budget.connection;

/**
 * Raised by a {@link BankConnector} when a fetch cannot complete. {@link #isReauthRequired()}
 * distinguishes "the user must re-authorise" (expired token/consent) from a transient error.
 */
public class ConnectorException extends RuntimeException {

    private final boolean reauthRequired;

    public ConnectorException(String message, boolean reauthRequired) {
        super(message);
        this.reauthRequired = reauthRequired;
    }

    public ConnectorException(String message, Throwable cause) {
        super(message, cause);
        this.reauthRequired = false;
    }

    public boolean isReauthRequired() {
        return reauthRequired;
    }
}
