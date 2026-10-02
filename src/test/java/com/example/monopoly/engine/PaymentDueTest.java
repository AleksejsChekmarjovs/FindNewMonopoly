package com.example.monopoly.engine;

import static java.time.Duration.ofMinutes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Аренда сначала показывается всем и списывается, когда игрок нажмёт «Заплатить». Baltic Ave — клетка 3 (аренда $4). */
class PaymentDueTest {

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

        assertThat(g.phase()).isEqualTo(TurnPhase.PAYMENT_DUE);
        assertThat(g.paymentDue()).isEqualTo(new PaymentDue("b", BALTIC, 4));
        assertThat(alice.money()).isEqualTo(1500); // ещё не списано

        g.payDue("a");

        assertThat(alice.money()).isEqualTo(1496);
        assertThat(bob.money()).isEqualTo(1504);
        assertThat(g.paymentDue()).isNull();
        assertThat(g.phase()).isEqualTo(TurnPhase.TURN_END);
    }

    @Test
    void incomeTaxIsShownAndPaidToBankOnButton() {
        Game g = newGame(alice, bob);
        dice.then(1, 3); // -> 4 «Подоходный налог»
        g.roll("a");

        assertThat(g.phase()).isEqualTo(TurnPhase.PAYMENT_DUE);
        assertThat(g.paymentDue()).isEqualTo(new PaymentDue(null, 4, 200)); // получатель — банк
        assertThat(alice.money()).isEqualTo(1500);

        g.payDue("a");

        assertThat(alice.money()).isEqualTo(1300);
        assertThat(bob.money()).isEqualTo(1500);
        assertThat(g.phase()).isEqualTo(TurnPhase.TURN_END);
    }

    @Test
    void luxuryTaxUsesItsOwnAmount() {
        Game g = newGame(alice, bob);
        for (int[] roll : new int[][]{{5, 6}, {6, 4}, {6, 5}}) { // 11, 21, 32 — по пути без налогов
            dice.then(roll[0], roll[1]);
            TestMoves.roll(g, "a");
            if (g.phase() == TurnPhase.AWAITING_BUY_DECISION) {
                TestMoves.declineAndNobodyBids(g, "a");
            }
            g.endTurn("a");
            dice.then(1, 2);
            TestMoves.roll(g, "b");
            if (g.phase() == TurnPhase.AWAITING_BUY_DECISION) {
                TestMoves.declineAndNobodyBids(g, "b");
            }
            g.endTurn("b");
        }
        dice.then(2, 4); // 32 -> 38 «Налог на роскошь»
        g.roll("a");

        assertThat(g.paymentDue()).isEqualTo(new PaymentDue(null, 38, 100));
    }

    @Test
    void taxShortOfCashTurnsIntoDebtToBank() {
        Player poor = new Player("a", "Alice", 150);
        Game g = newGame(poor, bob);
        g.setOwner(5, "a"); // $150 + залог $100 покрывают $200 — значит долг, а не банкротство
        dice.then(1, 3);
        g.roll("a");

        g.payDue("a");

        assertThat(g.phase()).isEqualTo(TurnPhase.PAYING_DEBT);
        assertThat(g.currentDebt()).isEqualTo(new Debt("a", null, 200));
    }

    @Test
    void noRentWindowOnOwnProperty() {
        Game g = newGame(alice, bob);
        g.setOwner(BALTIC, "a");
        dice.then(1, 2);
        g.roll("a");

        assertThat(g.phase()).isEqualTo(TurnPhase.TURN_END);
        assertThat(g.paymentDue()).isNull();
    }

    @Test
    void noRentWindowOnMortgagedProperty() {
        Game g = newGame(alice, bob);
        g.setOwner(6, "b");      // Oriental Ave
        dice.then(1, 3);         // Alice -> 4 налог
        g.roll("a");
        g.payDue("a");
        g.endTurn("a");
        g.mortgage("b", 6);      // Bob закладывает в свой ход
        dice.then(1, 3);         // Bob -> 4
        g.roll("b");
        g.payDue("b");
        g.endTurn("b");

        dice.then(1, 1);         // Alice -> 6, заложенная клетка Bob (дубль — ещё бросок)
        g.roll("a");

        assertThat(g.phase()).isEqualTo(TurnPhase.WAITING_FOR_ROLL);
        assertThat(g.paymentDue()).isNull();
        assertThat(alice.money()).isEqualTo(1500 - 200);
    }

    @Test
    void onlyThePayerActsAndNothingElseMeanwhile() {
        Game g = newGame(alice, bob);
        g.setOwner(BALTIC, "b");
        dice.then(1, 2);
        g.roll("a");

        assertThatThrownBy(() -> g.payDue("b")).hasMessageContaining("не ваш ход");
        assertThatThrownBy(() -> g.endTurn("a")).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> g.roll("a")).isInstanceOf(GameException.class);
    }

    @Test
    void cannotPayWhenNothingIsDue() {
        Game g = newGame(alice, bob);

        assertThatThrownBy(() -> g.payDue("a")).isInstanceOf(GameException.class);
    }

    @Test
    void shortOfCashTurnsIntoDebtAfterPressingPay() {
        Player poor = new Player("a", "Alice", 2);
        Game g = newGame(poor, bob);
        g.setOwner(BALTIC, "b");
        g.setOwner(5, "a"); // есть что заложить — значит долг, а не банкротство
        dice.then(1, 2);
        g.roll("a");
        assertThat(g.phase()).isEqualTo(TurnPhase.PAYMENT_DUE);

        g.payDue("a");

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

        assertThat(g.phase()).isEqualTo(TurnPhase.PAYMENT_DUE);
        assertThat(g.paymentDue().amount()).isEqualTo(50);
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
