package com.budget.statement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "statements")
public class Statement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    protected Statement() {
    }

    public Statement(String originalFilename, int rowCount) {
        this.originalFilename = originalFilename;
        this.rowCount = rowCount;
    }

    public Long getId() {
        return id;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public int getRowCount() {
        return rowCount;
    }
}
