package com.budget.subscription;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One recurring monthly payment found in the transaction history. */
public record DetectedSubscription(
        String merchantKey,
        String name,
        String category,
        BigDecimal monthlyAmount,
        LocalDate lastChargedOn,
        LocalDate nextExpectedOn,
        int timesCharged) {
}
