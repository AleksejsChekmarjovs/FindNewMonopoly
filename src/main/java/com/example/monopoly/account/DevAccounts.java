package com.example.monopoly.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Профиль dev: тестовые аккаунты из monopoly.dev-accounts с паролем test123 — чтобы играть в нескольких вкладках. */
@Component
@Profile("dev")
class DevAccounts implements ApplicationRunner {

    static final String PASSWORD = "test123";

    private final AccountService accounts;
    private final String[] usernames;

    DevAccounts(AccountService accounts, @Value("${monopoly.dev-accounts:}") String[] usernames) {
        this.accounts = accounts;
        this.usernames = usernames;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String username : usernames) {
            if (!username.isBlank()) {
                accounts.ensureAccount(username.strip(), PASSWORD);
            }
        }
    }
}
