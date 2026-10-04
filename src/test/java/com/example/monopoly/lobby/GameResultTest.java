package com.example.monopoly.lobby;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.monopoly.account.AccountService;
import com.example.monopoly.engine.DiceRoll;
import com.example.monopoly.engine.Game;
import com.example.monopoly.engine.Player;
import com.example.monopoly.engine.TurnPhase;
import com.example.monopoly.stats.StatsService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Закончившаяся партия попадает в статистику ровно один раз. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GameResultTest {

    @Autowired
    LobbyService lobby;

    @Autowired
    AccountService accounts;

    @Autowired
    StatsService stats;

    private AccountService.User user(String name) {
        return accounts.authenticate(accounts.register(name, "secret1").token()).orElseThrow();
    }

    @Test
    void bankruptcyEndsGameAndResultIsRecordedOnce() {
        AccountService.User alice = user("Alice");
        AccountService.User bob = user("Bob");
        Room room = lobby.create();
        Room.Seat aliceSeat = lobby.join(room, alice.id(), alice.username());
        lobby.join(room, bob.id(), bob.username());

        // у всех по $100, кубики всегда 1+3 — первый же ход на налог $200, банкротство неизбежно
        room.start(aliceSeat.playerId(), Instant.now(), players -> new Game(
                players.stream().map(p -> new Player(p.id(), p.name(), 100)).toList(),
                () -> new DiceRoll(1, 3)));
        String a = aliceSeat.playerId();

        lobby.act(room, g -> g.roll(a));
        lobby.act(room, g -> g.payDue(a));
        lobby.act(room, g -> g.declareBankruptcy(a));
        assertThat(room.game().phase()).isEqualTo(TurnPhase.GAME_OVER);

        assertThat(room.takeResult()).isEmpty(); // итог уже забран — второй раз не запишется

        assertThat(stats.statsFor(bob.id())).isEqualTo(new StatsService.Stats(1, 1, 0, 100, 0));
        assertThat(stats.statsFor(alice.id())).isEqualTo(new StatsService.Stats(1, 0, 1, 0, 100));
    }
}
