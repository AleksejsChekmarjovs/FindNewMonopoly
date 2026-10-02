package com.example.monopoly.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class GameTest {

    private final FixedDice dice = new FixedDice();
    private final Player alice = new Player("a", "Alice", Game.START_MONEY);
    private final Player bob = new Player("b", "Bob", Game.START_MONEY);
    private final Game game = newGame(alice, bob);

    /** Колоды из одной «пустой» карточки, чтобы карточки не влияли на тесты базовых правил. */
    private Game newGame(Player... players) {
        return new Game(List.of(players), dice,
                new Deck(List.of(Card.of(DeckType.CHANCE, "ничего", Card.Kind.GAIN, 0))),
                new Deck(List.of(Card.of(DeckType.COMMUNITY_CHEST, "ничего", Card.Kind.GAIN, 0))));
    }

    @Test
    void landingOnFreeStreetOffersPurchase() {
        dice.then(1, 2); // -> 3 Baltic Ave
        TestMoves.roll(game, "a");

        assertThat(alice.position()).isEqualTo(3);
        assertThat(game.phase()).isEqualTo(TurnPhase.AWAITING_BUY_DECISION);

        game.buy("a");

        assertThat(alice.money()).isEqualTo(1500 - 60);
        assertThat(game.owners()).containsEntry(3, "a");
        assertThat(game.phase()).isEqualTo(TurnPhase.TURN_END);
    }

    @Test
    void decliningLeavesStreetUnowned() {
        dice.then(1, 2);
        TestMoves.roll(game, "a");
        TestMoves.declineAndNobodyBids(game, "a");

        assertThat(game.owners()).doesNotContainKey(3);
        assertThat(alice.money()).isEqualTo(1500);
    }

    @Test
    void cannotActOutOfTurn() {
        assertThatThrownBy(() -> TestMoves.roll(game, "b"))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("не ваш ход");
    }

    @Test
    void cannotEndTurnBeforeRolling() {
        assertThatThrownBy(() -> game.endTurn("a")).isInstanceOf(GameException.class);
    }

    @Test
    void turnPassesToNextPlayer() {
        dice.then(1, 3); // -> 4 налог
        TestMoves.roll(game, "a");
        game.endTurn("a");

        assertThat(game.current()).isSameAs(bob);
        assertThat(game.phase()).isEqualTo(TurnPhase.WAITING_FOR_ROLL);
    }

    @Test
    void incomeTaxIsPaid() {
        dice.then(1, 3); // -> 4 Подоходный налог
        TestMoves.roll(game, "a");

        assertThat(alice.money()).isEqualTo(1500 - 200);
    }

    @Test
    void passingGoPaysSalary() {
        playAliceTurn(5, 6);   // 11 St. Charles — отказываемся
        playBobTurn(1, 3);
        playAliceTurn(6, 4);   // 21 Kentucky
        playBobTurn(1, 3);
        playAliceTurn(6, 5);   // 32 North Carolina
        playBobTurn(1, 3);
        dice.then(4, 5);       // 41 -> 1 Mediterranean, через Старт
        TestMoves.roll(game, "a");

        assertThat(alice.position()).isEqualTo(1);
        assertThat(alice.money()).isEqualTo(1500 + Game.GO_SALARY);
    }

    @Test
    void rentIsPaidToOwner() {
        dice.then(1, 2); // Alice -> 3 Baltic, покупает
        TestMoves.roll(game, "a");
        game.buy("a");
        game.endTurn("a");

        dice.then(1, 2); // Bob -> 3 Baltic
        TestMoves.roll(game, "b");

        assertThat(bob.money()).isEqualTo(1500 - 4);
        assertThat(alice.money()).isEqualTo(1500 - 60 + 4);
    }

    @Test
    void rentIsDoubledForFullColorGroup() {
        Tile baltic = game.board().tile(3);

        dice.then(1, 2);        // Alice -> 3 Baltic, покупает
        TestMoves.roll(game, "a");
        game.buy("a");
        game.endTurn("a");
        assertThat(game.rentFor(baltic)).isEqualTo(4);

        playBobTurn(1, 3);      // Bob -> 4
        playAliceTurn(5, 6);    // Alice -> 14
        playBobTurn(1, 3);      // Bob -> 8
        playAliceTurn(5, 6);    // Alice -> 25
        playBobTurn(1, 3);      // Bob -> 12
        playAliceTurn(5, 6);    // Alice -> 36
        playBobTurn(1, 3);      // Bob -> 16
        dice.then(1, 4);        // Alice -> 41 = 1 Mediterranean, покупает
        TestMoves.roll(game, "a");
        game.buy("a");

        assertThat(game.rentFor(baltic)).isEqualTo(8);
    }

    @Test
    void railroadRentGrowsWithCount() {
        dice.then(2, 3); // Alice -> 5 Reading Railroad
        TestMoves.roll(game, "a");
        game.buy("a");
        game.endTurn("a");

        dice.then(2, 3); // Bob -> 5
        TestMoves.roll(game, "b");

        assertThat(bob.money()).isEqualTo(1500 - 25);
    }

    @Test
    void doublesGiveExtraRoll() {
        dice.then(2, 2); // -> 4 налог
        TestMoves.roll(game, "a");

        assertThat(game.phase()).isEqualTo(TurnPhase.WAITING_FOR_ROLL);
        assertThat(game.current()).isSameAs(alice);
    }

    @Test
    void threeDoublesInRowSendToJail() {
        dice.then(1, 1).then(2, 2).then(3, 3);
        TestMoves.roll(game, "a"); // 2 Казна
        TestMoves.roll(game, "a"); // 6 Oriental
        TestMoves.declineAndNobodyBids(game, "a");
        TestMoves.roll(game, "a"); // третий дубль

        assertThat(alice.inJail()).isTrue();
        assertThat(alice.position()).isEqualTo(Board.JAIL_INDEX);
        assertThat(game.phase()).isEqualTo(TurnPhase.TURN_END);
    }

    @Test
    void goToJailTile() {
        // 30 = 5+6 (11) + 6+4 (21) + 4+5 (30)
        playAliceTurn(5, 6);
        playBobTurn(1, 3);
        playAliceTurn(6, 4);
        playBobTurn(1, 3);
        dice.then(4, 5);
        TestMoves.roll(game, "a");

        assertThat(alice.inJail()).isTrue();
        assertThat(alice.position()).isEqualTo(Board.JAIL_INDEX);
    }

    @Test
    void leavingJailByPayingFine() {
        sendAliceToJail();

        game.payJailFine("a");
        assertThat(alice.inJail()).isFalse();
        assertThat(alice.money()).isEqualTo(1500 - Game.JAIL_FINE);

        dice.then(1, 2);
        TestMoves.roll(game, "a");
        assertThat(alice.position()).isEqualTo(13);
    }

    @Test
    void leavingJailByDoublesMovesWithoutExtraRoll() {
        sendAliceToJail();

        dice.then(3, 3);
        TestMoves.roll(game, "a"); // -> 16 St. James
        TestMoves.declineAndNobodyBids(game, "a");

        assertThat(alice.inJail()).isFalse();
        assertThat(alice.position()).isEqualTo(16);
        assertThat(game.phase()).isEqualTo(TurnPhase.TURN_END);
    }

    @Test
    void thirdFailedJailRollForcesFine() {
        sendAliceToJail();
        for (int i = 0; i < 2; i++) {
            dice.then(1, 2);
            TestMoves.roll(game, "a");
            assertThat(alice.inJail()).isTrue();
            game.endTurn("a");
            playBobTurn(1, 3);
        }
        dice.then(1, 2); // третья попытка — платит 50 и идёт на 13
        TestMoves.roll(game, "a");

        assertThat(alice.inJail()).isFalse();
        assertThat(alice.position()).isEqualTo(13);
        assertThat(alice.money()).isEqualTo(1500 - Game.JAIL_FINE);
    }

    @Test
    void bankruptcyEndsTwoPlayerGame() {
        Player poor = new Player("p", "Poor", 100);
        Player rich = new Player("r", "Rich", 1500);
        Game g = newGame(poor, rich);

        dice.then(1, 3); // налог 200 при 100 на счету
        TestMoves.roll(g, "p");
        assertThat(g.currentDebt().hopeless()).isTrue(); // карточка «Банкрот»
        g.declareBankruptcy("p");

        assertThat(poor.bankrupt()).isTrue();
        assertThat(g.phase()).isEqualTo(TurnPhase.GAME_OVER);
        assertThat(g.winnerId()).isEqualTo("r");
    }

    // ---------------------------------------------------------------- помощники

    private void playAliceTurn(int d1, int d2) {
        playTurn("a", d1, d2);
    }

    private void playBobTurn(int d1, int d2) {
        playTurn("b", d1, d2);
    }

    /** Ход без дубля: бросок, отказ от покупки при необходимости, конец хода. */
    private void playTurn(String id, int d1, int d2) {
        dice.then(d1, d2);
        TestMoves.roll(game, id);
        if (game.phase() == TurnPhase.AWAITING_BUY_DECISION) {
            TestMoves.declineAndNobodyBids(game, id);
        }
        game.endTurn(id);
    }

    private void sendAliceToJail() {
        dice.then(1, 1).then(2, 2).then(3, 3);
        TestMoves.roll(game, "a");
        TestMoves.roll(game, "a");
        TestMoves.declineAndNobodyBids(game, "a");
        TestMoves.roll(game, "a");
        game.endTurn("a");
        playBobTurn(1, 3);
    }
}
