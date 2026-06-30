package com.budget.category;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategorizerTest {

    @Autowired
    private Categorizer categorizer;

    @Test
    void keywordFallbackStillWorks() {
        // With no provider category and no learned mapping, the built-in keyword list is used (old behaviour).
        assertEquals("Groceries", categorizer.categorize("TESCO STORES 2841", null));
        assertEquals("Uncategorized", categorizer.categorize("MYSTERY MERCHANT LTD", null));
    }

    @Test
    void providerCategoryBeatsKeywords() {
        // "AMAZON" would hit the Shopping keyword, but Monzo says it's groceries here — trust the provider.
        assertEquals("Groceries", categorizer.categorize("AMAZON FRESH", "groceries"));
    }

    @Test
    void learnedCorrectionBeatsEverything() {
        // A merchant the keyword list would call Groceries...
        assertEquals("Groceries", categorizer.categorize("TESCO STORES 2841", null));

        // ...once the user recategorises it, their choice wins for that merchant from then on,
        // even over a provider category.
        categorizer.learn("TESCO STORES 2841 LONDON GB", "Shopping");
        assertEquals("Shopping", categorizer.categorize("TESCO STORES 0019 MANCHESTER GB", "groceries"));
    }

    @Test
    void relearningUpdatesTheMapping() {
        categorizer.learn("BLUE CAFE", "Eating Out");
        assertEquals("Eating Out", categorizer.categorize("SQ *BLUE CAFE", null));
        categorizer.learn("BLUE CAFE", "Entertainment");
        assertEquals("Entertainment", categorizer.categorize("SQ *BLUE CAFE", null));
    }
}
