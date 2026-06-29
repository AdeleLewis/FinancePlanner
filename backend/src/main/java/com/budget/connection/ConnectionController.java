package com.budget.connection;

import com.budget.connection.ConnectionService.ProviderInfo;
import com.budget.connection.ConnectionService.SyncResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST API for linking bank/brokerage accounts and syncing them. Secrets never leave the backend —
 * connections are exposed through {@link ConnectionView}, which omits tokens and keys.
 */
@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    /** Where to send the user after the Monzo OAuth round-trip. */
    private static final String FRONTEND_RETURN_URL = "http://localhost:5173/?tab=connections";

    private final ConnectionService service;
    private final MonzoConnector monzoConnector;
    private final BankConnectionRepository connections;

    /** Outstanding OAuth state tokens, to validate the Monzo callback (CSRF guard). */
    private final Set<String> pendingStates = ConcurrentHashMap.newKeySet();

    public ConnectionController(ConnectionService service,
                                MonzoConnector monzoConnector,
                                BankConnectionRepository connections) {
        this.service = service;
        this.monzoConnector = monzoConnector;
        this.connections = connections;
    }

    @GetMapping("/providers")
    public List<ProviderInfo> providers() {
        return service.listProviders();
    }

    @GetMapping
    public List<ConnectionView> list() {
        return service.listConnections().stream().map(ConnectionView::of).toList();
    }

    @PostMapping("/{id}/sync")
    public SyncResult sync(@PathVariable Long id) {
        return service.sync(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteConnection(id);
        return ResponseEntity.noContent().build();
    }

    // --- Monzo OAuth ---

    /** Returns the Monzo authorize URL for the frontend to redirect the user to. */
    @GetMapping("/monzo/authorize")
    public ResponseEntity<AuthorizeUrl> monzoAuthorize() {
        if (!monzoConnector.isConfigured()) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .body(new AuthorizeUrl("Monzo client id/secret not configured on the server"));
        }
        String state = UUID.randomUUID().toString();
        pendingStates.add(state);
        return ResponseEntity.ok(new AuthorizeUrl(monzoConnector.authorizeUrl(state)));
    }

    /** Monzo redirects here with the authorization code; we exchange it and bounce back to the UI. */
    @GetMapping("/monzo/callback")
    public RedirectView monzoCallback(@RequestParam String code, @RequestParam(required = false) String state) {
        if (state == null || !pendingStates.remove(state)) {
            return new RedirectView(FRONTEND_RETURN_URL + "&error=invalid_state");
        }
        monzoConnector.completeAuthorization(code);
        return new RedirectView(FRONTEND_RETURN_URL + "&connected=monzo");
    }

    // --- Trading 212 (API key) ---

    @PostMapping("/trading212")
    public ConnectionView connectTrading212(@RequestBody Trading212Request request) {
        if (request.apiKey() == null || request.apiKey().isBlank()) {
            throw new IllegalArgumentException("apiKey is required");
        }
        BankConnection connection = new BankConnection(BankProvider.TRADING212,
                displayNameOr(request.displayName(), "Trading 212"));
        connection.setApiKey(request.apiKey().trim());
        connection.setStatus(ConnectionStatus.CONNECTED);
        return ConnectionView.of(connections.save(connection));
    }

    // --- Plaid (Santander / Amex) ---

    /** Create a Plaid Link token for the chosen aggregator-backed provider. */
    @PostMapping("/plaid/link-token")
    public ResponseEntity<LinkTokenResponse> plaidLinkToken(@RequestBody PlaidLinkRequest request) {
        PlaidConnector connector = plaidConnector(request.provider());
        if (!connector.isConfigured()) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .body(new LinkTokenResponse(null, "Plaid client id/secret not configured on the server"));
        }
        String linkToken = connector.createLinkToken("budget-user");
        return ResponseEntity.ok(new LinkTokenResponse(linkToken, null));
    }

    /** Exchange the Plaid Link public token, creating a CONNECTED connection for the provider. */
    @PostMapping("/plaid/exchange")
    public ConnectionView plaidExchange(@RequestBody PlaidExchangeRequest request) {
        BankProvider provider = BankProvider.valueOf(request.provider());
        PlaidConnector connector = plaidConnector(request.provider());
        BankConnection connection = new BankConnection(provider,
                displayNameOr(request.displayName(), provider.getDisplayName()));
        connector.exchangePublicToken(connection, request.publicToken());
        return ConnectionView.of(connections.save(connection));
    }

    private PlaidConnector plaidConnector(String providerId) {
        BankProvider provider = BankProvider.valueOf(providerId);
        BankConnector connector = service.connectorFor(provider);
        if (!(connector instanceof PlaidConnector plaid)) {
            throw new IllegalArgumentException(provider + " is not an aggregator-backed provider");
        }
        return plaid;
    }

    private static String displayNameOr(String provided, String fallback) {
        return provided != null && !provided.isBlank() ? provided.trim() : fallback;
    }

    // --- DTOs ---

    /** Secret-free projection of a {@link BankConnection} for the API. */
    public record ConnectionView(Long id, String provider, String providerName, String displayName,
                                 String status, Instant lastSyncedAt, String lastError) {
        static ConnectionView of(BankConnection c) {
            return new ConnectionView(c.getId(), c.getProvider().name(), c.getProvider().getDisplayName(),
                    c.getDisplayName(), c.getStatus().name(), c.getLastSyncedAt(), c.getLastError());
        }
    }

    public record AuthorizeUrl(String url) {
    }

    public record Trading212Request(String apiKey, String displayName) {
    }

    public record PlaidLinkRequest(String provider) {
    }

    public record LinkTokenResponse(String linkToken, String error) {
    }

    public record PlaidExchangeRequest(String provider, String publicToken, String displayName) {
    }
}
