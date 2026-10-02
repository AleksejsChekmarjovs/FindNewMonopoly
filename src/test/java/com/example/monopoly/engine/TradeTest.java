package com.example.monopoly.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Mediterranean (1) и Baltic (3) — коричневые, залог $30 (выкуп $33). */
class TradeTest {

    private static final int MEDITERRANEAN = 1;
    private static final int BALTIC = 3;

    private final FixedDice dice = new FixedDice();
    private final Player alice = new Player("a", "Alice", Game.START_MONEY);
    private final Player bob = new Player("b", "Bob", Game.START_MONEY);
    private final Player carol = new Player("c", "Carol", Game.START_MONEY);
    private final Game game = new Game(List.of(alice, bob, carol), dice,
            new Deck(List.of(Card.of(DeckType.CHANCE, "ничего", Card.Kind.GAIN, 0))),
            new Deck(List.of(Card.of(DeckType.COMMUNITY_CHEST, "ничего", Card.Kind.GAIN, 0))));

    private static TradeOffer offer(String to, List<Integer> give, List<Integer> take, int giveMoney, int takeMoney) {
        return new TradeOffer("a", to, give, take, giveMoney, takeMoney, 0, 0);
    }

    // ---------------------------------------------------------------- основной сценарий

    @Test
    void acceptedTradeSwapsPropertyAndMoney() {
        game.setOwner(MEDITERRANEAN, "a");
        game.setOwner(BALTIC, "b");

        game.proposeTrade(offer("b", List.of(MEDITERRANEAN), List.of(BALTIC), 20, 0)); // $80 против $60
        assertThat(game.phase()).isEqualTo(TurnPhase.TRADE_OFFER);
        assertThat(game.trade().toId()).isEqualTo("b");

        game.acceptTrade("b");

        assertThat(game.owners()).containsEntry(MEDITERRANEAN, "b").containsEntry(BALTIC, "a");
        assertThat(alice.money()).isEqualTo(1480);
        assertThat(bob.money()).isEqualTo(1520);
        assertThat(game.trade()).isNull();
        assertThat(game.phase()).isEqualTo(TurnPhase.WAITING_FOR_ROLL);
    }

    @Test
    void buyingPropertyForMoneyCompletesGroup() {
        game.setOwner(MEDITERRANEAN, "a");
        game.setOwner(BALTIC, "b");

        game.proposeTrade(offer("b", List.of(), List.of(BALTIC), 100, 0)); // $100 против $60
        game.acceptTrade("b");

        game.buildHouse("a", BALTIC); // группа собрана — можно строить
        assertThat(game.buildings()).containsEntry(BALTIC, 1);
    }

    @Test
    void rejectedTradeChangesNothing() {
        game.setOwner(BALTIC, "b");
        game.proposeTrade(offer("b", List.of(), List.of(BALTIC), 50, 0));

        game.rejectTrade("b");

        assertThat(game.owners()).containsEntry(BALTIC, "b");
        assertThat(alice.money()).isEqualTo(1500);
        assertThat(game.phase()).isEqualTo(TurnPhase.WAITING_FOR_ROLL);
    }

    @Test
    void proposerCanCancel() {
        game.setOwner(BALTIC, "b");
        game.proposeTrade(offer("b", List.of(), List.of(BALTIC), 50, 0));

        game.cancelTrade("a");

        assertThat(game.trade()).isNull();
        assertThat(game.phase()).isEqualTo(TurnPhase.WAITING_FOR_ROLL);
    }

    @Test
    void tradeAtEndOfTurnReturnsToTurnEnd() {
        dice.then(1, 3); // налог
        TestMoves.roll(game, "a");
        game.setOwner(BALTIC, "b");

        game.proposeTrade(offer("b", List.of(), List.of(BALTIC), 50, 0));
        game.acceptTrade("b");

        assertThat(game.phase()).isEqualTo(TurnPhase.TURN_END);
    }

    @Test
    void jailCardsCanBeTraded() {
        Deck chance = new Deck(List.of(Card.of(DeckType.CHANCE, "свобода", Card.Kind.JAIL_FREE)));
        Game g = new Game(List.of(alice, bob), dice, chance,
                new Deck(List.of(Card.of(DeckType.COMMUNITY_CHEST, "ничего", Card.Kind.GAIN, 0))));
        dice.then(3, 4); // Alice -> 7 Шанс: карточка
        TestMoves.roll(g, "a");

        g.proposeTrade(new TradeOffer("a", "b", List.of(), List.of(), 0, 30, 1, 0));
        g.acceptTrade("b");

        assertThat(alice.jailFreeCards()).isZero();
        assertThat(bob.jailFreeCards()).isEqualTo(1);
        assertThat(alice.money()).isEqualTo(1530);
    }

    // ---------------------------------------------------------------- заложенные клетки

    @Test
    void receiverOfMortgagedPropertyPaysTenPercent() {
        game.setOwner(BALTIC, "a");
        game.mortgage("a", BALTIC); // Alice +$30

        game.proposeTrade(offer("b", List.of(BALTIC), List.of(), 0, 40)); // $60 против $40
        game.acceptTrade("b");

        assertThat(game.owners()).containsEntry(BALTIC, "b");
        assertThat(game.mortgaged()).contains(BALTIC);      // остаётся заложенной
        assertThat(bob.money()).isEqualTo(1500 - 40 - 3);   // $40 Alice + 10% от залога $30
        assertThat(alice.money()).isEqualTo(1530 + 40);
    }

