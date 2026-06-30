package com.budget.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 512)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 64)
    private String category = "Uncategorized";

    @Column(name = "statement_id")
    private Long statementId;

    /** Where this row came from, e.g. "CSV", "MONZO", "TRADING212". Null for legacy CSV rows. */
    @Column(length = 32)
    private String source;

    /** Provider-native transaction id, used to de-duplicate repeated API syncs. Null for CSV rows. */
    @Column(name = "external_id", length = 128)
    private String externalId;

    /** The provider's own category (Monzo/Plaid), kept so re-categorisation can reuse it. Null for CSV rows. */
    @Column(name = "provider_category", length = 64)
    private String providerCategory;

    /** True once the user has manually set the category, so bulk re-categorisation won't overwrite it. */
    @Column(name = "user_categorized", nullable = false)
    private boolean userCategorized = false;

    protected Transaction() {
    }

    public Transaction(LocalDate date, String description, BigDecimal amount) {
        this.date = date;
        this.description = description;
        this.amount = amount;
    }

    public Transaction(LocalDate date, String description, BigDecimal amount, String source, String externalId) {
        this(date, description, amount);
        this.source = source;
        this.externalId = externalId;
    }

    public Transaction(LocalDate date, String description, BigDecimal amount, String source, String externalId,
                       String providerCategory) {
        this(date, description, amount, source, externalId);
        this.providerCategory = providerCategory;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getStatementId() {
        return statementId;
    }

    public void setStatementId(Long statementId) {
        this.statementId = statementId;
    }

    public String getSource() {
        return source;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getProviderCategory() {
        return providerCategory;
    }

    public void setProviderCategory(String providerCategory) {
        this.providerCategory = providerCategory;
    }

    public boolean isUserCategorized() {
        return userCategorized;
    }

    public void setUserCategorized(boolean userCategorized) {
        this.userCategorized = userCategorized;
    }
}
