package com.budget.category;

import com.budget.transaction.Transaction;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Assigns a spending category to a transaction using a layered pipeline, strongest signal first:
 *
 * <ol>
 *   <li><b>Learned</b> — a merchant the user has corrected before ({@link MerchantCategoryRepository}).
 *       Most trusted: it's the user's own decision, so it overrides everything.</li>
 *   <li><b>Provider</b> — the bank/aggregator's own classification ({@link ProviderCategoryMapper}). The
 *       providers classify with merchant databases and MCC codes we don't have locally.</li>
 *   <li><b>Keywords</b> — a built-in merchant-name list (below). A fallback for CSV rows and providers that
 *       give no category; deliberately last because it's the crudest signal.</li>
 *   <li><b>Uncategorized</b> — nothing matched.</li>
 * </ol>
 *
 * <p>The point of the ordering is that the system improves over time and respects richer signals: a generic
 * keyword can never beat the user's explicit correction or the bank's own label. The old behaviour (keywords
 * only) is preserved as the final stage, so nothing regresses.
 */
@Component
public class Categorizer {

    public static final String UNCATEGORIZED = "Uncategorized";

    /** The categories the UI offers when recategorising. Ordered roughly by how often they're used. */
    public static final List<String> KNOWN_CATEGORIES = List.of(
            "Income", "Groceries", "Eating Out", "Transport", "Utilities",
            "Shopping", "Entertainment", "Transfers", "Fees", UNCATEGORIZED);

    // Iteration order matters: more-specific categories are checked before less-specific ones
    // so e.g. "AMAZON PRIME" matches Entertainment before "AMAZON" matches Shopping.
    private static final Map<String, List<String>> RULES;

    static {
        Map<String, List<String>> rules = new LinkedHashMap<>();
        rules.put("Entertainment", List.of("NETFLIX", "SPOTIFY", "AMAZON PRIME", "DISNEY+", "CINEMA", "ODEON", "VUE", "STEAM"));
        rules.put("Eating Out", List.of("STARBUCKS", "COSTA", "PRET", "MCDONALD", "KFC", "NANDO", "CAFFE NERO", "GREGGS", "DOMINOS", "DELIVEROO", "UBEREATS", "JUST EAT"));
        rules.put("Income", List.of("SALARY", "PAYROLL", "WAGES", "INTEREST PAID"));
        rules.put("Utilities", List.of("BRITISH GAS", "BULB", "OCTOPUS ENERGY", "THAMES WATER", "EDF", "E.ON", "BT ", "VIRGIN MEDIA", "SKY ", "VODAFONE", "EE ", "O2 "));
        rules.put("Transport", List.of("TFL", "UBER", "BOLT", "RAILWAY", "TRAINLINE", "BP ", "SHELL", "ESSO", "ZIPCAR"));
        rules.put("Groceries", List.of("TESCO", "SAINSBURY", "WAITROSE", "ASDA", "MORRISONS", "LIDL", "ALDI", "M&S FOOD", "ICELAND", "CO-OP"));
        rules.put("Shopping", List.of("AMAZON", "ARGOS", "JOHN LEWIS", "IKEA", "BOOTS", "SUPERDRUG"));
        RULES = Collections.unmodifiableMap(rules);
    }

    private final MerchantCategoryRepository learnedCategories;

    public Categorizer(MerchantCategoryRepository learnedCategories) {
        this.learnedCategories = learnedCategories;
    }

    /** Run the full pipeline using both the description and any provider-supplied category. */
    public String categorize(String description, String providerCategory) {
        // 1. Learned: has the user corrected this merchant before?
        String learned = lookupLearned(description);
        if (learned != null) {
            return learned;
        }
        // 2. Provider: trust the bank/aggregator's own classification.
        String mapped = ProviderCategoryMapper.map(providerCategory);
        if (mapped != null) {
            return mapped;
        }
        // 3. Keywords: built-in merchant-name fallback.
        String byKeyword = matchKeyword(description);
        if (byKeyword != null) {
            return byKeyword;
        }
        // 4. Nothing matched.
        return UNCATEGORIZED;
    }

    /** Convenience for callers with no provider category (e.g. CSV imports). */
    public String categorize(String description) {
        return categorize(description, null);
    }

    public void apply(Transaction transaction) {
        transaction.setCategory(categorize(transaction.getDescription(), transaction.getProviderCategory()));
    }

    /**
     * Remember the user's correction: future transactions from the same merchant get this category. Returns
     * the normalised merchant key that was learned (empty if the description had nothing to key on).
     */
    public String learn(String description, String category) {
        String merchantKey = MerchantNormalizer.normalize(description);
        if (merchantKey.isBlank()) {
            return merchantKey;
        }
        learnedCategories.findByMerchantKey(merchantKey)
                .ifPresentOrElse(
                        existing -> {
                            existing.setCategory(category);
                            learnedCategories.save(existing);
                        },
                        () -> learnedCategories.save(new MerchantCategory(merchantKey, category)));
        return merchantKey;
    }

    private String lookupLearned(String description) {
        String merchantKey = MerchantNormalizer.normalize(description);
        if (merchantKey.isBlank()) {
            return null;
        }
        return learnedCategories.findByMerchantKey(merchantKey)
                .map(MerchantCategory::getCategory)
                .orElse(null);
    }

    private String matchKeyword(String description) {
        if (description == null) {
            return null;
        }
        String upper = description.toUpperCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : RULES.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (upper.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }
}