    // ---------------------------------------------------------------- проверки

    @Test
    void onlyCurrentPlayerProposesAtTheRightTime() {
        game.setOwner(BALTIC, "a");

        assertThatThrownBy(() -> game.proposeTrade(new TradeOffer("b", "a", List.of(), List.of(BALTIC), 10, 0, 0, 0)))
                .hasMessageContaining("в свой ход");

        dice.then(1, 5); // -> 6 Oriental, решение о покупке
        TestMoves.roll(game, "a");
        assertThatThrownBy(() -> game.proposeTrade(offer("b", List.of(BALTIC), List.of(), 0, 10)))
                .hasMessageContaining("до броска");
    }

    @Test
    void cannotTradeWhatYouDoNotOwn() {
        game.setOwner(BALTIC, "c");

        assertThatThrownBy(() -> game.proposeTrade(offer("b", List.of(), List.of(BALTIC), 10, 0)))
                .hasMessageContaining("не принадлежит");
        assertThatThrownBy(() -> game.proposeTrade(offer("b", List.of(4), List.of(), 0, 0)))
                .hasMessageContaining("нельзя обменять");
    }

    @Test
    void cannotTradeStreetsFromGroupWithBuildings() {
        game.setOwner(MEDITERRANEAN, "a");
        game.setOwner(BALTIC, "a");
        game.buildHouse("a", MEDITERRANEAN);

        // даже улицу без дома — дом стоит на соседней улице группы
        assertThatThrownBy(() -> game.proposeTrade(offer("b", List.of(BALTIC), List.of(), 0, 100)))
                .hasMessageContaining("продайте постройки");
    }

    // ---------------------------------------------------------------- равноценность

    @Test
    void twiceAsValuableSideIsForbiddenJustUnderIsAllowed() {
        game.setOwner(BALTIC, "b"); // $60

        assertThatThrownBy(() -> game.proposeTrade(offer("b", List.of(), List.of(BALTIC), 120, 0)))
                .hasMessageContaining("Неравный обмен: $120 против $60");
        assertThatThrownBy(() -> game.proposeTrade(offer("b", List.of(), List.of(BALTIC), 30, 0)))
                .hasMessageContaining("Неравный обмен");

        game.proposeTrade(offer("b", List.of(), List.of(BALTIC), 119, 0));
        assertThat(game.phase()).isEqualTo(TurnPhase.TRADE_OFFER);
    }

    @Test
    void giftsAreForbidden() {
        game.setOwner(BALTIC, "a");

        assertThatThrownBy(() -> game.proposeTrade(offer("b", List.of(BALTIC), List.of(), 0, 0)))
                .hasMessageContaining("Неравный обмен: $60 против $0");
        assertThatThrownBy(() -> game.proposeTrade(offer("b", List.of(), List.of(), 10, 0)))
                .hasMessageContaining("Неравный обмен");
    }

    @Test
    void jailCardCountsAsFiftyDollars() {
        assertThat(game.tradeValue(List.of(BALTIC, MEDITERRANEAN), 25, 1)).isEqualTo(60 + 60 + 25 + 50);
    }

    @Test
    void moneyAndCardsMustExist() {
        assertThatThrownBy(() -> game.proposeTrade(offer("b", List.of(), List.of(), 1501, 0)))
                .hasMessageContaining("нет $1501");
        assertThatThrownBy(() -> game.proposeTrade(offer("b", List.of(), List.of(), 0, 1501)))
                .hasMessageContaining("нет $1501");
        assertThatThrownBy(() -> game.proposeTrade(new TradeOffer("a", "b", List.of(), List.of(), 0, 0, 1, 0)))
                .hasMessageContaining("карточек");
        assertThatThrownBy(() -> game.proposeTrade(offer("b", List.of(), List.of(), -5, 0)))
                .hasMessageContaining("отрицательными");
    }

    @Test
    void emptyOrSelfTradeRejected() {
        assertThatThrownBy(() -> game.proposeTrade(offer("b", List.of(), List.of(), 0, 0)))
                .hasMessageContaining("Пустое");
        assertThatThrownBy(() -> game.proposeTrade(offer("a", List.of(), List.of(), 10, 0)))
                .hasMessageContaining("самим собой");
    }

    @Test
    void onlyAddresseeAnswersAndOnlyAuthorCancels() {
        game.setOwner(BALTIC, "b");
        game.proposeTrade(offer("b", List.of(), List.of(BALTIC), 50, 0));

        assertThatThrownBy(() -> game.acceptTrade("c")).hasMessageContaining("не вам");
        assertThatThrownBy(() -> game.acceptTrade("a")).hasMessageContaining("не вам");
        assertThatThrownBy(() -> game.cancelTrade("b")).hasMessageContaining("не ваше");
    }

    @Test
    void gameWaitsForAnswer() {
        game.setOwner(BALTIC, "b");
        game.setOwner(MEDITERRANEAN, "a");
        game.proposeTrade(offer("b", List.of(), List.of(BALTIC), 50, 0));

        assertThatThrownBy(() -> TestMoves.roll(game, "a")).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> game.mortgage("a", MEDITERRANEAN)).hasMessageContaining("Дождитесь ответа");
        assertThatThrownBy(() -> game.proposeTrade(offer("c", List.of(), List.of(), 10, 0)))
                .isInstanceOf(GameException.class);
    }

    @Test
    void noTradeToAnswer() {
        assertThatThrownBy(() -> game.acceptTrade("b")).hasMessageContaining("нет предложения");
    }
}
