package com.budget.connection;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single transaction as returned by a {@link BankConnector}, normalised across providers before it
 * is categorised and persisted as a {@code Transaction}.
 *
 * @param externalId  provider-native id, used to de-duplicate repeated syncs (must be stable)
 * @param date        the date the transaction settled/occurred
 * @param description human-readable description (merchant, reference, counterparty)
 * @param amount      signed amount: negative = money out, positive = money in
 */
public record NormalizedTransaction(String externalId, LocalDate date, String description, BigDecimal amount) {
}
