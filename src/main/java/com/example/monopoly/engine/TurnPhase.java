package com.example.monopoly.engine;

/**
 * Фазы хода. Каждая команда допустима только в определённой фазе — это и есть машина состояний.
 *
 * <pre>
 * WAITING_FOR_ROLL ──roll──► (AWAITING_BUY_DECISION ──buy──►) TURN_END ──endTurn──► WAITING_FOR_ROLL (след. игрок)
 *        ▲                            │ decline              ▲   │
 *        │                            ▼                      │   │
 *        │                         AUCTION ──bid/pass...─────┘   │
 *        └──────────────── дубль: бросок ещё раз ────────────────┘
 * </pre>
 */
public enum TurnPhase {
    WAITING_FOR_ROLL,
    AWAITING_BUY_DECISION,
    /** Торги за клетку; ходит не текущий игрок, а {@link Auction#currentBidderId()}. */
    AUCTION,
    /**
     * Кто-то не смог заплатить сразу; ходит должник ({@link Game#currentDebt()}): продаёт постройки,
     * закладывает имущество, платит — или объявляет банкротство. После этого игра продолжается с того же места.
     */
    PAYING_DEBT,
    /** Текущий игрок предложил обмен; ждём ответа адресата ({@link Game#trade()}). */
    TRADE_OFFER,
    TURN_END,
    GAME_OVER
}
