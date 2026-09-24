package com.example.monopoly.engine;

import static com.example.monopoly.engine.Card.Kind.*;
import static com.example.monopoly.engine.DeckType.CHANCE;
import static com.example.monopoly.engine.DeckType.COMMUNITY_CHEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** «Шанс» на клетке 7, «Общественная казна» на клетках 2 и 17. */
class CardTest {

    private static final Card NOTHING_CHANCE = Card.of(CHANCE, "ничего", GAIN, 0);
    private static final Card NOTHING_CHEST = Card.of(COMMUNITY_CHEST, "ничего", GAIN, 0);

    private final FixedDice dice = new FixedDice();
    private final Player alice = new Player("a", "Alice", Game.START_MONEY);
    private final Player bob = new Player("b", "Bob", Game.START_MONEY);

    private Game game(List<Card> chance, List<Card> chest) {
        return new Game(List.of(alice, bob), dice, new Deck(chance), new Deck(chest));
    }

    private Game withChance(Card... cards) {
        return game(List.of(cards), List.of(NOTHING_CHEST));
    }

    private Game withChest(Card... cards) {
        return game(List.of(NOTHING_CHANCE), List.of(cards));
    }

    // ---------------------------------------------------------------- перемещения

    @Test
    void moveToStreetWithoutPassingGo() {
        Game g = withChance(Card.of(CHANCE, "Boardwalk", MOVE_TO, 39));
        dice.then(3, 4);
        g.roll("a");

        assertThat(alice.position()).isEqualTo(39);
        assertThat(alice.money()).isEqualTo(1500);
        assertThat(g.phase()).isEqualTo(TurnPhase.AWAITING_BUY_DECISION);
        assertThat(g.lastCard().text()).isEqualTo("Boardwalk");
    }

    @Test
    void advanceToGoPaysSalary() {
        Game g = withChance(Card.of(CHANCE, "Старт", MOVE_TO, 0));
        dice.then(3, 4);
        g.roll("a");

        assertThat(alice.position()).isEqualTo(0);
        assertThat(alice.money()).isEqualTo(1500 + Game.GO_SALARY);
        assertThat(g.phase()).isEqualTo(TurnPhase.TURN_END);
    }

    @Test
    void goBackThreeSpacesResolvesNewTile() {
        Game g = withChance(Card.of(CHANCE, "назад", MOVE_BACK, 3));
        dice.then(3, 4); // 7 -> 4 Подоходный налог
        g.roll("a");

        assertThat(alice.position()).isEqualTo(4);
        assertThat(alice.money()).isEqualTo(1500 - 200);
    }

    @Test
    void nearestRailroadOwnedPaysDoubleRent() {
        Game g = withChance(Card.of(CHANCE, "ж/д", NEAREST_RAILROAD));
        play(g, "a", 1, 3);        // Alice -> 4, налог 200
        play(g, "b", 1, 2);        // Bob -> 3
        dice.then(5, 6);           // Alice -> 15 Pennsylvania Railroad
        g.roll("a");
        g.buy("a");
        g.endTurn("a");

        dice.then(1, 3);           // Bob -> 7 Шанс -> 15
        g.roll("b");

        assertThat(bob.position()).isEqualTo(15);
        assertThat(bob.money()).isEqualTo(1500 - 50);
        assertThat(alice.money()).isEqualTo(1500 - 200 - 200 + 50);
    }

    @Test
    void nearestRailroadUnownedOffersPurchase() {
        Game g = withChance(Card.of(CHANCE, "ж/д", NEAREST_RAILROAD));
        dice.then(3, 4);
        g.roll("a");

        assertThat(alice.position()).isEqualTo(15);
        assertThat(g.phase()).isEqualTo(TurnPhase.AWAITING_BUY_DECISION);
    }

    @Test
    void nearestUtilityOwnedPaysTenTimesNewRoll() {
        Game g = withChance(Card.of(CHANCE, "предприятие", NEAREST_UTILITY));
        play(g, "a", 1, 3);        // Alice -> 4
        play(g, "b", 1, 2);        // Bob -> 3
        dice.then(3, 5);           // Alice -> 12 Электростанция
        g.roll("a");
        g.buy("a");
        g.endTurn("a");

        dice.then(1, 3).then(2, 3); // Bob -> 7 Шанс -> 12, бросок для аренды 5
        g.roll("b");

        assertThat(bob.position()).isEqualTo(12);
        assertThat(bob.money()).isEqualTo(1500 - 50);
    }

    @Test
    void nearestFromLastChanceWrapsThroughGo() {
        // Проверяем поиск ближайшей клетки через «Старт»: 36 -> 5
        Game g = withChance(Card.of(CHANCE, "ж/д", NEAREST_RAILROAD));
        play(g, "a", 5, 6);  // 11
        play(g, "b", 1, 3);  // 4
        play(g, "a", 5, 6);  // 22 Шанс -> 25
        play(g, "b", 1, 3);  // 8
        dice.then(5, 6);     // 25 -> 36 Шанс -> 5, через «Старт»
        g.roll("a");

        assertThat(alice.position()).isEqualTo(5);
        assertThat(alice.money()).isEqualTo(1500 + Game.GO_SALARY);
    }

