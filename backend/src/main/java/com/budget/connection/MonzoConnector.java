package com.budget.connection;

import com.budget.connection.ConnectorProperties.Monzo;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pulls transactions from the <a href="https://docs.monzo.com/">Monzo Developer API</a> using its
 * OAuth2 flow. Monzo only exposes the authenticated user's own account, which is exactly this app's
 * single-user use case. Amounts arrive as integer minor units (pence); we convert to signed pounds.
 */
@Component
public class MonzoConnector implements BankConnector {

    private static final String AUTH_BASE = "https://auth.monzo.com";
    private static final String API_BASE = "https://api.monzo.com";

    private final Monzo config;
    private final RestClient authClient;
    private final RestClient apiClient;
    private final BankConnectionRepository connections;

    public MonzoConnector(ConnectorProperties properties, BankConnectionRepository connections) {
        this.config = properties.getMonzo();
        this.authClient = RestClient.create(API_BASE);
        this.apiClient = RestClient.create(API_BASE);
        this.connections = connections;
    }

    @Override
    public BankProvider provider() {
        return BankProvider.MONZO;
    }

    @Override
    public boolean isConfigured() {
        return config.isConfigured();
    }

    /** The Monzo authorize URL to send the user to. {@code state} guards against CSRF on the callback. */
    public String authorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTH_BASE)
                .queryParam("client_id", config.getClientId())
                .queryParam("redirect_uri", config.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    /** Exchange an authorization code for tokens and persist them on a new CONNECTED connection. */
    public BankConnection completeAuthorization(String code) {
        TokenResponse token = exchangeCode(code);
        BankConnection connection = new BankConnection(BankProvider.MONZO, "Monzo");
        applyToken(connection, token);
        connection.setExternalAccountId(firstAccountId(token.access_token()));
        connection.setStatus(ConnectionStatus.CONNECTED);
        return connections.save(connection);
    }

    @Override
    public List<NormalizedTransaction> fetch(BankConnection connection, LocalDate from) {
        ensureFreshToken(connection);
        if (connection.getExternalAccountId() == null) {
            connection.setExternalAccountId(firstAccountId(connection.getAccessToken()));
        }
        String accountId = connection.getExternalAccountId();

        String since = from.atStartOfDay().toInstant(ZoneOffset.UTC).toString();
        TransactionsResponse response;
        try {
            response = apiClient.get()
                    .uri(uri -> uri.path("/transactions")
                            .queryParam("account_id", accountId)
                            .queryParam("since", since)
                            .queryParam("expand[]", "merchant")
                            .build())
                    .header("Authorization", "Bearer " + connection.getAccessToken())
                    .retrieve()
                    .body(TransactionsResponse.class);
        } catch (RestClientResponseException ex) {
            throw toConnectorException(ex);
        }

        List<NormalizedTransaction> result = new ArrayList<>();
        if (response == null || response.transactions() == null) {
            return result;
        }
        for (MonzoTransaction tx : response.transactions()) {
            if (tx.amount() == null || tx.created() == null) {
                continue;
            }
            // Monzo includes zero-amount "active card check" holds and top-up declines; skip the noise.
            if (tx.amount() == 0) {
                continue;
            }
            LocalDate date = OffsetDateTime.parse(tx.created()).atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
            result.add(new NormalizedTransaction(tx.id(), date, describe(tx), minorToMajor(tx.amount()), tx.category()));
        }
        return result;
    }

    private TokenResponse exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", config.getClientId());
        form.add("client_secret", config.getClientSecret());
        form.add("redirect_uri", config.getRedirectUri());
        form.add("code", code);
        return postToken(form);
    }

    /** Refresh the access token in place if it has expired (or is about to). No-op without a refresh token. */
    private void ensureFreshToken(BankConnection connection) {
        Instant expiry = connection.getAccessTokenExpiresAt();
        boolean stillValid = expiry != null && expiry.isAfter(Instant.now().plus(60, ChronoUnit.SECONDS));
        if (stillValid) {
            return;
        }
        if (connection.getRefreshToken() == null) {
            throw new ConnectorException("Monzo access token expired and no refresh token is available. "
                    + "Register a confidential client and reconnect.", true);
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", config.getClientId());
        form.add("client_secret", config.getClientSecret());
        form.add("refresh_token", connection.getRefreshToken());
        applyToken(connection, postToken(form));
        connections.save(connection);
    }

    private TokenResponse postToken(MultiValueMap<String, String> form) {
        try {
            return authClient.post()
                    .uri("/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientResponseException ex) {
            throw toConnectorException(ex);
        }
    }

    private void applyToken(BankConnection connection, TokenResponse token) {
        if (token == null || token.access_token() == null) {
            throw new ConnectorException("Monzo token endpoint returned no access token", true);
        }
        connection.setAccessToken(token.access_token());
        if (token.refresh_token() != null) {
            connection.setRefreshToken(token.refresh_token());
        }
        long expiresIn = token.expires_in() != null ? token.expires_in() : 0L;
        connection.setAccessTokenExpiresAt(Instant.now().plusSeconds(expiresIn));
    }

    private String firstAccountId(String accessToken) {
        try {
            AccountsResponse accounts = apiClient.get()
                    .uri("/accounts")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(AccountsResponse.class);
            if (accounts == null || accounts.accounts() == null || accounts.accounts().isEmpty()) {
                throw new ConnectorException("No Monzo accounts visible for this authorization", false);
            }
            return accounts.accounts().get(0).id();
        } catch (RestClientResponseException ex) {
            throw toConnectorException(ex);
        }
    }

    private static String describe(MonzoTransaction tx) {
        if (tx.merchant() != null && tx.merchant().name() != null && !tx.merchant().name().isBlank()) {
            return tx.merchant().name();
        }
        if (tx.description() != null && !tx.description().isBlank()) {
            return tx.description();
        }
        return "Monzo transaction";
    }

    private static BigDecimal minorToMajor(long minorUnits) {
        return BigDecimal.valueOf(minorUnits, 2);
    }

    private static ConnectorException toConnectorException(RestClientResponseException ex) {
        boolean reauth = ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403;
        return new ConnectorException("Monzo API error " + ex.getStatusCode().value() + ": "
                + ex.getResponseBodyAsString(), reauth);
    }

    // --- Monzo wire DTOs (subset of fields we use) ---

    private record TokenResponse(String access_token, String refresh_token, Long expires_in) {
    }

    private record AccountsResponse(List<Account> accounts) {
    }

    private record Account(String id) {
    }

    private record TransactionsResponse(List<MonzoTransaction> transactions) {
    }

    private record MonzoTransaction(String id, Long amount, String created, String description, String category,
                                    Merchant merchant, Map<String, Object> metadata) {
    }

    private record Merchant(String name) {
    }
}
