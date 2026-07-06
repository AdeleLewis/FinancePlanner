package com.budget.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The user's keep/cancel choice for one detected subscription, keyed by the normalised merchant key so the
 * decision survives re-detection runs and new charges from the same merchant.
 */
@Entity
@Table(name = "subscription_decisions")
public class SubscriptionDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_key", nullable = false, unique = true, length = 128)
    private String merchantKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Decision decision;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected SubscriptionDecision() {
    }

    public SubscriptionDecision(String merchantKey, Decision decision) {
        this.merchantKey = merchantKey;
        this.decision = decision;
        this.decidedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getMerchantKey() {
        return merchantKey;
    }

    public Decision getDecision() {
        return decision;
    }

    public void setDecision(Decision decision) {
        this.decision = decision;
        this.decidedAt = Instant.now();
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
