package com.budget.category;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProviderCategoryMapperTest {

    @Test
    void mapsMonzoCategories() {
        assertEquals("Eating Out", ProviderCategoryMapper.map("eating_out"));
        assertEquals("Groceries", ProviderCategoryMapper.map("groceries"));
        assertEquals("Utilities", ProviderCategoryMapper.map("bills"));
    }

    @Test
    void mapsPlaidPrimaryCategories() {
        assertEquals("Transport", ProviderCategoryMapper.map("TRANSPORTATION"));
        assertEquals("Shopping", ProviderCategoryMapper.map("GENERAL_MERCHANDISE"));
        assertEquals("Income", ProviderCategoryMapper.map("INCOME"));
    }

    @Test
    void entertainmentSharedByBothProviders() {
        assertEquals("Entertainment", ProviderCategoryMapper.map("entertainment"));
        assertEquals("Entertainment", ProviderCategoryMapper.map("ENTERTAINMENT"));
    }

    @Test
    void catchAllAndUnknownReturnNullSoNextStageDecides() {
        assertNull(ProviderCategoryMapper.map("general"));
        assertNull(ProviderCategoryMapper.map("OTHER"));
        assertNull(ProviderCategoryMapper.map("SOMETHING_WE_DONT_KNOW"));
        assertNull(ProviderCategoryMapper.map(null));
        assertNull(ProviderCategoryMapper.map(""));
    }
}
