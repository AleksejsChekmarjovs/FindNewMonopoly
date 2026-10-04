package com.example.monopoly.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.example.monopoly.account.AccountService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StatsServiceTest {

    private static final Instant START = Instant.parse("2026-01-01T12:00:00Z");

    @Autowired
    StatsService stats;

    @Autowired
    AccountService accounts;

    @Autowired
    FinishedGameRepository games;

    private long account(String name) {
        return accounts.authenticate(accounts.register(name, "secret1").token()).orElseThrow().id();
    }

    @Test
    void newPlayerHasNoGames() {
        assertThat(stats.statsFor(account("Newbie"))).isEqualTo(new StatsService.Stats(0, 0, 0, 0, 0));
    }

    @Test
    void countsWinsLossesAndPercents() {
        long alice = account("Alice");
        long bob = account("Bob");
        long carol = account("Carol");

        stats.recordFinishedGame("100001", START, List.of(alice, bob, carol));
        stats.recordFinishedGame("100002", START, List.of(bob, alice));
        stats.recordFinishedGame("100003", START, List.of(alice, carol));

        assertThat(stats.statsFor(alice)).isEqualTo(new StatsService.Stats(3, 2, 1, 67, 33));
        assertThat(stats.statsFor(bob)).isEqualTo(new StatsService.Stats(2, 1, 1, 50, 50));
        assertThat(stats.statsFor(carol)).isEqualTo(new StatsService.Stats(2, 0, 2, 0, 100));
    }

    @Test
    void storesPlacesAndPlayerCount() {
        long alice = account("Alice");
        long bob = account("Bob");
        long carol = account("Carol");

        stats.recordFinishedGame("100001", START, List.of(carol, alice, bob));

        FinishedGame game = games.findAll().getFirst();
        assertThat(game.participants())
                .extracting(GameParticipant::accountId, GameParticipant::place, GameParticipant::winner)
                .containsExactly(
                        tuple(carol, 1, true),
                        tuple(alice, 2, false),
                        tuple(bob, 3, false));
    }
}
