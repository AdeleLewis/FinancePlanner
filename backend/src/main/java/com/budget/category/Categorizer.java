package com.budget.category;

import com.budget.transaction.Transaction;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class Categorizer {

    public static final String UNCATEGORIZED = "Uncategorized";

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

    public String categorize(String description) {
        if (description == null) {
            return UNCATEGORIZED;
        }
        String upper = description.toUpperCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : RULES.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (upper.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        return UNCATEGORIZED;
    }

    public void apply(Transaction transaction) {
        transaction.setCategory(categorize(transaction.getDescription()));
    }
}
