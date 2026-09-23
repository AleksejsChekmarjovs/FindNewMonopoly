package com.example.monopoly.engine;

/**
 * Фазы хода. Каждая команда допустима только в определённой фазе — это и есть машина состояний.
 *
 * <pre>
 * WAITING_FOR_ROLL ──roll──► (AWAITING_BUY_DECISION ──buy/decline──►) TURN_END ──endTurn──► WAITING_FOR_ROLL (след. игрок)
 *        ▲                                                               │
 *        └────────────────────── дубль: бросок ещё раз ──────────────────┘
 * </pre>
 */
public enum TurnPhase {
    WAITING_FOR_ROLL,
    AWAITING_BUY_DECISION,
    TURN_END,
    GAME_OVER
}
