package com.example.monopoly.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Регистрация, вход и выход. Вход выдаёт случайный токен: браузер хранит его и присылает
 * с запросами. Пароли хранятся как BCrypt, токены — как SHA-256.
 */
@Service
public class AccountService {

    /** Буквы любого алфавита, цифры, «_» и «-». */
    private static final Pattern USERNAME = Pattern.compile("[\\p{L}\\p{N}_-]{3,20}");
    static final int MIN_PASSWORD = 6;
    /** BCrypt учитывает только первые 72 байта пароля. */
    static final int MAX_PASSWORD_BYTES = 72;
    static final Duration SESSION_TTL = Duration.ofDays(30);

    /** Кто вошёл: то, что нужно игре и клиенту. */
    public record User(long id, String username) {
    }

    /** Результат входа: токен отдаётся браузеру один раз. */
    public record Login(String token, String username) {
    }

    private final AccountRepository accounts;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwords = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public AccountService(AccountRepository accounts, AuthSessionRepository sessions, Clock clock) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional
    public Login register(String username, String password) {
        String name = username == null ? "" : username.strip();
        if (!USERNAME.matcher(name).matches()) {
            throw new AuthException("Имя: от 3 до 20 букв, цифр, «_» или «-»");
        }
        checkPassword(password);
        if (accounts.existsByUsernameKey(Account.key(name))) {
            throw new AuthException("Имя «" + name + "» уже занято");
        }
        Account account;
        try {
            account = accounts.saveAndFlush(new Account(name, passwords.encode(password), clock.instant()));
        } catch (DataIntegrityViolationException e) { // кто-то занял имя одновременно с нами
            throw new AuthException("Имя «" + name + "» уже занято");
        }
        return openSession(account);
    }

    @Transactional
    public Login login(String username, String password) {
        Optional<Account> account = username == null ? Optional.empty()
                : accounts.findByUsernameKey(Account.key(username.strip()));
        // одно сообщение для обоих случаев — не подсказываем, какие имена существуют
        if (account.isEmpty() || password == null || !passwords.matches(password, account.get().passwordHash())) {
            throw new AuthException("Неверное имя или пароль");
        }
        return openSession(account.get());
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessions.deleteById(hash(token));
        }
    }

    /** Кто вошёл по этому токену; пусто — токен неизвестен или истёк. */
    @Transactional(readOnly = true)
    public Optional<User> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return sessions.findById(hash(token))
                .filter(s -> s.expiresAt().isAfter(clock.instant()))
                .map(s -> new User(s.account().id(), s.account().username()));
    }

    /** Создать аккаунт, если такого ещё нет (тестовые аккаунты профиля dev). */
    @Transactional
    public void ensureAccount(String username, String password) {
        if (!accounts.existsByUsernameKey(Account.key(username))) {
            accounts.save(new Account(username, passwords.encode(password), clock.instant()));
        }
    }

    @Scheduled(fixedRate = 60 * 60 * 1000)
    @Transactional
    public void deleteExpiredSessions() {
        sessions.deleteExpired(clock.instant());
    }

    private static void checkPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new AuthException("Пароль: не короче " + MIN_PASSWORD + " символов");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new AuthException("Пароль слишком длинный");
        }
    }

    private Login openSession(Account account) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        sessions.save(new AuthSession(hash(token), account, now, now.plus(SESSION_TTL)));
        return new Login(token, account.username());
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
