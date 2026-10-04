package com.example.monopoly.stats;

import com.example.monopoly.account.AccountService;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/stats/me — статистика вошедшего игрока (заголовок «Authorization: Bearer токен»). */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final AccountService accounts;
    private final StatsService stats;

    public StatsController(AccountService accounts, StatsService stats) {
        this.accounts = accounts;
        this.stats = stats;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        return accounts.authenticate(AccountService.bearerToken(auth))
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(stats.statsFor(u.id())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Войдите в аккаунт")));
    }
}
