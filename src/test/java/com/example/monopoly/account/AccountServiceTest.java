package com.example.monopoly.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Аккаунты на настоящей базе (H2 в памяти, схема из миграций Flyway). Каждый тест откатывается. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountServiceTest {

    @Autowired
    AccountService service;

    @Autowired
    AccountRepository accounts;

    @Autowired
    AuthSessionRepository sessions;

    @Test
    void registerLogsInAndTokenIdentifiesUser() {
        AccountService.Login login = service.register("  Alice ", "secret1");

        assertThat(login.username()).isEqualTo("Alice");
        assertThat(service.authenticate(login.token())).get()
                .extracting(AccountService.User::username).isEqualTo("Alice");
    }

    @Test
    void loginIsCaseInsensitiveAndChecksPassword() {
        service.register("Alice", "secret1");

        assertThat(service.login("alice", "secret1").username()).isEqualTo("Alice");
        assertThatThrownBy(() -> service.login("Alice", "wrong-password"))
                .hasMessage("Неверное имя или пароль");
        assertThatThrownBy(() -> service.login("nobody", "secret1"))
                .hasMessage("Неверное имя или пароль");
    }

    @Test
    void usernameIsUniqueIgnoringCase() {
        service.register("Alice", "secret1");

        assertThatThrownBy(() -> service.register("ALICE", "secret2")).hasMessageContaining("уже занято");
    }

    @Test
    void validatesUsernameAndPassword() {
        assertThatThrownBy(() -> service.register("ab", "secret1")).hasMessageContaining("от 3 до 20");
        assertThatThrownBy(() -> service.register("Алиса Петровна", "secret1")).hasMessageContaining("от 3 до 20");
        assertThatThrownBy(() -> service.register(null, "secret1")).hasMessageContaining("от 3 до 20");
        assertThatThrownBy(() -> service.register("Alice", "12345")).hasMessageContaining("не короче 6");
        assertThatThrownBy(() -> service.register("Alice", "я".repeat(37))).hasMessageContaining("слишком длинный");

        assertThat(service.register("Алиса_2", "secret1").username()).isEqualTo("Алиса_2");
    }

    @Test
    void passwordsAndTokensAreStoredHashed() {
        AccountService.Login login = service.register("Alice", "secret1");

        Account account = accounts.findByUsernameKey("alice").orElseThrow();
        assertThat(account.passwordHash()).doesNotContain("secret1").startsWith("$2");
        assertThat(sessions.findById(login.token())).isEmpty();
        assertThat(sessions.findById(AccountService.hash(login.token()))).isPresent();
    }

    @Test
    void logoutEndsOnlyThatSession() {
        service.register("Alice", "secret1");
        String laptop = service.login("Alice", "secret1").token();
        String phone = service.login("Alice", "secret1").token();

        service.logout(laptop);

        assertThat(service.authenticate(laptop)).isEmpty();
        assertThat(service.authenticate(phone)).isPresent();
    }

    @Test
    void unknownOrMissingTokenIsNobody() {
        assertThat(service.authenticate(null)).isEmpty();
        assertThat(service.authenticate("")).isEmpty();
        assertThat(service.authenticate("guess")).isEmpty();
    }

    @Test
    void sessionExpiresAfterThirtyDays() {
        Instant start = Instant.parse("2026-01-01T12:00:00Z");
        AccountService then = new AccountService(accounts, sessions, Clock.fixed(start, ZoneOffset.UTC));
        String token = then.register("Alice", "secret1").token();

        Instant almost = start.plus(AccountService.SESSION_TTL).minusSeconds(1);
        assertThat(new AccountService(accounts, sessions, Clock.fixed(almost, ZoneOffset.UTC))
                .authenticate(token)).isPresent();

        AccountService later = new AccountService(accounts, sessions,
                Clock.fixed(start.plus(AccountService.SESSION_TTL), ZoneOffset.UTC));
        assertThat(later.authenticate(token)).isEmpty();
        later.deleteExpiredSessions();
        assertThat(sessions.findById(AccountService.hash(token))).isEmpty();
    }

    @Test
    void ensureAccountCreatesOnce() {
        service.ensureAccount("bob", "test123");
        service.ensureAccount("bob", "other-password");

        assertThat(service.login("bob", "test123").username()).isEqualTo("bob");
    }
}
