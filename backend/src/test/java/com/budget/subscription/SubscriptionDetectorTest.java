package com.budget.subscription;

import com.budget.transaction.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionDetectorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 6);

    @Test
    void detect_monthlyChargesAtStableAmount_isReportedAsSubscription() {
        final List<Transaction> transactions = List.of(
                charge("NETFLIX.COM", "-12.99", TODAY.minusMonths(3)),
                charge("NETFLIX.COM", "-12.99", TODAY.minusMonths(2)),
                charge("NETFLIX.COM", "-12.99", TODAY.minusMonths(1)));

        final List<DetectedSubscription> detected = SubscriptionDetector.detect(transactions, TODAY);

        assertThat(detected).hasSize(1);
        final DetectedSubscription subscription = detected.get(0);
        assertThat(subscription.name()).isEqualTo("Netflix Com");
        assertThat(subscription.monthlyAmount()).isEqualByComparingTo("12.99");
        assertThat(subscription.timesCharged()).isEqualTo(3);
        assertThat(subscription.lastChargedOn()).isEqualTo(TODAY.minusMonths(1));
        assertThat(subscription.nextExpectedOn()).isAfter(TODAY.minusDays(10));
    }

    @Test
    void detect_irregularSpendingAtSameMerchant_isNotASubscription() {
        // Weekly-ish shopping: the gaps are far too short to be a monthly subscription.
        final List<Transaction> transactions = List.of(
                charge("TESCO STORES 2841", "-54.20", TODAY.minusDays(40)),
                charge("TESCO STORES 2841", "-31.75", TODAY.minusDays(33)),
                charge("TESCO STORES 2841", "-62.10", TODAY.minusDays(21)),
                charge("TESCO STORES 2841", "-48.90", TODAY.minusDays(9)));

        final List<DetectedSubscription> detected = SubscriptionDetector.detect(transactions, TODAY);

        assertThat(detected).isEmpty();
    }

    @Test
    void detect_amountsDriftingBeyondTolerance_isNotASubscription() {
        final List<Transaction> transactions = List.of(
                charge("ELECTRICITY CO", "-60.00", TODAY.minusMonths(3)),
                charge("ELECTRICITY CO", "-95.00", TODAY.minusMonths(2)),
                charge("ELECTRICITY CO", "-140.00", TODAY.minusMonths(1)));

        final List<DetectedSubscription> detected = SubscriptionDetector.detect(transactions, TODAY);

        assertThat(detected).isEmpty();
    }

    @Test
    void detect_smallPriceRise_staysDetectedWithLatestAmount() {
        final List<Transaction> transactions = List.of(
                charge("SPOTIFY", "-9.99", TODAY.minusMonths(3)),
                charge("SPOTIFY", "-9.99", TODAY.minusMonths(2)),
                charge("SPOTIFY", "-11.99", TODAY.minusMonths(1)));

        final List<DetectedSubscription> detected = SubscriptionDetector.detect(transactions, TODAY);

        assertThat(detected).hasSize(1);
        assertThat(detected.get(0).monthlyAmount()).isEqualByComparingTo("11.99");
    }

    @Test
    void detect_lapsedSubscription_isNotReported() {
        // Regular and stable, but the last charge is months old — nothing left to cancel.
        final List<Transaction> transactions = List.of(
                charge("OLD GYM", "-25.00", TODAY.minusMonths(6)),
                charge("OLD GYM", "-25.00", TODAY.minusMonths(5)),
                charge("OLD GYM", "-25.00", TODAY.minusMonths(4)));

        final List<DetectedSubscription> detected = SubscriptionDetector.detect(transactions, TODAY);

        assertThat(detected).isEmpty();
    }

    @Test
    void detect_incomingPayments_areNeverSubscriptions() {
        final List<Transaction> transactions = List.of(
                charge("ACME PAYROLL", "3200.00", TODAY.minusMonths(2)),
                charge("ACME PAYROLL", "3200.00", TODAY.minusMonths(1)));

        final List<DetectedSubscription> detected = SubscriptionDetector.detect(transactions, TODAY);

        assertThat(detected).isEmpty();
    }

    @Test
    void detect_resultsAreSortedByMonthlyCostDescending() {
        final List<Transaction> transactions = List.of(
                charge("NETFLIX.COM", "-12.99", TODAY.minusMonths(2)),
                charge("NETFLIX.COM", "-12.99", TODAY.minusMonths(1)),
                charge("PUREGYM LTD", "-32.99", TODAY.minusMonths(2)),
                charge("PUREGYM LTD", "-32.99", TODAY.minusMonths(1)));

        final List<DetectedSubscription> detected = SubscriptionDetector.detect(transactions, TODAY);

        assertThat(detected).extracting(DetectedSubscription::name)
                .containsExactly("Puregym Ltd", "Netflix Com");
    }

    @Test
    void merchantKey_domainOnlyMerchant_fallsBackInsteadOfVanishing() {
        // MerchantNormalizer strips whole domains, which would leave "NETFLIX.COM" empty.
        assertThat(SubscriptionDetector.merchantKey("NETFLIX.COM")).isEqualTo("NETFLIX COM");
    }

    private static Transaction charge(final String description, final String amount, final LocalDate date) {
        final Transaction transaction = new Transaction(date, description, new BigDecimal(amount));
        transaction.setCategory("Subscriptions");
        return transaction;
    }
}
