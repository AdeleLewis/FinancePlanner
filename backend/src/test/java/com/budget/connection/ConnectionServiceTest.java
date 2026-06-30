package com.budget.connection;

import com.budget.category.Categorizer;
import com.budget.connection.ConnectionService.SyncResult;
import com.budget.transaction.Transaction;
import com.budget.transaction.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConnectionServiceTest {

    @Autowired
    private BankConnectionRepository connections;

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private Categorizer categorizer;

    private FakeConnector connector;
    private ConnectionService service;

    @BeforeEach
    void setUp() {
        connector = new FakeConnector();
        service = new ConnectionService(List.of(connector), connections, transactions, categorizer);
    }

    @Test
    void syncImportsCategorizesAndTagsSource() {
        BankConnection connection = connections.save(new BankConnection(BankProvider.MONZO, "Monzo"));
        connector.next = List.of(
                new NormalizedTransaction("tx-1", LocalDate.now(), "TESCO STORES", new BigDecimal("-12.50")),
                new NormalizedTransaction("tx-2", LocalDate.now(), "SALARY", new BigDecimal("2000.00")));

        SyncResult result = service.sync(connection.getId());

        assertTrue(result.success());
        assertEquals(2, result.imported());
        List<Transaction> saved = transactions.findAll();
        assertEquals(2, saved.size());
        assertTrue(saved.stream().allMatch(t -> "MONZO".equals(t.getSource())));
        assertTrue(saved.stream().anyMatch(t -> "Groceries".equals(t.getCategory())));
        assertTrue(saved.stream().anyMatch(t -> "Income".equals(t.getCategory())));
        assertEquals(ConnectionStatus.CONNECTED, connections.findById(connection.getId()).orElseThrow().getStatus());
    }

    @Test
    void reSyncSkipsAlreadyImportedExternalIds() {
        BankConnection connection = connections.save(new BankConnection(BankProvider.MONZO, "Monzo"));
        connector.next = List.of(
                new NormalizedTransaction("tx-1", LocalDate.now(), "TESCO", new BigDecimal("-5.00")));
        service.sync(connection.getId());

        // Same id returns again plus one new id — only the new one should be imported.
        connector.next = List.of(
                new NormalizedTransaction("tx-1", LocalDate.now(), "TESCO", new BigDecimal("-5.00")),
                new NormalizedTransaction("tx-2", LocalDate.now(), "ALDI", new BigDecimal("-7.00")));
        SyncResult result = service.sync(connection.getId());

        assertEquals(1, result.imported());
        assertEquals(2, transactions.count());
    }

    @Test
    void reauthErrorMovesConnectionToNeedsReauth() {
        BankConnection connection = connections.save(new BankConnection(BankProvider.MONZO, "Monzo"));
        connector.failure = new ConnectorException("token expired", true);

        SyncResult result = service.sync(connection.getId());

        assertFalse(result.success());
        assertEquals(0, transactions.count());
        BankConnection reloaded = connections.findById(connection.getId()).orElseThrow();
        assertEquals(ConnectionStatus.NEEDS_REAUTH, reloaded.getStatus());
        assertEquals("token expired", reloaded.getLastError());
    }

    @Test
    void successfulSyncClearsPreviousError() {
        BankConnection connection = connections.save(new BankConnection(BankProvider.MONZO, "Monzo"));
        connection.setLastError("old failure");
        connections.save(connection);
        connector.next = List.of();

        service.sync(connection.getId());

        assertNull(connections.findById(connection.getId()).orElseThrow().getLastError());
    }

    /** Test double standing in for a real provider connector. */
    private static final class FakeConnector implements BankConnector {
        private List<NormalizedTransaction> next = List.of();
        private ConnectorException failure;

        @Override
        public BankProvider provider() {
            return BankProvider.MONZO;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public List<NormalizedTransaction> fetch(BankConnection connection, LocalDate from) {
            if (failure != null) {
                throw failure;
            }
            return next;
        }
    }
}
