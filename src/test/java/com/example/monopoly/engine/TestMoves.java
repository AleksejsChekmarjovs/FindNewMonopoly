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

    /**
     * Бросок, после которого игрок сразу делает то, о чём его спрашивают окна:
     * закрывает карточку «Шанс»/«Казна» (она выполняется) и платит аренду или налог.
     */
    static void roll(Game g, String playerId) {
        g.roll(playerId);
        while (g.phase() == TurnPhase.CARD_REVEAL || g.phase() == TurnPhase.PAYMENT_DUE) {
            if (g.phase() == TurnPhase.CARD_REVEAL) {
                g.closeCard(g.current().id());
            } else {
                g.payDue(g.current().id());
            }
        }
    }

    static void passAll(Game g) {
        while (g.phase() == TurnPhase.AUCTION) {
            g.passAuction(g.auction().currentBidderId());
        }
    }
}
