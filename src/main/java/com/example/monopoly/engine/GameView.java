package com.example.monopoly.engine;

import java.util.List;
import java.util.Map;

/** Снимок состояния игры, который целиком отправляется клиентам. */
public record GameView(
        List<Tile> tiles,
        List<PlayerView> players,
        Map<Integer, String> owners,
        String currentPlayerId,
        TurnPhase phase,
        DiceRoll lastRoll,
        CardView lastCard,
        AuctionView auction,
        String winnerId,
        List<String> log
) {

    public record PlayerView(String id, String name, int money, int position, boolean inJail,
                             int jailFreeCards, boolean bankrupt) {
    }

    public record CardView(DeckType deck, String text) {
    }

    public record AuctionView(int tileIndex, int highestBid, String highestBidderId,
                              String currentBidderId, List<String> bidders) {
    }

    public static GameView of(Game game) {
        Card card = game.lastCard();
        Auction a = game.auction();
        return new GameView(
                game.board().tiles(),
                game.players().stream()
                        .map(p -> new PlayerView(p.id(), p.name(), p.money(), p.position(), p.inJail(),
                                p.jailFreeCards(), p.bankrupt()))
                        .toList(),
                game.owners(),
                game.current().id(),
                game.phase(),
                game.lastRoll(),
                card == null ? null : new CardView(card.deck(), card.text()),
                a == null ? null : new AuctionView(a.tileIndex(), a.highestBid(), a.highestBidderId(),
                        a.currentBidderId(), a.bidders()),
                game.winnerId(),
                game.log()
        );
    }
}
