package com.budget.category;

import java.util.Locale;

/**
 * Reduces a noisy bank description to a stable "merchant key" so that the same shop is recognised across
 * transactions and across providers.
 *
 * <p>Raw descriptions carry a lot of per-transaction noise — store numbers, locations, card-network
 * prefixes, dates, reference codes — e.g. {@code "TESCO STORES 2841 LONDON GB"}, {@code "SQ *BLUE CAFE"},
 * {@code "AMZNMKTPLACE*A1B2C3 amazon.co.uk"}. Matching or learning on the raw string is unreliable; matching
 * on the cleaned key ({@code "TESCO STORES"}, {@code "BLUE CAFE"}, {@code "AMZNMKTPLACE"}) is far steadier.
 *
 * <p>This is deliberately conservative: it strips obvious noise without trying to be clever, because an
 * over-aggressive cleaner that collapses distinct merchants together would mislearn categories. The goal is
 * a key that's the same for repeat visits to one merchant, not a perfectly human-readable name.
 */
public final class MerchantNormalizer {

    private MerchantNormalizer() {
    }

    public static String normalize(String description) {
        if (description == null) {
            return "";
        }
        String s = description.toUpperCase(Locale.ROOT);

        // Drop common card-network / processor prefixes that sit in front of the real merchant.
        s = s.replaceAll("\\b(SQ|SQUARE|SUMUP|IZ|IZETTLE|ZTL|PAYPAL|PP|TST|WWW|VISA|POS|CRD)\\s*\\*", " ");
        s = s.replace("*", " ");

        // Remove URLs/domains and country/currency tails that ride along on card payments.
        s = s.replaceAll("\\b[A-Z0-9-]+\\.(CO\\.UK|COM|NET|ORG|UK)\\b", " ");
        s = s.replaceAll("\\b(GB|GBR|UK|USA|US|EUR|GBP)\\b", " ");

        // Strip digits and the symbols/punctuation that usually mark store numbers, dates and refs.
        s = s.replaceAll("[0-9]+", " ");
        s = s.replaceAll("[#/\\\\.,:;@&()\\[\\]_+-]", " ");

        // Collapse whitespace.
        s = s.replaceAll("\\s+", " ").trim();

        // Keep just the leading, most-identifying words. Trailing tokens are almost always location/branch
        // noise (the town the card was used in), so two words is enough to identify the merchant while
        // collapsing repeat visits to different branches onto the same key.
        StringBuilder key = new StringBuilder();
        int kept = 0;
        for (String word : s.split(" ")) {
            if (word.length() < 2) {
                continue;   // drop stray single letters left over from stripping
            }
            if (kept > 0) {
                key.append(' ');
            }
            key.append(word);
            if (++kept == 2) {
                break;
            }
        }
        return key.length() > 0 ? key.toString() : s;
    }
}
