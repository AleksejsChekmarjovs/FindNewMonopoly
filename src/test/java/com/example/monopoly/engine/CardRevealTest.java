package com.example.monopoly.engine;

import static com.example.monopoly.engine.Card.Kind.*;
import static com.example.monopoly.engine.DeckType.CHANCE;
import static com.example.monopoly.engine.DeckType.COMMUNITY_CHEST;
import static java.time.Duration.ofMinutes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Карточка сначала открывается всем и выполняется только после того, как игрок её закроет. «Шанс» — клетка 7. */
class CardRevealTest {

    private final FixedDice dice = new FixedDice();
    private final MutableClock clock = new MutableClock();
    private final Player alice = new Player("a", "Alice", Game.START_MONEY);
    private final Player bob = new Player("b", "Bob", Game.START_MONEY);

    private Game withChance(Card card) {
        return new Game(List.of(alice, bob), dice, new Deck(List.of(card)),
                new Deck(List.of(Card.of(COMMUNITY_CHEST, "ничего", GAIN, 0))), clock);
    }

    @Test
    void cardIsShownButNotAppliedUntilClosed() {
        Game g = withChance(Card.of(CHANCE, "Отправляйтесь на Boardwalk", MOVE_TO, 39));
        dice.then(3, 4);
        g.roll("a");

        assertThat(g.phase()).isEqualTo(TurnPhase.CARD_REVEAL);
        assertThat(alice.position()).isEqualTo(7);            // стоит на «Шансе»
        assertThat(g.lastCard().text()).isEqualTo("Отправляйтесь на Boardwalk");

        g.closeCard("a");

        assertThat(alice.position()).isEqualTo(39);           // только теперь переместилась
        assertThat(g.phase()).isEqualTo(TurnPhase.AWAITING_BUY_DECISION);
    }

    @Test
    void moneyCardAppliedOnClose() {
        Game g = withChance(Card.of(CHANCE, "Дивиденды", GAIN, 50));
        dice.then(3, 4);
        g.roll("a");
        assertThat(alice.money()).isEqualTo(1500);

        g.closeCard("a");

        assertThat(alice.money()).isEqualTo(1550);
        assertThat(g.phase()).isEqualTo(TurnPhase.TURN_END);
    }

    @Test
    void onlyTheDrawingPlayerClosesAndNothingElseHappensMeanwhile() {
        Game g = withChance(Card.of(CHANCE, "Дивиденды", GAIN, 50));
        dice.then(3, 4);
        g.roll("a");

        assertThatThrownBy(() -> g.closeCard("b")).hasMessageContaining("не ваш ход");
        assertThatThrownBy(() -> g.endTurn("a")).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> g.roll("a")).isInstanceOf(GameException.class);
    }

    @Test
    void cannotCloseWhenNoCardIsOpen() {
        Game g = withChance(Card.of(CHANCE, "Дивиденды", GAIN, 50));

        assertThatThrownBy(() -> g.closeCard("a")).isInstanceOf(GameException.class);
    }

    @Test
    void doublesExtraRollKeptAfterCard() {
        Game g = withChance(Card.of(CHANCE, "Дивиденды", GAIN, 50));
        dice.then(2, 2).then(1, 2);
        g.roll("a");                // дубль -> 4, ещё бросок
        assertThat(g.phase()).isEqualTo(TurnPhase.WAITING_FOR_ROLL);
        g.roll("a");                // 7 «Шанс»
        assertThat(g.phase()).isEqualTo(TurnPhase.CARD_REVEAL);

        g.closeCard("a");

        assertThat(g.phase()).isEqualTo(TurnPhase.TURN_END); // второй бросок не дубль
    }

    @Test
    void openCardClosesAutomaticallyWhenTurnTimeRunsOut() {
        Game g = withChance(Card.of(CHANCE, "Дивиденды", GAIN, 50));
        dice.then(3, 4);
        g.roll("a");
        assertThat(g.timers().turnClockRunning()).isTrue();

        clock.advance(ofMinutes(3));
        g.tick();

        assertThat(alice.money()).isEqualTo(1550); // карточка выполнилась
        assertThat(g.current()).isSameAs(bob);     // и ход доигран
    }
}
