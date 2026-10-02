package com.example.monopoly.engine;

import static java.time.Duration.ofMinutes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Аренда сначала показывается всем и списывается, когда игрок нажмёт «Заплатить». Baltic Ave — клетка 3 (аренда $4). */
class RentDueTest {

    private static final int BALTIC = 3;

    private final FixedDice dice = new FixedDice();
    private final MutableClock clock = new MutableClock();
    private final Player alice = new Player("a", "Alice", Game.START_MONEY);
    private final Player bob = new Player("b", "Bob", Game.START_MONEY);

    private Game newGame(Card chanceCard, Player... players) {
        return new Game(List.of(players), dice, new Deck(List.of(chanceCard)),
                new Deck(List.of(Card.of(DeckType.COMMUNITY_CHEST, "ничего", Card.Kind.GAIN, 0))), clock);
    }

    private Game newGame(Player... players) {
        return newGame(Card.of(DeckType.CHANCE, "ничего", Card.Kind.GAIN, 0), players);
    }

    @Test
    void rentIsShownAndPaidOnlyOnButton() {
        Game g = newGame(alice, bob);
        g.setOwner(BALTIC, "b");
        dice.then(1, 2);
        g.roll("a");

        assertThat(g.phase()).isEqualTo(TurnPhase.RENT_DUE);
        assertThat(g.rentDue()).isEqualTo(new RentDue("b", BALTIC, 4));
        assertThat(alice.money()).isEqualTo(1500); // ещё не списано

        g.payRent("a");

        assertThat(alice.money()).isEqualTo(1496);
        assertThat(bob.money()).isEqualTo(1504);
        assertThat(g.rentDue()).isNull();
        assertThat(g.phase()).isEqualTo(TurnPhase.TURN_END);
    }

    @Test
    void noRentWindowOnOwnProperty() {
        Game g = newGame(alice, bob);
        g.setOwner(BALTIC, "a");
        dice.then(1, 2);
        g.roll("a");

        assertThat(g.phase()).isEqualTo(TurnPhase.TURN_END);
        assertThat(g.rentDue()).isNull();
    }

    @Test
    void noRentWindowOnMortgagedProperty() {
        Game g = newGame(alice, bob);
        g.setOwner(6, "b");      // Oriental Ave
        dice.then(1, 3);         // Alice -> 4 налог
        g.roll("a");
        g.endTurn("a");
        g.mortgage("b", 6);      // Bob закладывает в свой ход
        dice.then(1, 3);         // Bob -> 4
        g.roll("b");
        g.endTurn("b");

        dice.then(1, 1);         // Alice -> 6, заложенная клетка Bob (дубль — ещё бросок)
        g.roll("a");

        assertThat(g.phase()).isEqualTo(TurnPhase.WAITING_FOR_ROLL);
        assertThat(g.rentDue()).isNull();
        assertThat(alice.money()).isEqualTo(1500 - 200);
    }

    @Test
    void onlyThePayerActsAndNothingElseMeanwhile() {
        Game g = newGame(alice, bob);
        g.setOwner(BALTIC, "b");
        dice.then(1, 2);
        g.roll("a");

        assertThatThrownBy(() -> g.payRent("b")).hasMessageContaining("не ваш ход");
        assertThatThrownBy(() -> g.endTurn("a")).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> g.roll("a")).isInstanceOf(GameException.class);
    }

    @Test
    void cannotPayWhenNothingIsDue() {
        Game g = newGame(alice, bob);

        assertThatThrownBy(() -> g.payRent("a")).isInstanceOf(GameException.class);
    }

    @Test
    void shortOfCashTurnsIntoDebtAfterPressingPay() {
        Player poor = new Player("a", "Alice", 2);
        Game g = newGame(poor, bob);
        g.setOwner(BALTIC, "b");
        g.setOwner(5, "a"); // есть что заложить — значит долг, а не банкротство
        dice.then(1, 2);
        g.roll("a");
        assertThat(g.phase()).isEqualTo(TurnPhase.RENT_DUE);

        g.payRent("a");

        assertThat(g.phase()).isEqualTo(TurnPhase.PAYING_DEBT);
        assertThat(g.currentDebt()).isEqualTo(new Debt("a", "b", 4));
    }

    @Test
    void doubleRailroadRentFromCardIsShownDoubled() {
        Game g = newGame(Card.of(DeckType.CHANCE, "ж/д", Card.Kind.NEAREST_RAILROAD), alice, bob);
        g.setOwner(15, "b");
        dice.then(3, 4); // -> 7 «Шанс»
        g.roll("a");
        g.closeCard("a"); // -> 15, владелец Bob

        assertThat(g.phase()).isEqualTo(TurnPhase.RENT_DUE);
        assertThat(g.rentDue().amount()).isEqualTo(50);
    }

    @Test
    void rentIsPaidAutomaticallyWhenTurnTimeRunsOut() {
        Game g = newGame(alice, bob);
        g.setOwner(BALTIC, "b");
        dice.then(1, 2);
        g.roll("a");

        clock.advance(ofMinutes(3));
        g.tick();

        assertThat(bob.money()).isEqualTo(1504);
        assertThat(g.current()).isSameAs(bob);
    }
}
