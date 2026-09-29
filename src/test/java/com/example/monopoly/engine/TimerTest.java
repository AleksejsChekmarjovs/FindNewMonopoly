package com.example.monopoly.engine;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TimerTest {

    private static final int BALTIC = 3;
    private static final int READING = 5;

    private final FixedDice dice = new FixedDice();
    private final MutableClock clock = new MutableClock();
    private final Player alice = new Player("a", "Alice", Game.START_MONEY);
    private final Player bob = new Player("b", "Bob", Game.START_MONEY);

    private Game newGame(Card chestCard, Player... players) {
        return new Game(List.of(players), dice,
                new Deck(List.of(Card.of(DeckType.CHANCE, "ничего", Card.Kind.GAIN, 0))),
                new Deck(List.of(chestCard)), clock);
    }

    private Game newGame(Player... players) {
        return newGame(Card.of(DeckType.COMMUNITY_CHEST, "ничего", Card.Kind.GAIN, 0), players);
    }

    private static TradeOffer smallOffer() {
        return new TradeOffer("a", "b", List.of(), List.of(), 10, 0, 0, 0);
    }

    // ---------------------------------------------------------------- время хода

    @Test
    void nothingHappensBeforeTurnTimeRunsOut() {
        Game g = newGame(alice, bob);

        clock.advance(ofMinutes(3).minusSeconds(1));

        assertThat(g.tick()).isFalse();
        assertThat(g.current()).isSameAs(alice);
        assertThat(g.timers().turnMillisLeft()).isEqualTo(1000);
    }

    @Test
    void expiredTurnIsPlayedAutomatically() {
        Game g = newGame(alice, bob);
        dice.then(1, 3); // авто-бросок -> 4 налог

        clock.advance(ofMinutes(3));

        assertThat(g.tick()).isTrue();
        assertThat(alice.position()).isEqualTo(4);
        assertThat(alice.money()).isEqualTo(1300);
        assertThat(g.current()).isSameAs(bob);
        assertThat(g.phase()).isEqualTo(TurnPhase.WAITING_FOR_ROLL);
        assertThat(g.timers().turnMillisLeft()).isEqualTo(ofMinutes(3).toMillis());
        assertThat(g.log()).anyMatch(l -> l.contains("Время хода Alice истекло"));
    }

    @Test
    void extraRollsShareTheSameTurnClock() {
        Game g = newGame(alice, bob);
        dice.then(2, 2);   // дубль -> 4, ещё бросок
        g.roll("a");
        clock.advance(ofMinutes(2).plusSeconds(59));
        assertThat(g.tick()).isFalse();

        dice.then(1, 2);   // авто-бросок -> 7 Шанс
        clock.advance(ofSeconds(1));
        g.tick();

        assertThat(alice.position()).isEqualTo(7);
        assertThat(g.current()).isSameAs(bob);
    }

    @Test
    void timeoutDeclinesPurchaseAndAuctionRunsOnItsOwnTimer() {
        Game g = newGame(alice, bob);
        dice.then(1, 2); // -> 3 Baltic
        g.roll("a");

        clock.advance(ofMinutes(3));
        g.tick(); // отказ от покупки -> аукцион, часы хода стоят

        assertThat(g.phase()).isEqualTo(TurnPhase.AUCTION);
        assertThat(g.timers().turnClockRunning()).isFalse();
        assertThat(g.timers().waitingForId()).isEqualTo("b");
    }

    // ---------------------------------------------------------------- аукцион

    @Test
    void silentBiddersPassAfterThirtySeconds() {
        Game g = newGame(alice, bob);
        dice.then(1, 2); // -> 3 Baltic
        g.roll("a");
        g.declineBuy("a");
        g.bid("b", 10);   // очередь Alice

        clock.advance(ofSeconds(29));
        assertThat(g.tick()).isFalse();
        clock.advance(ofSeconds(1));
        g.tick();         // Alice молчит -> пас, Bob выигрывает

        assertThat(g.owners()).containsEntry(BALTIC, "b");
        assertThat(g.phase()).isEqualTo(TurnPhase.TURN_END);
    }

    @Test
    void eachBidGetsFreshTime() {
        Game g = newGame(alice, bob);
        dice.then(1, 2);
        g.roll("a");
        g.declineBuy("a");

        clock.advance(ofSeconds(25));
        g.bid("b", 10);
        clock.advance(ofSeconds(25)); // у Alice свои 30 секунд

        assertThat(g.tick()).isFalse();
        assertThat(g.phase()).isEqualTo(TurnPhase.AUCTION);
    }

    // ---------------------------------------------------------------- обмен

    @Test
    void unansweredTradeIsRejectedAfterAMinuteAndTurnClockWasPaused() {
        Game g = newGame(alice, bob);
        clock.advance(ofMinutes(2));
        g.proposeTrade(smallOffer());
        assertThat(g.timers().turnClockRunning()).isFalse();
        assertThat(g.timers().waitingForId()).isEqualTo("b");

        clock.advance(ofSeconds(59));
        assertThat(g.tick()).isFalse();
        clock.advance(ofSeconds(1));
        g.tick();

        assertThat(g.trade()).isNull();
        assertThat(alice.money()).isEqualTo(1500);
        assertThat(g.phase()).isEqualTo(TurnPhase.WAITING_FOR_ROLL);
        assertThat(g.log()).anyMatch(l -> l.contains("Bob отклоняет обмен"));
        // из 3 минут хода прошло только 2 — минута ожидания ответа не считается
        assertThat(g.timers().turnMillisLeft()).isEqualTo(ofMinutes(1).toMillis());
    }

    @Test
    void atMostThreeTradeOffersPerTurn() {
        Game g = newGame(alice, bob);
        for (int i = 0; i < 3; i++) {
            g.proposeTrade(smallOffer());
            g.rejectTrade("b");
        }
        assertThat(g.timers().tradesLeft()).isZero();

        assertThatThrownBy(() -> g.proposeTrade(smallOffer())).hasMessageContaining("Не больше 3");

        // в следующий ход у Alice снова 3 попытки
        dice.then(1, 3);
        g.roll("a");
        g.endTurn("a");
        dice.then(1, 3);
        g.roll("b");
        g.endTurn("b");
        g.proposeTrade(smallOffer());
        assertThat(g.timers().tradesLeft()).isEqualTo(2);
    }

    @Test
    void cancelledOffersCountToo() {
        Game g = newGame(alice, bob);
        for (int i = 0; i < 3; i++) {
            g.proposeTrade(smallOffer());
            g.cancelTrade("a");
        }

        assertThatThrownBy(() -> g.proposeTrade(smallOffer())).hasMessageContaining("Не больше 3");
    }

    // ---------------------------------------------------------------- долги

    @Test
    void ownDebtIsSettledAutomaticallyWhenTurnTimeRunsOut() {
        Player poor = new Player("a", "Alice", 150);
        Game g = newGame(poor, bob);
        g.setOwner(READING, "a");
        dice.then(1, 3); // налог $200 при $150
        g.roll("a");
        assertThat(g.phase()).isEqualTo(TurnPhase.PAYING_DEBT);
        assertThat(g.timers().turnClockRunning()).isTrue();

        clock.advance(ofMinutes(3));
        g.tick();

        assertThat(g.mortgaged()).contains(READING);
        assertThat(poor.money()).isEqualTo(50);
        assertThat(poor.bankrupt()).isFalse();
        assertThat(g.current()).isSameAs(bob);
    }

    @Test
    void otherPlayersDebtHasItsOwnMinute() {
        Player poorBob = new Player("b", "Bob", 5);
        Game g = newGame(Card.of(DeckType.COMMUNITY_CHEST, "день рождения", Card.Kind.GAIN_FROM_EACH, 10),
                alice, poorBob);
        g.setOwner(BALTIC, "b");
        dice.then(1, 1); // Alice -> 2 Казна, дубль
        g.roll("a");
        assertThat(g.timers().waitingForId()).isEqualTo("b");
        assertThat(g.timers().turnClockRunning()).isFalse();

        clock.advance(ofMinutes(1));
        g.tick();

        assertThat(g.mortgaged()).contains(BALTIC);
        assertThat(poorBob.money()).isEqualTo(5 + 30 - 10);
        assertThat(alice.money()).isEqualTo(1510);
        assertThat(g.phase()).isEqualTo(TurnPhase.WAITING_FOR_ROLL); // дубль Alice сохранился
        assertThat(g.current()).isSameAs(alice);
    }
}
