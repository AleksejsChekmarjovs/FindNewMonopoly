package com.example.monopoly.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class AuctionTest {

    private final FixedDice dice = new FixedDice();
    private final Player alice = new Player("a", "Alice", Game.START_MONEY);
    private final Player bob = new Player("b", "Bob", Game.START_MONEY);
    private final Player carol = new Player("c", "Carol", Game.START_MONEY);

    private Game newGame(Player... players) {
        return new Game(List.of(players), dice,
                new Deck(List.of(Card.of(DeckType.CHANCE, "ничего", Card.Kind.GAIN, 0))),
                new Deck(List.of(Card.of(DeckType.COMMUNITY_CHEST, "ничего", Card.Kind.GAIN, 0))));
    }

    /** Alice попадает на Baltic Ave (3, цена $60) и отказывается покупать. */
    private Game aliceDeclinesBaltic(Player... players) {
        Game g = newGame(players);
        dice.then(1, 2);
        TestMoves.roll(g, "a");
        g.declineBuy("a");
        return g;
    }

    // ---------------------------------------------------------------- кто участвует

    @Test
    void declineStartsAuctionWithNextPlayerWithoutTheDecliner() {
        Game g = aliceDeclinesBaltic(alice, bob, carol);

        assertThat(g.phase()).isEqualTo(TurnPhase.AUCTION);
        assertThat(g.auction().tileIndex()).isEqualTo(3);
        assertThat(g.auction().bidders()).containsExactly("b", "c");
        assertThat(g.auction().startPrice()).isEqualTo(60);
        assertThat(g.auction().minBid()).isEqualTo(60);
        assertThat(g.auction().currentBidderId()).isEqualTo("b");
    }

    @Test
    void declinerCannotBid() {
        Game g = aliceDeclinesBaltic(alice, bob, carol);
        g.bid("b", 60);

        assertThatThrownBy(() -> g.bid("a", 70)).hasMessageContaining("не ваша очередь");
    }

    @Test
    void playersWhoCannotAffordStartPriceDoNotTakePart() {
        Player poorBob = new Player("b", "Bob", 59);
        Game g = aliceDeclinesBaltic(alice, poorBob, carol);

        assertThat(g.auction().bidders()).containsExactly("c");
        assertThat(g.log()).anyMatch(l -> l.contains("Bob не участвует в аукционе"));
    }

    @Test
    void nobodyCanAffordPropertyStaysWithBankWithoutAuction() {
        Game g = aliceDeclinesBaltic(alice, new Player("b", "Bob", 10));

        assertThat(g.auction()).isNull();
        assertThat(g.owners()).doesNotContainKey(3);
        assertThat(g.phase()).isEqualTo(TurnPhase.TURN_END);
    }

    @Test
    void bidderWhoCannotAffordNextBidDropsOut() {
        Player bob100 = new Player("b", "Bob", 100);
        Game g = aliceDeclinesBaltic(alice, bob100, carol);

        g.bid("b", 60);
        g.bid("c", 100); // Bob нужно $101, у него $100 — выбывает, Carol побеждает

        assertThat(g.auction()).isNull();
        assertThat(g.owners()).containsEntry(3, "c");
        assertThat(carol.money()).isEqualTo(1500 - 100);
        assertThat(g.log()).anyMatch(l -> l.contains("Bob выбывает из аукциона"));
    }

    // ---------------------------------------------------------------- ставки

    @Test
    void highestBidderWinsAndPaysBank() {
        Game g = aliceDeclinesBaltic(alice, bob, carol);

        g.bid("b", 60);
        g.bid("c", 70);
        g.passAuction("b");

        assertThat(g.owners()).containsEntry(3, "c");
        assertThat(carol.money()).isEqualTo(1500 - 70);
        assertThat(bob.money()).isEqualTo(1500);
        assertThat(g.auction()).isNull();
        assertThat(g.phase()).isEqualTo(TurnPhase.TURN_END);
        assertThat(g.current()).isSameAs(alice);
    }

    @Test
    void singleBidderWinsAtStartPrice() {
        Game g = aliceDeclinesBaltic(alice, bob);

        assertThat(g.auction().bidders()).containsExactly("b");
        g.bid("b", 60);

        assertThat(g.owners()).containsEntry(3, "b");
        assertThat(bob.money()).isEqualTo(1500 - 60);
    }

    @Test
    void firstBidMustBeAtLeastStartPrice() {
        Game g = aliceDeclinesBaltic(alice, bob, carol);

        assertThatThrownBy(() -> g.bid("b", 59)).hasMessageContaining("Минимальная ставка — $60");
        g.bid("b", 60);
        assertThat(g.auction().minBid()).isEqualTo(61);
    }

    @Test
    void bidMustExceedCurrentHighest() {
        Game g = aliceDeclinesBaltic(alice, bob, carol);
        g.bid("b", 80);

        assertThatThrownBy(() -> g.bid("c", 80)).hasMessageContaining("Минимальная ставка — $81");
        g.bid("c", 81);
        assertThat(g.auction().highestBidderId()).isEqualTo("c");
    }

    @Test
    void passedPlayerLeavesAuction() {
        Player dave = new Player("d", "Dave", Game.START_MONEY);
        Game g = aliceDeclinesBaltic(alice, bob, carol, dave);

        g.passAuction("b");
        g.bid("c", 60);
        g.bid("d", 70);

        assertThat(g.auction().bidders()).containsExactly("c", "d");
        assertThat(g.auction().currentBidderId()).isEqualTo("c");

        g.bid("c", 75);
        g.passAuction("d");

        assertThat(g.owners()).containsEntry(3, "c");
        assertThat(carol.money()).isEqualTo(1500 - 75);
    }

    @Test
    void nobodyBidsPropertyStaysWithBank() {
        Game g = aliceDeclinesBaltic(alice, bob, carol);

        g.passAuction("b");
        g.passAuction("c");

        assertThat(g.owners()).doesNotContainKey(3);
        assertThat(g.phase()).isEqualTo(TurnPhase.TURN_END);
    }

    @Test
    void cannotBidMoreThanYouHave() {
        Game g = aliceDeclinesBaltic(alice, bob, carol);

        assertThatThrownBy(() -> g.bid("b", 1501))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("Недостаточно денег");
    }

    @Test
    void onlyCurrentBidderCanAct() {
        Game g = aliceDeclinesBaltic(alice, bob, carol);

        assertThatThrownBy(() -> g.bid("c", 60)).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> g.passAuction("c")).isInstanceOf(GameException.class);
    }

    // ---------------------------------------------------------------- ход игры

    @Test
    void regularActionsBlockedDuringAuction() {
        Game g = aliceDeclinesBaltic(alice, bob);

        assertThatThrownBy(() -> g.endTurn("a")).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> TestMoves.roll(g, "a")).isInstanceOf(GameException.class);
    }

    @Test
    void auctionOutsideAuctionPhaseFails() {
        Game g = newGame(alice, bob);

        assertThatThrownBy(() -> g.bid("a", 10))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("нет аукциона");
    }

    @Test
    void playerWhoCannotAffordStillDecidesThenAuctionStarts() {
        Player poor = new Player("p", "Poor", 50);
        Game g = newGame(poor, bob);

        dice.then(4, 5); // -> 9 Connecticut Ave, $120
        TestMoves.roll(g, "p");

        // Можно заложить имущество и купить — поэтому решение всё равно за игроком
        assertThat(g.phase()).isEqualTo(TurnPhase.AWAITING_BUY_DECISION);
        assertThatThrownBy(() -> g.buy("p")).hasMessageContaining("Недостаточно денег");

        g.declineBuy("p");
        assertThat(g.phase()).isEqualTo(TurnPhase.AUCTION);
        assertThat(g.auction().tileIndex()).isEqualTo(9);
        assertThat(g.auction().currentBidderId()).isEqualTo("b");
    }

    @Test
    void doublesStillGiveExtraRollAfterAuction() {
        Game g = newGame(alice, bob);
        dice.then(3, 3); // -> 6 Oriental Ave
        TestMoves.roll(g, "a");
        g.declineBuy("a");
        TestMoves.passAll(g);

        assertThat(g.phase()).isEqualTo(TurnPhase.WAITING_FOR_ROLL);
        assertThat(g.current()).isSameAs(alice);
    }
}
