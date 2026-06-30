package com.budget.connection;

import com.budget.connection.ConnectorProperties.Trading212;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pulls cash movements (deposits, withdrawals, dividends, fees, interest) from the
 * <a href="https://docs.trading212.com/api">Trading 212 public API</a>. The user generates a per-account
 * API key in the Trading 212 app and pastes it in; we send it as the {@code Authorization} header.
 *
 * <p>Trading 212 is an investing platform, not a current account, so these rows are cash flows rather
 * than card spend. The history endpoint is paginated via {@code nextPagePath}.
 */
@Component
public class Trading212Connector implements BankConnector {

    private static final String LIVE_BASE = "https://live.trading212.com/api/v0";
    private static final String DEMO_BASE = "https://demo.trading212.com/api/v0";
    private static final int MAX_PAGES = 20;

    /** Transaction types that represent money leaving the account, so the amount is an outflow. */
    private static final Set<String> OUTFLOW_TYPES = Set.of("WITHDRAW", "WITHDRAWAL", "FEE", "FEE_REFUND");

    private final RestClient client;

    public Trading212Connector(ConnectorProperties properties) {
        Trading212 config = properties.getTrading212();
        String base = "demo".equalsIgnoreCase(config.getEnvironment()) ? DEMO_BASE : LIVE_BASE;
        this.client = RestClient.create(base);
    }

    @Override
    public BankProvider provider() {
        return BankProvider.TRADING212;
    }

    @Override
    public boolean isConfigured() {
        // Nothing to configure at deployment level — each connection brings its own API key.
        return true;
    }

    @Override
    public List<NormalizedTransaction> fetch(BankConnection connection, LocalDate from) {
        String apiKey = connection.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new ConnectorException("No Trading 212 API key set on this connection", true);
        }

        List<NormalizedTransaction> result = new ArrayList<>();
        String path = "/equity/history/transactions?limit=50";
        int pages = 0;
        while (path != null && pages++ < MAX_PAGES) {
            HistoryPage page = getPage(apiKey, path);
            if (page == null || page.items() == null) {
                break;
            }
            boolean reachedCutoff = false;
            for (HistoryItem item : page.items()) {
                if (item.dateTime() == null || item.amount() == null) {
                    continue;
                }
                LocalDate date = OffsetDateTime.parse(item.dateTime()).atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
                if (date.isBefore(from)) {
                    reachedCutoff = true;     // results are newest-first; stop once we pass the window
                    break;
                }
                result.add(toNormalized(item, date));
            }
            path = reachedCutoff ? null : page.nextPagePath();
        }
        return result;
    }

    private HistoryPage getPage(String apiKey, String path) {
        try {
            return client.get()
                    .uri(path)
                    .header("Authorization", apiKey)
                    .retrieve()
                    .body(HistoryPage.class);
        } catch (RestClientResponseException ex) {
            boolean reauth = ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403;
            throw new ConnectorException("Trading 212 API error " + ex.getStatusCode().value() + ": "
                    + ex.getResponseBodyAsString(), reauth);
        }
    }

    private static NormalizedTransaction toNormalized(HistoryItem item, LocalDate date) {
        BigDecimal amount = item.amount();
        String type = item.type() == null ? "" : item.type().toUpperCase(Locale.ROOT);
        // Normalise to signed pounds: force outflow types negative regardless of how the API signs them.
        if (OUTFLOW_TYPES.contains(type) && amount.signum() > 0) {
            amount = amount.negate();
        }
        String description = "Trading 212 " + (item.reference() != null && !item.reference().isBlank()
                ? item.reference() : friendlyType(type));
        String externalId = item.reference() != null && !item.reference().isBlank()
                ? item.reference()
                : type + ":" + item.dateTime() + ":" + item.amount();
        return new NormalizedTransaction(externalId, date, description, amount, providerCategory(type));
    }

    /** Map a Trading 212 cash-movement type to a category token the ProviderCategoryMapper understands. */
    private static String providerCategory(String type) {
        return switch (type) {
            case "DEPOSIT" -> "DEPOSIT";
            case "WITHDRAW", "WITHDRAWAL" -> "WITHDRAWAL";
            case "DIVIDEND" -> "DIVIDEND";
            case "INTEREST" -> "INTEREST";
            case "FEE", "FEE_REFUND" -> "FEE";
            default -> null;
        };
    }

    private static String friendlyType(String type) {
        return switch (type) {
            case "DEPOSIT" -> "Deposit";
            case "WITHDRAW", "WITHDRAWAL" -> "Withdrawal";
            case "DIVIDEND" -> "Dividend";
            case "INTEREST" -> "Interest";
            case "FEE", "FEE_REFUND" -> "Fee";
            case "" -> "transaction";
            default -> type;
        };
    }

    // --- Trading 212 wire DTOs (subset) ---

    private record HistoryPage(List<HistoryItem> items, String nextPagePath) {
    }

    private record HistoryItem(String reference, String type, BigDecimal amount, String dateTime) {
    }
}
