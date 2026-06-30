package com.budget.category;

import java.util.Locale;
import java.util.Map;

/**
 * Translates a provider's own transaction category into one of our category names.
 *
 * <p>Monzo and Plaid already classify transactions (Monzo's {@code category}, Plaid's
 * {@code personal_finance_category.primary}) using far more signal than we have locally — merchant
 * databases, MCC codes, their own models. Honouring that classification is more accurate than any keyword
 * list we could maintain, so it sits high in the {@link Categorizer} pipeline. We only need to map their
 * vocabulary onto ours; anything we don't recognise returns null so the next pipeline stage can try.
 */
public final class ProviderCategoryMapper {

    /**
     * Provider tokens (upper-cased) → our category. Covers Monzo's snake_case values, Plaid's primary
     * enum, and the synthetic tokens the Trading 212 connector emits for cash movements.
     */
    private static final Map<String, String> MAPPING = Map.ofEntries(
            // --- Monzo (category) ---
            Map.entry("EATING_OUT", "Eating Out"),
            Map.entry("GROCERIES", "Groceries"),
            Map.entry("TRANSPORT", "Transport"),
            Map.entry("BILLS", "Utilities"),
            Map.entry("ENTERTAINMENT", "Entertainment"),
            Map.entry("SHOPPING", "Shopping"),
            Map.entry("HOLIDAYS", "Transport"),
            Map.entry("TRANSFERS", "Transfers"),
            Map.entry("INCOME", "Income"),
            Map.entry("SAVINGS", "Transfers"),

            // --- Plaid (personal_finance_category.primary) ---
            Map.entry("FOOD_AND_DRINK", "Eating Out"),
            Map.entry("GENERAL_MERCHANDISE", "Shopping"),
            Map.entry("TRANSPORTATION", "Transport"),
            Map.entry("TRAVEL", "Transport"),
            Map.entry("RENT_AND_UTILITIES", "Utilities"),
            Map.entry("TRANSFER_IN", "Transfers"),
            Map.entry("TRANSFER_OUT", "Transfers"),
            Map.entry("LOAN_PAYMENTS", "Fees"),
            Map.entry("BANK_FEES", "Fees"),

            // --- Trading 212 (synthetic, from transaction type) ---
            Map.entry("DIVIDEND", "Income"),
            Map.entry("INTEREST", "Income"),
            Map.entry("DEPOSIT", "Transfers"),
            Map.entry("WITHDRAWAL", "Transfers"),
            Map.entry("FEE", "Fees"));

    private ProviderCategoryMapper() {
    }

    /** Map a provider category to ours, or null if it's blank, "general"/"other", or unrecognised. */
    public static String map(String providerCategory) {
        if (providerCategory == null || providerCategory.isBlank()) {
            return null;
        }
        String key = providerCategory.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        // Plaid and Monzo both use catch-all buckets that carry no real signal — let the next stage decide.
        if (key.equals("GENERAL") || key.equals("OTHER") || key.equals("GENERAL_SERVICES")
                || key.equals("UNCATEGORIZED") || key.equals("EXPENSES")) {
            return null;
        }
        if (key.equals("ENTERTAINMENT")) {
            return "Entertainment";   // shared by Monzo + Plaid; handled here to avoid a duplicate map key
        }
        return MAPPING.get(key);
    }
}