    // ---------------------------------------------------------------- тюрьма

    @Test
    void goToJailCard() {
        Game g = withChance(Card.of(CHANCE, "тюрьма", GO_TO_JAIL));
        dice.then(3, 4);
        g.roll("a");

        assertThat(alice.inJail()).isTrue();
        assertThat(alice.position()).isEqualTo(Board.JAIL_INDEX);
        assertThat(alice.money()).isEqualTo(1500);
        assertThat(g.phase()).isEqualTo(TurnPhase.TURN_END);
    }

    @Test
    void jailFreeCardIsKeptAndUsed() {
        Deck chance = new Deck(List.of(Card.of(CHANCE, "свобода", JAIL_FREE), NOTHING_CHANCE));
        Deck chest = new Deck(List.of(Card.of(COMMUNITY_CHEST, "тюрьма", GO_TO_JAIL)));
        Game g = new Game(List.of(alice, bob), dice, chance, chest);

        play(g, "a", 3, 4);        // 7 Шанс -> карточка у Alice
        assertThat(alice.jailFreeCards()).isEqualTo(1);
        assertThat(chance.size()).isEqualTo(1);

        play(g, "b", 1, 2);        // Bob -> 3
        play(g, "a", 4, 6);        // 17 Казна -> тюрьма
        assertThat(alice.inJail()).isTrue();
        play(g, "b", 1, 2);        // Bob -> 6

        g.useJailFreeCard("a");

        assertThat(alice.inJail()).isFalse();
        assertThat(alice.jailFreeCards()).isZero();
        assertThat(chance.size()).isEqualTo(2);
        assertThat(alice.money()).isEqualTo(1500);
    }

    @Test
    void cannotUseJailFreeCardWithoutOne() {
        Game g = withChance(Card.of(CHANCE, "тюрьма", GO_TO_JAIL));
        play(g, "a", 3, 4);
        play(g, "b", 1, 2);

        assertThatThrownBy(() -> g.useJailFreeCard("a")).isInstanceOf(GameException.class);
    }

    // ---------------------------------------------------------------- деньги

    @Test
    void gainAndPayBank() {
        Game g = withChest(Card.of(COMMUNITY_CHEST, "ошибка банка", GAIN, 200),
                Card.of(COMMUNITY_CHEST, "врач", PAY, 50));
        dice.then(1, 1);            // Alice -> 2 Казна: +200, дубль — ещё бросок
        g.roll("a");
        assertThat(alice.money()).isEqualTo(1700);
        play(g, "a", 1, 2);         // Alice -> 5

        dice.then(1, 1);            // Bob -> 2 Казна: -50
        g.roll("b");
        assertThat(bob.money()).isEqualTo(1450);
    }

    @Test
    void birthdayCollectsFromEveryPlayer() {
        Game g = withChest(Card.of(COMMUNITY_CHEST, "день рождения", GAIN_FROM_EACH, 10));
        dice.then(1, 1);
        g.roll("a");

        assertThat(alice.money()).isEqualTo(1510);
        assertThat(bob.money()).isEqualTo(1490);
    }

    @Test
    void chairmanPaysEveryPlayer() {
        Game g = withChance(Card.of(CHANCE, "председатель", PAY_EACH, 50));
        dice.then(3, 4);
        g.roll("a");

        assertThat(alice.money()).isEqualTo(1450);
        assertThat(bob.money()).isEqualTo(1550);
    }

    @Test
    void repairsWithoutBuildingsCostNothing() {
        Game g = withChance(new Card(CHANCE, "ремонт", REPAIRS, 25, 100));
        dice.then(3, 4);
        g.roll("a");

        assertThat(alice.money()).isEqualTo(1500);
        assertThat(g.phase()).isEqualTo(TurnPhase.TURN_END);
    }

    // ---------------------------------------------------------------- колоды

    @Test
    void drawnCardGoesToBottom() {
        Card first = Card.of(CHANCE, "1", GAIN, 1);
        Card second = Card.of(CHANCE, "2", GAIN, 2);
        Deck deck = new Deck(List.of(first, second));

        assertThat(deck.draw()).isSameAs(first);
        assertThat(deck.draw()).isSameAs(second);
        assertThat(deck.draw()).isSameAs(first);
    }

    @Test
    void classicDecksHaveSixteenCards() {
        assertThat(Deck.chanceCards(new Board())).hasSize(16).allMatch(c -> c.deck() == CHANCE);
        assertThat(Deck.communityChestCards()).hasSize(16).allMatch(c -> c.deck() == COMMUNITY_CHEST);
    }

    // ---------------------------------------------------------------- помощники

    /** Ход без дубля: бросок, отказ от покупки при необходимости, конец хода. */
    private void play(Game g, String id, int d1, int d2) {
        dice.then(d1, d2);
        g.roll(id);
        if (g.phase() == TurnPhase.AWAITING_BUY_DECISION) {
            TestMoves.declineAndNobodyBids(g, id);
        }
        g.endTurn(id);
    }
}
