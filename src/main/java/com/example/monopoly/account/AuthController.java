package com.example.monopoly.account;

import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/auth/register и /api/auth/login {username, password} → {token, username};
 * POST /api/auth/logout и GET /api/auth/me — с заголовком «Authorization: Bearer токен».
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record Credentials(String username, String password) {
    }

    private final AccountService accounts;

    public AuthController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/register")
    public AccountService.Login register(@RequestBody Credentials c) {
        return accounts.register(c.username(), c.password());
    }

    @PostMapping("/login")
    public AccountService.Login login(@RequestBody Credentials c) {
        return accounts.login(c.username(), c.password());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        accounts.logout(AccountService.bearerToken(auth));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        return accounts.authenticate(AccountService.bearerToken(auth))
                .map(u -> ResponseEntity.ok(Map.of("username", u.username())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Войдите в аккаунт")));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> onAuthError(AuthException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
