package com.budget.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A learned merchant → category mapping, remembered from the user's own corrections.
 *
 * <p>When the user recategorises a transaction, we store the (normalised) merchant and the category they
 * chose here. Future transactions from the same merchant are then auto-categorised the user's way, so the
 * app fits the way <em>they</em> think about their spending rather than a generic keyword list. This is the
 * highest-trust signal in the {@link Categorizer} pipeline.
 */
@Entity
@Table(name = "merchant_categories")
public class MerchantCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Normalised merchant key (see {@link MerchantNormalizer}); unique so each merchant maps to one category. */
    @Column(name = "merchant_key", nullable = false, unique = true, length = 128)
    private String merchantKey;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected MerchantCategory() {
    }

    public MerchantCategory(String merchantKey, String category) {
        this.merchantKey = merchantKey;
        this.category = category;
    }

    public Long getId() {
        return id;
    }

    public String getMerchantKey() {
        return merchantKey;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
