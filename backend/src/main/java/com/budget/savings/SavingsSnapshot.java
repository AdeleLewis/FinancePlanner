package com.budget.savings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "savings_snapshots")
public class SavingsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "account_name", length = 128)
    private String accountName;

    protected SavingsSnapshot() {
    }

    public SavingsSnapshot(BigDecimal amount, String accountName) {
        this.amount = amount;
        this.accountName = accountName;
    }

    public Long getId() {
        return id;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getAccountName() {
        return accountName;
    }
}
