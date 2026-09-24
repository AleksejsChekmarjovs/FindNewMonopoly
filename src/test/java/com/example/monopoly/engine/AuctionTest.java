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
        g.roll("a");
        g.declineBuy("a");
        return g;
    }

    @Test
    void declineStartsAuctionWithNextPlayer() {
        Game g = aliceDeclinesBaltic(alice, bob, carol);

        assertThat(g.phase()).isEqualTo(TurnPhase.AUCTION);
        assertThat(g.auction().tileIndex()).isEqualTo(3);
        assertThat(g.auction().bidders()).containsExactly("b", "c", "a");
        assertThat(g.auction().currentBidderId()).isEqualTo("b");
    }

    @Test
    void highestBidderWinsAndPaysBank() {
        Game g = aliceDeclinesBaltic(alice, bob);

        g.bid("b", 30);
        g.bid("a", 40);
        g.passAuction("b");

        assertThat(g.owners()).containsEntry(3, "a");
        assertThat(alice.money()).isEqualTo(1500 - 40);
        assertThat(bob.money()).isEqualTo(1500);
        assertThat(g.auction()).isNull();
        assertThat(g.phase()).isEqualTo(TurnPhase.TURN_END);
        assertThat(g.current()).isSameAs(alice);
    }

    @Test
    void bidBelowListPriceIsAllowed() {
        Game g = aliceDeclinesBaltic(alice, bob, carol);

        g.bid("b", 1);
        g.passAuction("c");
        g.passAuction("a");

        assertThat(g.owners()).containsEntry(3, "b");
        assertThat(bob.money()).isEqualTo(1500 - 1);
    }

    @Test
    void passedPlayerLeavesAuction() {
        Game g = aliceDeclinesBaltic(alice, bob, carol);

        g.passAuction("b");
        g.bid("c", 10);
        g.bid("a", 20);

        assertThat(g.auction().bidders()).containsExactly("c", "a");
        assertThat(g.auction().currentBidderId()).isEqualTo("c");

        g.bid("c", 25);
        g.passAuction("a");

        assertThat(g.owners()).containsEntry(3, "c");
        assertThat(carol.money()).isEqualTo(1500 - 25);
    }

    @Test
    void nobodyBidsPropertyStaysWithBank() {
        Game g = aliceDeclinesBaltic(alice, bob);

        g.passAuction("b");
        g.passAuction("a");

        assertThat(g.owners()).doesNotContainKey(3);
        assertThat(g.phase()).isEqualTo(TurnPhase.TURN_END);
    }

    @Test
    void bidMustExceedCurrentHighest() {
        Game g = aliceDeclinesBaltic(alice, bob);
        g.bid("b", 30);

        assertThatThrownBy(() -> g.bid("a", 30)).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> g.bid("a", 0)).isInstanceOf(GameException.class);
    }

    @Test
    void cannotBidMoreThanYouHave() {
        Game g = aliceDeclinesBaltic(alice, bob);

        assertThatThrownBy(() -> g.bid("b", 1501))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("Недостаточно денег");
    }

    @Test
    void onlyCurrentBidderCanAct() {
        Game g = aliceDeclinesBaltic(alice, bob);

        assertThatThrownBy(() -> g.bid("a", 10)).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> g.passAuction("a")).isInstanceOf(GameException.class);
    }

    @Test
    void regularActionsBlockedDuringAuction() {
        Game g = aliceDeclinesBaltic(alice, bob);

        assertThatThrownBy(() -> g.endTurn("a")).isInstanceOf(GameException.class);
        assertThatThrownBy(() -> g.roll("a")).isInstanceOf(GameException.class);
    }

    @Test
    void auctionOutsideAuctionPhaseFails() {
        Game g = newGame(alice, bob);

        assertThatThrownBy(() -> g.bid("a", 10))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("нет аукциона");
    }

    @Test
    void cannotAffordStartsAuctionImmediately() {
        Player poor = new Player("p", "Poor", 50);
        Game g = newGame(poor, bob);

        dice.then(4, 5); // -> 9 Connecticut Ave, $120
        g.roll("p");

        assertThat(g.phase()).isEqualTo(TurnPhase.AUCTION);
        assertThat(g.auction().tileIndex()).isEqualTo(9);
        assertThat(g.auction().currentBidderId()).isEqualTo("b");
    }

    @Test
    void doublesStillGiveExtraRollAfterAuction() {
        Game g = newGame(alice, bob);
        dice.then(3, 3); // -> 6 Oriental Ave
        g.roll("a");
        g.declineBuy("a");
        TestMoves.passAll(g);

        assertThat(g.phase()).isEqualTo(TurnPhase.WAITING_FOR_ROLL);
        assertThat(g.current()).isSameAs(alice);
    }
}
