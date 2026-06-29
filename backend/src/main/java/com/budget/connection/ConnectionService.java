package com.budget.connection;

import com.budget.category.Categorizer;
import com.budget.transaction.Transaction;
import com.budget.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates linked-account syncs: pick the connector for a connection, fetch new transactions,
 * de-duplicate against what we've already imported, categorise, and persist. Reuses the same
 * {@link Categorizer} and {@link TransactionRepository} as the CSV path so imported rows behave
 * identically downstream.
 */
@Service
public class ConnectionService {

    /** How far back to pull on each sync — also the Open Banking consent/history ceiling. */
    private static final int SYNC_WINDOW_DAYS = 90;

    private final Map<BankProvider, BankConnector> connectorsByProvider = new EnumMap<>(BankProvider.class);
    private final BankConnectionRepository connections;
    private final TransactionRepository transactions;
    private final Categorizer categorizer;

    public ConnectionService(List<BankConnector> connectors,
                             BankConnectionRepository connections,
                             TransactionRepository transactions,
                             Categorizer categorizer) {
        connectors.forEach(connector -> connectorsByProvider.put(connector.provider(), connector));
        this.connections = connections;
        this.transactions = transactions;
        this.categorizer = categorizer;
    }

    public BankConnector connectorFor(BankProvider provider) {
        BankConnector connector = connectorsByProvider.get(provider);
        if (connector == null) {
            throw new IllegalArgumentException("No connector registered for provider " + provider);
        }
        return connector;
    }

    /** Provider catalogue for the UI: metadata + whether the deployment can connect each one. */
    public List<ProviderInfo> listProviders() {
        List<ProviderInfo> result = new ArrayList<>();
        for (BankProvider provider : BankProvider.values()) {
            BankConnector connector = connectorsByProvider.get(provider);
            boolean configured = connector != null && connector.isConfigured();
            result.add(new ProviderInfo(provider.name(), provider.getDisplayName(),
                    provider.getAuthType().name(), provider.getDescription(), configured));
        }
        return result;
    }

    public List<BankConnection> listConnections() {
        return connections.findAll();
    }

    public BankConnection getConnection(Long id) {
        return connections.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No connection with id " + id));
    }

    public void deleteConnection(Long id) {
        connections.deleteById(id);
    }

    /**
     * Sync one connection. Returns how many new transactions were imported. Marks the connection
     * CONNECTED on success, NEEDS_REAUTH or ERROR on failure (and never throws for those — the result
     * carries the message), so the UI can render per-connection status.
     */
    @Transactional
    public SyncResult sync(Long connectionId) {
        BankConnection connection = getConnection(connectionId);
        BankConnector connector = connectorFor(connection.getProvider());
        String source = connection.getProvider().getSource();

        try {
            List<NormalizedTransaction> fetched = connector.fetch(connection, syncFrom(connection));
            Set<String> seen = new HashSet<>(transactions.findExternalIdsBySource(source));

            List<Transaction> toSave = new ArrayList<>();
            for (NormalizedTransaction tx : fetched) {
                if (tx.externalId() != null && !seen.add(tx.externalId())) {
                    continue;   // already imported, or a duplicate within this batch
                }
                Transaction entity = new Transaction(tx.date(), tx.description(), tx.amount(), source, tx.externalId());
                categorizer.apply(entity);
                toSave.add(entity);
            }
            transactions.saveAll(toSave);

            connection.setStatus(ConnectionStatus.CONNECTED);
            connection.setLastSyncedAt(Instant.now());
            connection.setLastError(null);
            connections.save(connection);
            return new SyncResult(connectionId, toSave.size(), true, null);
        } catch (ConnectorException ex) {
            connection.setStatus(ex.isReauthRequired() ? ConnectionStatus.NEEDS_REAUTH : ConnectionStatus.ERROR);
            connection.setLastError(ex.getMessage());
            connections.save(connection);
            return new SyncResult(connectionId, 0, false, ex.getMessage());
        }
    }

    private LocalDate syncFrom(BankConnection connection) {
        LocalDate floor = LocalDate.now().minusDays(SYNC_WINDOW_DAYS);
        if (connection.getLastSyncedAt() == null) {
            return floor;
        }
        LocalDate lastSync = connection.getLastSyncedAt().atZone(ZoneOffset.UTC).toLocalDate();
        return lastSync.isAfter(floor) ? lastSync : floor;
    }

    public record ProviderInfo(String id, String displayName, String authType, String description,
                               boolean configured) {
    }

    public record SyncResult(Long connectionId, int imported, boolean success, String error) {
    }
}
