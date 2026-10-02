package com.example.monopoly.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Коричневая группа: Mediterranean (1) и Baltic (3), дом стоит $50. */
class HousesTest {

    private static final int MEDITERRANEAN = 1;
    private static final int BALTIC = 3;

    private final FixedDice dice = new FixedDice();
    private final Player alice = new Player("a", "Alice", Game.START_MONEY);
    private final Player bob = new Player("b", "Bob", Game.START_MONEY);
    private final Game game = newGame(new Deck(List.of(Card.of(DeckType.CHANCE, "ничего", Card.Kind.GAIN, 0))));

    private Game newGame(Deck chance) {
        return new Game(List.of(alice, bob), dice, chance,
                new Deck(List.of(Card.of(DeckType.COMMUNITY_CHEST, "ничего", Card.Kind.GAIN, 0))));
    }

    private void giveAliceBrowns(Game g) {
        g.setOwner(MEDITERRANEAN, "a");
        g.setOwner(BALTIC, "a");
    }

    private void build(Game g, int... tiles) {
        for (int t : tiles) {
            g.buildHouse("a", t);
        }
    }

    // ---------------------------------------------------------------- строительство

    @Test
    void buildingRequiresFullColorGroup() {
        game.setOwner(BALTIC, "a");

        assertThatThrownBy(() -> game.buildHouse("a", BALTIC))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("соберите все улицы");
    }

    @Test
    void cannotBuildOnSomeoneElsesStreetOrNonStreet() {
        giveAliceBrowns(game);

        assertThatThrownBy(() -> game.buildHouse("a", 5)).hasMessageContaining("только на улицах");
        game.setOwner(6, "b");
        assertThatThrownBy(() -> game.buildHouse("a", 6)).hasMessageContaining("не ваша");
    }

    @Test
    void houseCostsMoneyAndRaisesRent() {
        giveAliceBrowns(game);
        assertThat(game.rentFor(game.board().tile(BALTIC))).isEqualTo(8); // полная группа без домов — ×2

        build(game, BALTIC);

        assertThat(alice.money()).isEqualTo(1500 - 50);
        assertThat(game.buildings()).containsEntry(BALTIC, 1);
        assertThat(game.housesInBank()).isEqualTo(Game.BANK_HOUSES - 1);
        assertThat(game.rentFor(game.board().tile(BALTIC))).isEqualTo(20);
    }

    @Test
    void mustBuildEvenly() {
        giveAliceBrowns(game);
        build(game, BALTIC);

        assertThatThrownBy(() -> game.buildHouse("a", BALTIC)).hasMessageContaining("равномерно");

        build(game, MEDITERRANEAN, BALTIC);
        assertThat(game.buildings()).containsEntry(MEDITERRANEAN, 1).containsEntry(BALTIC, 2);
    }

    @Test
    void fifthLevelIsHotelAndReturnsHousesToBank() {
        giveAliceBrowns(game);
        for (int i = 0; i < 4; i++) {
            build(game, MEDITERRANEAN, BALTIC);
        }
        assertThat(game.housesInBank()).isEqualTo(Game.BANK_HOUSES - 8);

        build(game, BALTIC);

        assertThat(game.buildings()).containsEntry(BALTIC, Game.HOTEL);
        assertThat(game.housesInBank()).isEqualTo(Game.BANK_HOUSES - 4);
        assertThat(game.hotelsInBank()).isEqualTo(Game.BANK_HOTELS - 1);
        assertThat(game.rentFor(game.board().tile(BALTIC))).isEqualTo(450);
        assertThat(alice.money()).isEqualTo(1500 - 9 * 50);
    }

    @Test
    void cannotBuildAboveHotel() {
        giveAliceBrowns(game);
        for (int i = 0; i < 5; i++) {
            build(game, MEDITERRANEAN, BALTIC);
        }

        assertThatThrownBy(() -> game.buildHouse("a", BALTIC)).hasMessageContaining("уже стоит отель");
    }

    @Test
    void cannotBuildWithoutMoney() {
        Player poor = new Player("p", "Poor", 40);
        Game g = new Game(List.of(poor, bob), dice);
        g.setOwner(MEDITERRANEAN, "p");
        g.setOwner(BALTIC, "p");

        assertThatThrownBy(() -> g.buildHouse("p", BALTIC)).hasMessageContaining("Недостаточно денег");
    }

    @Test
    void bankRunsOutOfHouses() {
        // 3 группы по 3 улицы (light blue, pink, orange) — 36 мест под дома, в банке только 32
        int[] streets = {6, 8, 9, 11, 13, 14, 16, 18, 19};
        Player rich = new Player("r", "Rich", 100_000);
        Game g = new Game(List.of(rich, bob), dice);
        for (int s : streets) {
            g.setOwner(s, "r");
        }
        int built = 0;
        outer:
        for (int round = 0; round < 4; round++) {
            for (int s : streets) {
                if (built == Game.BANK_HOUSES) {
                    break outer;
                }
                g.buildHouse("r", s);
                built++;
            }
        }
        assertThat(g.housesInBank()).isZero();

        assertThatThrownBy(() -> g.buildHouse("r", 19)).hasMessageContaining("закончились дома");
    }

