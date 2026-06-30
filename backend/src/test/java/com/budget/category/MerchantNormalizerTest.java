package com.budget.category;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantNormalizerTest {

    @Test
    void stripsStoreNumbersAndLocationNoise() {
        // The same shop visited twice should produce the same key despite differing branch/location noise.
        String a = MerchantNormalizer.normalize("TESCO STORES 2841 LONDON GB");
        String b = MerchantNormalizer.normalize("TESCO STORES 0019 MANCHESTER GB");
        assertEquals(a, b);
        assertTrue(a.startsWith("TESCO"));
    }

    @Test
    void stripsCardProcessorPrefix() {
        assertEquals("BLUE CAFE", MerchantNormalizer.normalize("SQ *BLUE CAFE"));
    }

    @Test
    void stripsDomainTails() {
        String key = MerchantNormalizer.normalize("AMZNMKTPLACE amazon.co.uk");
        assertEquals("AMZNMKTPLACE", key);
    }

    @Test
    void handlesNullAndBlank() {
        assertEquals("", MerchantNormalizer.normalize(null));
        assertEquals("", MerchantNormalizer.normalize("   "));
    }
}
