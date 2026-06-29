package com.budget.connection;

import com.budget.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A linked account for one {@link BankProvider}, holding the secrets and state needed to sync it.
 *
 * <p>The secret columns ({@link #accessToken}, {@link #refreshToken}, {@link #apiKey}) are encrypted at
 * rest via {@link EncryptedStringConverter} (AES-256-GCM, key from the environment) so a stolen DB file
 * does not expose tokens. Set {@code APP_ENCRYPTION_KEY} to enable it; without a key they fall back to
 * plaintext with a startup warning. Columns are sized generously to hold the base64 ciphertext.
 */
@Entity
@Table(name = "bank_connections")
public class BankConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BankProvider provider;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConnectionStatus status = ConnectionStatus.PENDING;

    /** OAuth bearer token (Monzo) — null for other auth types. Encrypted at rest. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "access_token", length = 4096)
    private String accessToken;

    /** OAuth refresh token (Monzo confidential client) — null otherwise. Encrypted at rest. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "refresh_token", length = 4096)
    private String refreshToken;

    /** When {@link #accessToken} expires, for proactive refresh. */
    @Column(name = "access_token_expires_at")
    private Instant accessTokenExpiresAt;

    /** Static API key (Trading 212) or aggregator access token (Plaid item) — null otherwise. Encrypted at rest. */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "api_key", length = 4096)
    private String apiKey;

    /** Provider-side account/item identifier scoped by the connector (e.g. Monzo account id). */
    @Column(name = "external_account_id", length = 128)
    private String externalAccountId;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected BankConnection() {
    }

    public BankConnection(BankProvider provider, String displayName) {
        this.provider = provider;
        this.displayName = displayName;
    }

    public Long getId() {
        return id;
    }

    public BankProvider getProvider() {
        return provider;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public ConnectionStatus getStatus() {
        return status;
    }

    public void setStatus(ConnectionStatus status) {
        this.status = status;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Instant getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public void setAccessTokenExpiresAt(Instant accessTokenExpiresAt) {
        this.accessTokenExpiresAt = accessTokenExpiresAt;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getExternalAccountId() {
        return externalAccountId;
    }

    public void setExternalAccountId(String externalAccountId) {
        this.externalAccountId = externalAccountId;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
