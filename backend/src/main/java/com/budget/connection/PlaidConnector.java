package com.budget.connection;

import com.budget.connection.ConnectorProperties.Plaid;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base connector for banks reached through the <a href="https://plaid.com/docs/">Plaid</a> Open Banking
 * aggregator — Santander and Amex have no hobbyist API of their own. Plaid is the licensed AISP; we
 * connect under it. Each subclass fixes the {@link BankProvider} so synced rows are tagged correctly.
 *
 * <p>Connection flow (driven by {@code ConnectionController} + Plaid Link on the frontend):
 * <ol>
 *   <li>{@link #createLinkToken} → frontend opens Plaid Link with the returned token.</li>
 *   <li>User authorises with their bank; Link returns a {@code public_token}.</li>
 *   <li>{@link #exchangePublicToken} swaps it for a long-lived {@code access_token} (stored on the
 *       connection's {@code apiKey} field) and the {@code item_id}.</li>
 *   <li>{@link #fetch} calls {@code /transactions/sync} and normalises the results.</li>
 * </ol>
 *
 * <p>Open Banking consent expires after 90 days; an expired item surfaces here as a re-auth required
 * {@link ConnectorException}, and the connection is moved to {@link ConnectionStatus#NEEDS_REAUTH}.
 */
public abstract class PlaidConnector implements BankConnector {

    private final Plaid config;
    private final RestClient client;

    protected PlaidConnector(ConnectorProperties properties) {
        this.config = properties.getPlaid();
        this.client = RestClient.create(baseUrl(config.getEnvironment()));
    }

    private static String baseUrl(String environment) {
        return switch (environment == null ? "sandbox" : environment.toLowerCase()) {
            case "production" -> "https://production.plaid.com";
            case "development" -> "https://development.plaid.com";
            default -> "https://sandbox.plaid.com";
        };
    }

    @Override
    public boolean isConfigured() {
        return config.isConfigured();
    }

    /** Create a Plaid Link token the frontend uses to open the bank-authorisation modal. */
    public String createLinkToken(String userId) {
        Map<String, Object> user = Map.of("client_user_id", userId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_id", config.getClientId());
        body.put("secret", config.getSecret());
        body.put("client_name", "Budget");
        body.put("user", user);
        body.put("products", List.of("transactions"));
        body.put("country_codes", List.of("GB"));
        body.put("language", "en");
        LinkTokenResponse response = post("/link/token/create", body, LinkTokenResponse.class);
        return response == null ? null : response.link_token();
    }

    /** Exchange the Link {@code public_token} for a durable access token; persist it on the connection. */
    public void exchangePublicToken(BankConnection connection, String publicToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_id", config.getClientId());
        body.put("secret", config.getSecret());
        body.put("public_token", publicToken);
        ExchangeResponse response = post("/item/public_token/exchange", body, ExchangeResponse.class);
        if (response == null || response.access_token() == null) {
            throw new ConnectorException("Plaid did not return an access token", false);
        }
        connection.setApiKey(response.access_token());
        connection.setExternalAccountId(response.item_id());
        connection.setStatus(ConnectionStatus.CONNECTED);
    }

    @Override
    public List<NormalizedTransaction> fetch(BankConnection connection, LocalDate from) {
        String accessToken = connection.getApiKey();
        if (accessToken == null || accessToken.isBlank()) {
            throw new ConnectorException("Plaid item not linked yet for this connection", true);
        }

        List<NormalizedTransaction> result = new ArrayList<>();
        // Cursor-based incremental sync. We page from the start each run and let the importer de-dup;
        // persisting next_cursor on the connection would make this strictly incremental.
        String cursor = null;
        boolean hasMore = true;
        while (hasMore) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("client_id", config.getClientId());
            body.put("secret", config.getSecret());
            body.put("access_token", accessToken);
            if (cursor != null) {
                body.put("cursor", cursor);
            }
            SyncResponse response = post("/transactions/sync", body, SyncResponse.class);
            if (response == null) {
                break;
            }
            if (response.added() != null) {
                for (PlaidTransaction tx : response.added()) {
                    NormalizedTransaction normalized = toNormalized(tx, from);
                    if (normalized != null) {
                        result.add(normalized);
                    }
                }
            }
            cursor = response.next_cursor();
            hasMore = response.has_more() != null && response.has_more();
        }
        return result;
    }

    private static NormalizedTransaction toNormalized(PlaidTransaction tx, LocalDate from) {
        if (tx.date() == null || tx.amount() == null) {
            return null;
        }
        LocalDate date = LocalDate.parse(tx.date());
        if (date.isBefore(from)) {
            return null;
        }
        String description = tx.merchant_name() != null && !tx.merchant_name().isBlank()
                ? tx.merchant_name() : tx.name();
        // Plaid signs outflows positive; flip to our convention (negative = money out).
        BigDecimal amount = tx.amount().negate();
        String providerCategory = tx.personal_finance_category() != null
                ? tx.personal_finance_category().primary() : null;
        return new NormalizedTransaction(tx.transaction_id(), date, description, amount, providerCategory);
    }

    private <T> T post(String path, Map<String, Object> body, Class<T> type) {
        try {
            return client.post()
                    .uri(path)
                    .body(body)
                    .retrieve()
                    .body(type);
        } catch (RestClientResponseException ex) {
            // ITEM_LOGIN_REQUIRED → the user must re-authorise the bank consent.
            boolean reauth = ex.getResponseBodyAsString().contains("ITEM_LOGIN_REQUIRED")
                    || ex.getStatusCode().value() == 401;
            throw new ConnectorException("Plaid API error " + ex.getStatusCode().value() + ": "
                    + ex.getResponseBodyAsString(), reauth);
        }
    }

    // --- Plaid wire DTOs (subset) ---

    private record LinkTokenResponse(String link_token) {
    }

    private record ExchangeResponse(String access_token, String item_id) {
    }

    private record SyncResponse(List<PlaidTransaction> added, String next_cursor, Boolean has_more) {
    }

    private record PlaidTransaction(String transaction_id, String date, String name, String merchant_name,
                                    BigDecimal amount, PersonalFinanceCategory personal_finance_category) {
    }

    private record PersonalFinanceCategory(String primary, String detailed) {
    }
}
