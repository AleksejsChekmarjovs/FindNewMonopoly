package com.example.monopoly.engine;

/**
 * Долг, который игрок не смог оплатить сразу: он должен продать постройки или заложить имущество
 * и заплатить — или объявить банкротство.
 *
 * @param creditorId кому платить; {@code null} — банку
 */
public record Debt(String debtorId, String creditorId, int amount) {
}
