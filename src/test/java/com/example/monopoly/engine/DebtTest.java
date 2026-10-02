package com.example.monopoly.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Долги: не хватает наличных, но есть что продать или заложить — игрок сам решает, как расплатиться.
 * Mediterranean (1) и Baltic (3) — коричневые, залог $30; Reading Railroad (5) — залог $100.
 */
class DebtTest {

    private static final int MEDITERRANEAN = 1;
    private static final int BALTIC = 3;
    private static final int READING = 5;

    private final FixedDice dice = new FixedDice();
    private final Player bob = new Player("b", "Bob", Game.START_MONEY);

    private Game newGame(Card chestCard, Player... players) {
        return new Game(List.of(players), dice,
                new Deck(List.of(Card.of(DeckType.CHANCE, "ничего", Card.Kind.GAIN, 0))),
                new Deck(List.of(chestCard)));
    }

    private Game newGame(Player... players) {
        return newGame(Card.of(DeckType.COMMUNITY_CHEST, "ничего", Card.Kind.GAIN, 0), players);
    }

    /** Alice с $150 и Reading Railroad попадает на подоходный налог $200. */
    private Game aliceOwesTaxWithReading(Player alice) {
        Game g = newGame(alice, bob);
        g.setOwner(READING, "a");
        dice.then(1, 3);
        TestMoves.roll(g, "a");
        return g;
    }

    // ---------------------------------------------------------------- возникновение и оплата

    @Test
    void shortOfCashButHasAssetsCreatesDebt() {
        Player alice = new Player("a", "Alice", 150);
        Game g = aliceOwesTaxWithReading(alice);

        assertThat(g.phase()).isEqualTo(TurnPhase.PAYING_DEBT);
        assertThat(g.currentDebt()).isEqualTo(new Debt("a", null, 200));
        assertThat(alice.money()).isEqualTo(150);   // ничего не продано автоматически
        assertThat(g.mortgaged()).isEmpty();
    }

    @Test
    void debtorMortgagesThenPays() {
        Player alice = new Player("a", "Alice", 150);
        Game g = aliceOwesTaxWithReading(alice);

        assertThatThrownBy(() -> g.payDebt("a")).hasMessageContaining("Не хватает $50");

        g.mortgage("a", READING);
        g.payDebt("a");

        assertThat(alice.money()).isEqualTo(150 + 100 - 200);
        assertThat(g.currentDebt()).isNull();
        assertThat(g.phase()).isEqualTo(TurnPhase.TURN_END);
    }

    @Test
    void debtorSellsHousesToPay() {
        Player alice = new Player("a", "Alice", 250);
        Game g = newGame(alice, bob);
        g.setOwner(MEDITERRANEAN, "a");
        g.setOwner(BALTIC, "a");
        g.buildHouse("a", MEDITERRANEAN);
        g.buildHouse("a", BALTIC); // $150

        dice.then(1, 3); // налог $200
        TestMoves.roll(g, "a");
        g.sellHouse("a", MEDITERRANEAN);
        g.sellHouse("a", BALTIC); // $200
        g.payDebt("a");

        assertThat(alice.money()).isZero();
        assertThat(g.buildings()).isEmpty();
        assertThat(alice.bankrupt()).isFalse();
    }

    @Test
    void onlyMoneyRaisingActionsDuringDebt() {
        Player alice = new Player("a", "Alice", 150);
        Game g = newGame(alice, bob);
        g.setOwner(MEDITERRANEAN, "a");
        g.setOwner(BALTIC, "a");
        g.setOwner(READING, "a");
        g.mortgage("a", BALTIC); // $180
        dice.then(1, 3); // налог $200
        TestMoves.roll(g, "a");

        assertThatThrownBy(() -> g.unmortgage("a", BALTIC)).hasMessageContaining("Сначала расплатитесь");
        assertThatThrownBy(() -> g.buildHouse("a", MEDITERRANEAN)).hasMessageContaining("Сначала расплатитесь");
        assertThatThrownBy(() -> g.endTurn("a")).isInstanceOf(GameException.class);
    }

    @Test
    void otherPlayersWaitWhileDebtorPays() {
        Player alice = new Player("a", "Alice", 150);
        Game g = aliceOwesTaxWithReading(alice);
        g.setOwner(6, "b");

        assertThatThrownBy(() -> g.mortgage("b", 6)).hasMessageContaining("другой игрок");
        assertThatThrownBy(() -> g.payDebt("b")).hasMessageContaining("другой игрок");
        assertThatThrownBy(() -> TestMoves.roll(g, "b")).isInstanceOf(GameException.class);
    }

    @Test
    void cannotPayDebtWhenThereIsNone() {
        Game g = newGame(new Player("a", "Alice", 1500), bob);

        assertThatThrownBy(() -> g.payDebt("a")).hasMessageContaining("нет долга");
        assertThatThrownBy(() -> g.declareBankruptcy("a")).hasMessageContaining("нет долга");
    }

    // ---------------------------------------------------------------- банкротство

