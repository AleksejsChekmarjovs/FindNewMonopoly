package com.example.monopoly.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Вход в аккаунт: по токену из браузера находим игрока. В базе лежит только хеш токена. */
@Entity
@Table(name = "auth_session")
public class AuthSession {

    @Id
    @Column(name = "token_hash")
    private String tokenHash;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected AuthSession() {
    }

    AuthSession(String tokenHash, Account account, Instant createdAt, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.account = account;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    Account account() { return account; }
    Instant expiresAt() { return expiresAt; }
}
