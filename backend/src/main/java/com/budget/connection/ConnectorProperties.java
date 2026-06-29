package com.budget.connection;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deployment-level connector credentials, bound from {@code app.connectors.*} (typically supplied as
 * environment variables — see application.yml). All fields are optional: a connector with blank
 * credentials reports {@link BankConnector#isConfigured()} == false and is shown but not connectable.
 */
@ConfigurationProperties(prefix = "app.connectors")
public class ConnectorProperties {

    private final Monzo monzo = new Monzo();
    private final Trading212 trading212 = new Trading212();
    private final Plaid plaid = new Plaid();

    public Monzo getMonzo() {
        return monzo;
    }

    public Trading212 getTrading212() {
        return trading212;
    }

    public Plaid getPlaid() {
        return plaid;
    }

    /** Monzo Developer API OAuth client. Register a confidential client to receive refresh tokens. */
    public static class Monzo {
        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "http://localhost:8080/api/connections/monzo/callback";

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }

        public boolean isConfigured() {
            return !clientId.isBlank() && !clientSecret.isBlank();
        }
    }

    /** Trading 212 uses a per-account API key, so there is nothing to configure at deployment level. */
    public static class Trading212 {
        /** "live" or "demo" — selects the Trading 212 API base URL. */
        private String environment = "live";

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }
    }

    /** Plaid aggregator credentials (sandbox/development/production). */
    public static class Plaid {
        private String clientId = "";
        private String secret = "";
        private String environment = "sandbox";

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }

        public boolean isConfigured() {
            return !clientId.isBlank() && !secret.isBlank();
        }
    }
}
