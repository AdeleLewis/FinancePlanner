package com.budget.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The single owner's login credential. Only a one-way password hash (BCrypt via Spring's delegating
 * encoder) is ever stored — the password itself never touches the database, so the hash is useless to
 * anyone who steals the DB file.
 *
 * <p>Also holds the encryption vault: the random data key that protects bank secrets, stored only in
 * wrapped (password-encrypted) form next to the PBKDF2 salt used to derive the wrapping key. Neither
 * column is usable without the password. Rows created before the vault existed have both columns null;
 * they are filled in on the first successful login (see {@code AuthService}).
 */
@Entity
@Table(name = "user_credential")
public class UserCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** Base64 PBKDF2 salt for deriving the key-wrapping key from the password. */
    @Column(name = "kek_salt", length = 64)
    private String kekSalt;

    /** Base64 of the data key, AES-GCM-encrypted under the password-derived key. */
    @Column(name = "wrapped_dek", length = 128)
    private String wrappedDek;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserCredential() {
    }

    public UserCredential(String passwordHash) {
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getKekSalt() {
        return kekSalt;
    }

    public String getWrappedDek() {
        return wrappedDek;
    }

    public boolean hasVault() {
        return kekSalt != null && wrappedDek != null;
    }

    public void attachVault(String kekSalt, String wrappedDek) {
        this.kekSalt = kekSalt;
        this.wrappedDek = wrappedDek;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