    @Test
    void declaringBankruptcyGivesEverythingToCreditor() {
        Player alice = new Player("a", "Alice", 150);
        Game g = newGame(alice, bob);
        g.setOwner(BALTIC, "a");
        g.setOwner(6, "a");      // Oriental, залог $50 → всего можно собрать $230
        g.setOwner(READING, "b");
        g.setOwner(15, "b");
        g.setOwner(25, "b");
        g.setOwner(35, "b");     // 4 ж/д у Bob — аренда $200

        dice.then(2, 3);         // Alice -> 5 Reading
        TestMoves.roll(g, "a");
        assertThat(g.phase()).isEqualTo(TurnPhase.PAYING_DEBT);

        g.declareBankruptcy("a");

        assertThat(alice.bankrupt()).isTrue();
        assertThat(bob.money()).isEqualTo(1500 + 150);
        assertThat(g.owners()).containsEntry(BALTIC, "b").containsEntry(6, "b");
        assertThat(g.phase()).isEqualTo(TurnPhase.GAME_OVER);
        assertThat(g.winnerId()).isEqualTo("b");
    }

    @Test
    void assetsTooSmallMeansImmediateBankruptcy() {
        Player alice = new Player("a", "Alice", 100);
        Game g = newGame(alice, bob);
        g.setOwner(BALTIC, "a"); // $100 + $30 < $200

        dice.then(1, 3);
        TestMoves.roll(g, "a");

        assertThat(alice.bankrupt()).isTrue();
        assertThat(g.currentDebt()).isNull();
    }

    // ---------------------------------------------------------------- долг не у текущего игрока

    @Test
    void birthdayDebtOfOtherPlayerThenTurnContinues() {
        Player alice = new Player("a", "Alice", 1500);
        Player poorBob = new Player("b", "Bob", 5);
        Game g = newGame(Card.of(DeckType.COMMUNITY_CHEST, "день рождения", Card.Kind.GAIN_FROM_EACH, 10),
                alice, poorBob);
        g.setOwner(BALTIC, "b");

        dice.then(1, 1); // Alice -> 2 Казна, дубль
        TestMoves.roll(g, "a");

        assertThat(g.phase()).isEqualTo(TurnPhase.PAYING_DEBT);
        assertThat(g.currentDebt()).isEqualTo(new Debt("b", "a", 10));

        g.mortgage("b", BALTIC);
        g.payDebt("b");

        assertThat(alice.money()).isEqualTo(1510);
        assertThat(poorBob.money()).isEqualTo(5 + 30 - 10);
        // дубль Alice не потерялся — она бросает ещё раз
        assertThat(g.phase()).isEqualTo(TurnPhase.WAITING_FOR_ROLL);
        assertThat(g.current()).isSameAs(alice);
    }

    @Test
    void severalDebtorsPayInTurn() {
        Player alice = new Player("a", "Alice", 1500);
        Player poorBob = new Player("b", "Bob", 5);
        Player carol = new Player("c", "Carol", 5);
        Game g = newGame(Card.of(DeckType.COMMUNITY_CHEST, "день рождения", Card.Kind.GAIN_FROM_EACH, 10),
                alice, poorBob, carol);
        g.setOwner(BALTIC, "b");
        g.setOwner(MEDITERRANEAN, "c");

        dice.then(1, 1);
        TestMoves.roll(g, "a");

        assertThat(g.currentDebt().debtorId()).isEqualTo("b");
        g.mortgage("b", BALTIC);
        g.payDebt("b");

        assertThat(g.phase()).isEqualTo(TurnPhase.PAYING_DEBT);
        assertThat(g.currentDebt().debtorId()).isEqualTo("c");
        g.declareBankruptcy("c");

        assertThat(carol.bankrupt()).isTrue();
        assertThat(g.owners()).containsEntry(MEDITERRANEAN, "a");
        assertThat(alice.money()).isEqualTo(1500 + 10 + 5);
        assertThat(g.phase()).isEqualTo(TurnPhase.WAITING_FOR_ROLL);
    }

    // ---------------------------------------------------------------- тюрьма

    @Test
    void jailFineDebtMustBePaidBeforeMoving() {
        Player alice = new Player("a", "Alice", 40);
        Game g = newGame(alice, bob);
        g.setOwner(BALTIC, "a");

        // Alice в тюрьму: три дубля подряд
        dice.then(1, 1).then(2, 2).then(3, 3);
        TestMoves.roll(g, "a");                                  // 2 Казна
        TestMoves.roll(g, "a");                                  // 6 Oriental — не хватает, отказ
        TestMoves.declineAndNobodyBids(g, "a");
        TestMoves.roll(g, "a");                                  // третий дубль — тюрьма
        g.endTurn("a");
        playBob(g);
        for (int i = 0; i < 2; i++) {                 // две неудачные попытки
            dice.then(1, 2);
            TestMoves.roll(g, "a");
            g.endTurn("a");
            playBob(g);
        }

        dice.then(1, 2);                              // третья неудача: штраф $50 при $40
        TestMoves.roll(g, "a");

        assertThat(g.phase()).isEqualTo(TurnPhase.PAYING_DEBT);
        assertThat(alice.inJail()).isTrue();
        assertThat(alice.position()).isEqualTo(Board.JAIL_INDEX);

        g.mortgage("a", BALTIC);                      // +$30
        g.payDebt("a");

        assertThat(alice.inJail()).isFalse();
        assertThat(alice.position()).isEqualTo(13);   // States Ave
        assertThat(alice.money()).isEqualTo(40 + 30 - 50);
        assertThat(g.phase()).isEqualTo(TurnPhase.AWAITING_BUY_DECISION);
    }

    private void playBob(Game g) {
        dice.then(1, 3);
        TestMoves.roll(g, "b");
        if (g.phase() == TurnPhase.AWAITING_BUY_DECISION) {
            TestMoves.declineAndNobodyBids(g, "b");
        }
        g.endTurn("b");
    }
}
