package com.example.monopoly.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Коричневая группа: Mediterranean (1, $60) и Baltic (3, $60); Reading Railroad (5, $200). */
class MortgageTest {

    private static final int MEDITERRANEAN = 1;
    private static final int BALTIC = 3;
    private static final int READING = 5;

    private final FixedDice dice = new FixedDice();
    private final Player alice = new Player("a", "Alice", Game.START_MONEY);
    private final Player bob = new Player("b", "Bob", Game.START_MONEY);
    private final Game game = newGame(alice, bob);

    private Game newGame(Player... players) {
        return new Game(List.of(players), dice,
                new Deck(List.of(Card.of(DeckType.CHANCE, "ничего", Card.Kind.GAIN, 0))),
                new Deck(List.of(Card.of(DeckType.COMMUNITY_CHEST, "ничего", Card.Kind.GAIN, 0))));
    }

    /** Alice ходит на налог (4) и завершает ход — Bob готов бросать. */
    private void passAliceTurn(Game g) {
        dice.then(1, 3);
        TestMoves.roll(g, "a");
        g.endTurn("a");
    }

    // ---------------------------------------------------------------- залог

    @Test
    void mortgageGivesHalfPrice() {
        game.setOwner(READING, "a");

        game.mortgage("a", READING);

        assertThat(alice.money()).isEqualTo(1500 + 100);
        assertThat(game.mortgaged()).containsExactly(READING);
    }

    @Test
    void cannotMortgageTwiceOrSomeoneElses() {
        game.setOwner(READING, "a");
        game.setOwner(BALTIC, "b");
        game.mortgage("a", READING);

        assertThatThrownBy(() -> game.mortgage("a", READING)).hasMessageContaining("уже заложена");
        assertThatThrownBy(() -> game.mortgage("a", BALTIC)).hasMessageContaining("не ваше");
        assertThatThrownBy(() -> game.mortgage("a", 4)).hasMessageContaining("нельзя заложить");
    }

    @Test
    void cannotMortgageWhileGroupHasBuildings() {
        game.setOwner(MEDITERRANEAN, "a");
        game.setOwner(BALTIC, "a");
        game.buildHouse("a", BALTIC);

        // даже улицу без домов — дома стоят на соседней улице группы
        assertThatThrownBy(() -> game.mortgage("a", MEDITERRANEAN)).hasMessageContaining("продайте все постройки");

        game.sellHouse("a", BALTIC);
        game.mortgage("a", MEDITERRANEAN);
        assertThat(game.mortgaged()).contains(MEDITERRANEAN);
    }

    @Test
    void cannotBuildInGroupWithMortgagedStreet() {
        game.setOwner(MEDITERRANEAN, "a");
        game.setOwner(BALTIC, "a");
        game.mortgage("a", MEDITERRANEAN);

        assertThatThrownBy(() -> game.buildHouse("a", BALTIC)).hasMessageContaining("выкупите");
    }

    @Test
    void mortgagedPropertyCollectsNoRent() {
        game.setOwner(READING, "a");
        game.mortgage("a", READING);
        passAliceTurn(game);

        dice.then(2, 3); // Bob -> 5 Reading
        TestMoves.roll(game, "b");

        assertThat(bob.money()).isEqualTo(1500);
        assertThat(game.phase()).isEqualTo(TurnPhase.TURN_END);
    }

    @Test
    void cannotManagePropertyDuringAuction() {
        game.setOwner(READING, "a");
        dice.then(1, 2); // Alice -> 3 Baltic
        TestMoves.roll(game, "a");
        game.declineBuy("a");

        assertThatThrownBy(() -> game.mortgage("a", READING)).hasMessageContaining("аукциона");
    }

    @Test
    void canMortgageToAffordPurchase() {
        Player poor = new Player("p", "Poor", 50);
        Game g = newGame(poor, bob);
        g.setOwner(READING, "p");

        dice.then(1, 2); // -> 3 Baltic, $60 при $50
        TestMoves.roll(g, "p");
        g.mortgage("p", READING); // +$100
        g.buy("p");

        assertThat(g.owners()).containsEntry(BALTIC, "p");
        assertThat(poor.money()).isEqualTo(50 + 100 - 60);
    }

    // ---------------------------------------------------------------- выкуп

    @Test
    void unmortgageCostsValuePlusTenPercentRoundedUp() {
        Tile baltic = game.board().tile(BALTIC);
        Tile kentucky = game.board().tile(21);
        assertThat(Game.unmortgageCost(baltic)).isEqualTo(33);    // 30 + 3
        assertThat(Game.unmortgageCost(kentucky)).isEqualTo(121); // 110 + 11
        assertThat(Game.unmortgageCost(game.board().tile(12))).isEqualTo(83); // 75 + 7.5 → 83

        game.setOwner(BALTIC, "a");
        game.mortgage("a", BALTIC);
        game.unmortgage("a", BALTIC);

        assertThat(alice.money()).isEqualTo(1500 + 30 - 33);
        assertThat(game.mortgaged()).isEmpty();
    }

    @Test
    void cannotUnmortgageWithoutMoneyOrIfNotMortgaged() {
        game.setOwner(BALTIC, "a");
        assertThatThrownBy(() -> game.unmortgage("a", BALTIC)).hasMessageContaining("не заложена");

        Player poor = new Player("p", "Poor", 0);
        Game g = newGame(poor, bob);
        g.setOwner(BALTIC, "p");
        g.mortgage("p", BALTIC); // +$30, выкуп $33

        assertThatThrownBy(() -> g.unmortgage("p", BALTIC)).hasMessageContaining("Недостаточно денег");
    }

    // ---------------------------------------------------------------- долги и банкротство

    @Test
    void bankruptcyToBankClearsMortgages() {
        Player alice = new Player("a", "Alice", 10);
        Game g = newGame(alice, bob);
        g.setOwner(BALTIC, "a");
        g.mortgage("a", BALTIC); // $40

        dice.then(1, 3); // налог $200 — не покрыть ничем
        TestMoves.roll(g, "a");

        assertThat(alice.bankrupt()).isTrue();
        assertThat(g.owners()).doesNotContainKey(BALTIC);
        assertThat(g.mortgaged()).isEmpty();
    }

    @Test
    void bankruptcyToPlayerPassesPropertyMortgaged() {
        Player alice = new Player("a", "Alice", 10);
        Game g = newGame(alice, bob);
        g.setOwner(BALTIC, "a");
        g.setOwner(READING, "b");
        g.setOwner(15, "b");
        g.setOwner(25, "b");
        g.setOwner(35, "b"); // все 4 ж/д у Bob — аренда $200
        g.mortgage("a", BALTIC); // $40

        dice.then(2, 3); // Alice -> 5 Reading, долг не покрыть
        TestMoves.roll(g, "a");

        assertThat(alice.bankrupt()).isTrue();
        assertThat(g.owners()).containsEntry(BALTIC, "b");
        assertThat(g.mortgaged()).contains(BALTIC);
    }
}
