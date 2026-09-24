package com.example.monopoly.engine;

/** Общие шаги для тестов. */
final class TestMoves {

    private TestMoves() {
    }

    /** Отказ от покупки, после которого на аукционе все пасуют — клетка остаётся у банка. */
    static void declineAndNobodyBids(Game g, String playerId) {
        g.declineBuy(playerId);
        passAll(g);
    }

    static void passAll(Game g) {
        while (g.phase() == TurnPhase.AUCTION) {
            g.passAuction(g.auction().currentBidderId());
        }
    }
}
