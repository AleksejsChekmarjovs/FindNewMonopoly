package com.example.monopoly.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.Locale;

/** Аккаунт игрока. */
@Entity
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    /** Имя в нижнем регистре: «Alice» и «alice» — один аккаунт. */
    @Column(name = "username_key", nullable = false, unique = true)
    private String usernameKey;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {
    }

    Account(String username, String passwordHash, Instant createdAt) {
        this.username = username;
        this.usernameKey = key(username);
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    public Long id() { return id; }
    public String username() { return username; }
    String passwordHash() { return passwordHash; }
}
