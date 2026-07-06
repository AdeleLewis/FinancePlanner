package com.budget.subscription;

import com.budget.category.MerchantNormalizer;
import com.budget.transaction.Transaction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Finds monthly subscriptions in a transaction history by looking for repeated charges from the same
 * merchant at a roughly monthly cadence and a roughly stable amount.
 *
 * <p>The rules are deliberately strict so that variable spending (groceries, fuel) does not masquerade as a
 * subscription: every gap between consecutive charges must look monthly, the amounts may only drift within a
 * small tolerance (price rises happen), and the most recent charge must be recent enough for the
 * subscription to still be considered live.
 */
public final class SubscriptionDetector {

    private static final int MIN_CHARGES = 2;
    private static final long MIN_GAP_DAYS = 21;
    private static final long MAX_GAP_DAYS = 38;
    private static final long MAX_DAYS_SINCE_LAST_CHARGE = 45;
    /** Allow amounts within a group to drift by up to 25% of the largest charge. */
    private static final BigDecimal MAX_AMOUNT_SPREAD = new BigDecimal("0.25");

    private SubscriptionDetector() {
    }

    public static List<DetectedSubscription> detect(final List<Transaction> transactions, final LocalDate asOf) {
        final Map<String, List<Transaction>> byMerchant = new LinkedHashMap<>();
        for (final Transaction transaction : transactions) {
            if (transaction.getAmount().signum() >= 0) {
                continue;   // only outgoing money can be a subscription
            }
            final String key = merchantKey(transaction.getDescription());
            if (key.isEmpty()) {
                continue;
            }
            byMerchant.computeIfAbsent(key, k -> new ArrayList<>()).add(transaction);
        }

        final List<DetectedSubscription> result = new ArrayList<>();
        for (final Map.Entry<String, List<Transaction>> entry : byMerchant.entrySet()) {
            asSubscription(entry.getKey(), entry.getValue(), asOf).ifPresent(result::add);
        }
        result.sort(Comparator.comparing(DetectedSubscription::monthlyAmount).reversed());
        return result;
    }

    /**
     * The merchant key normally comes from {@link MerchantNormalizer}, but that cleaner strips whole
     * domains — and many subscription merchants are nothing but a domain ("NETFLIX.COM"). When it comes
     * back empty, fall back to the same cleaning minus the domain removal so those merchants still group.
     */
    static String merchantKey(final String description) {
        final String normalized = MerchantNormalizer.normalize(description);
        if (!normalized.isEmpty()) {
            return normalized;
        }
        if (description == null) {
            return "";
        }
        String s = description.toUpperCase(Locale.ROOT).replace('*', ' ');
        s = s.replaceAll("[0-9]+", " ");
        s = s.replaceAll("[#/\\\\.,:;@&()\\[\\]_+-]", " ");
        s = s.replaceAll("\\s+", " ").trim();
        final StringBuilder key = new StringBuilder();
        int kept = 0;
        for (final String word : s.split(" ")) {
            if (word.length() < 2) {
                continue;
            }
            if (kept > 0) {
                key.append(' ');
            }
            key.append(word);
            if (++kept == 2) {
                break;
            }
        }
        return key.toString();
    }

    private static Optional<DetectedSubscription> asSubscription(final String key, final List<Transaction> charges,
                                                                 final LocalDate asOf) {
        if (charges.size() < MIN_CHARGES) {
            return Optional.empty();
        }
        charges.sort(Comparator.comparing(Transaction::getDate));

        for (int i = 1; i < charges.size(); i++) {
            final long gap = ChronoUnit.DAYS.between(charges.get(i - 1).getDate(), charges.get(i).getDate());
            if (gap < MIN_GAP_DAYS || gap > MAX_GAP_DAYS) {
                return Optional.empty();
            }
        }

        BigDecimal min = null;
        BigDecimal max = null;
        for (final Transaction charge : charges) {
            final BigDecimal amount = charge.getAmount().abs();
            min = min == null || amount.compareTo(min) < 0 ? amount : min;
            max = max == null || amount.compareTo(max) > 0 ? amount : max;
        }
        final BigDecimal spread = max.subtract(min).divide(max, 4, RoundingMode.HALF_UP);
        if (spread.compareTo(MAX_AMOUNT_SPREAD) > 0) {
            return Optional.empty();
        }

        final Transaction latest = charges.get(charges.size() - 1);
        if (latest.getDate().isBefore(asOf.minusDays(MAX_DAYS_SINCE_LAST_CHARGE))) {
            return Optional.empty();    // lapsed — nothing left to cancel
        }

        final long spanDays = ChronoUnit.DAYS.between(charges.get(0).getDate(), latest.getDate());
        final long averageGap = Math.round((double) spanDays / (charges.size() - 1));
        return Optional.of(new DetectedSubscription(
                key,
                displayName(key),
                latest.getCategory(),
                latest.getAmount().abs(),
                latest.getDate(),
                latest.getDate().plusDays(averageGap),
                charges.size()));
    }

    private static String displayName(final String key) {
        final StringBuilder name = new StringBuilder(key.length());
        for (final String word : key.split(" ")) {
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return name.toString();
    }
}
