package com.budget.connection;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single transaction as returned by a {@link BankConnector}, normalised across providers before it
 * is categorised and persisted as a {@code Transaction}.
 *
 * @param externalId       provider-native id, used to de-duplicate repeated syncs (must be stable)
 * @param date             the date the transaction settled/occurred
 * @param description      human-readable description (merchant, reference, counterparty)
 * @param amount           signed amount: negative = money out, positive = money in
 * @param providerCategory the provider's own classification (Monzo {@code category}, Plaid
 *                         {@code personal_finance_category}, …), or null if the provider gives none.
 *                         Used as a strong categorisation signal — the banks already classify well.
 */
public record NormalizedTransaction(String externalId, LocalDate date, String description, BigDecimal amount,
                                    String providerCategory) {

    /** Convenience for connectors/tests that have no provider category to pass. */
    public NormalizedTransaction(String externalId, LocalDate date, String description, BigDecimal amount) {
        this(externalId, date, description, amount, null);
    }
}