    @Test
    void onlyCurrentPlayerBuildsAndNotDuringAuction() {
        giveAliceBrowns(game);
        game.setOwner(37, "b");
        game.setOwner(39, "b");

        assertThatThrownBy(() -> game.buildHouse("b", 37)).hasMessageContaining("не ваш ход");

        dice.then(2, 4); // -> 6 Oriental, решение о покупке — строить можно
        TestMoves.roll(game, "a");
        build(game, BALTIC);

        game.declineBuy("a"); // аукцион — нельзя
        assertThatThrownBy(() -> game.buildHouse("a", MEDITERRANEAN)).hasMessageContaining("аукциона");

        TestMoves.passAll(game);
        build(game, MEDITERRANEAN); // TURN_END — можно
        assertThat(game.buildings()).containsEntry(BALTIC, 1).containsEntry(MEDITERRANEAN, 1);
    }

    // ---------------------------------------------------------------- продажа

    @Test
    void sellingReturnsHalfPrice() {
        giveAliceBrowns(game);
        build(game, MEDITERRANEAN, BALTIC);

        game.sellHouse("a", BALTIC);

        assertThat(alice.money()).isEqualTo(1500 - 100 + 25);
        assertThat(game.buildings()).doesNotContainKey(BALTIC).containsEntry(MEDITERRANEAN, 1);
        assertThat(game.housesInBank()).isEqualTo(Game.BANK_HOUSES - 1);
    }

    @Test
    void mustSellEvenly() {
        giveAliceBrowns(game);
        build(game, MEDITERRANEAN, BALTIC, MEDITERRANEAN);

        assertThatThrownBy(() -> game.sellHouse("a", BALTIC)).hasMessageContaining("равномерно");
        game.sellHouse("a", MEDITERRANEAN);
    }

    @Test
    void cannotSellWhenNothingBuilt() {
        giveAliceBrowns(game);

        assertThatThrownBy(() -> game.sellHouse("a", BALTIC)).hasMessageContaining("нет построек");
    }

    @Test
    void sellingHotelGivesBackFourHouses() {
        giveAliceBrowns(game);
        for (int i = 0; i < 5; i++) {
            build(game, MEDITERRANEAN, BALTIC);
        }
        int housesBefore = game.housesInBank();

        game.sellHouse("a", BALTIC);

        assertThat(game.buildings()).containsEntry(BALTIC, 4);
        assertThat(game.housesInBank()).isEqualTo(housesBefore - 4);
        assertThat(game.hotelsInBank()).isEqualTo(Game.BANK_HOTELS - 1);
    }

    // ---------------------------------------------------------------- влияние на игру

    @Test
    void rentWithHousesIsPaidOnLanding() {
        giveAliceBrowns(game);
        build(game, MEDITERRANEAN, BALTIC);
        dice.then(1, 3);
        TestMoves.roll(game, "a");
        game.endTurn("a");

        dice.then(1, 2); // Bob -> 3 Baltic, 1 дом
        TestMoves.roll(game, "b");

        assertThat(bob.money()).isEqualTo(1500 - 20);
    }

    @Test
    void repairsCardChargesPerBuilding() {
        Game g = newGame(new Deck(List.of(new Card(DeckType.CHANCE, "ремонт", Card.Kind.REPAIRS, 25, 100))));
        g.setOwner(MEDITERRANEAN, "a");
        g.setOwner(BALTIC, "a");
        for (int i = 0; i < 4; i++) {
            build(g, MEDITERRANEAN, BALTIC);
        }
        build(g, BALTIC); // Mediterranean: 4 дома, Baltic: отель
        int before = alice.money();

        dice.then(3, 4); // -> 7 Шанс
        TestMoves.roll(g, "a");

        assertThat(alice.money()).isEqualTo(before - (4 * 25 + 100));
    }

    @Test
    void bankruptcyReturnsBuildingsToBank() {
        Player alice = new Player("a", "Alice", 110);
        Game g = new Game(List.of(alice, bob), dice);
        g.setOwner(MEDITERRANEAN, "a");
        g.setOwner(BALTIC, "a");
        g.buildHouse("a", MEDITERRANEAN);
        g.buildHouse("a", BALTIC); // осталось $10

        dice.then(1, 3); // налог $200 — даже с продажей домов не хватает
        TestMoves.roll(g, "a");
        assertThat(g.currentDebt().hopeless()).isTrue(); // карточка «Банкрот»
        g.declareBankruptcy("a");

        assertThat(alice.bankrupt()).isTrue();
        assertThat(g.buildings()).isEmpty();
        assertThat(g.housesInBank()).isEqualTo(Game.BANK_HOUSES);
    }
}
