package com.example.monopoly.engine;

/**
 * Долг, который игрок не смог оплатить сразу: он должен продать постройки или заложить имущество
 * и заплатить — или объявить банкротство.
 *
 * @param creditorId кому платить; {@code null} — банку
 * @param hopeless долг не покрыть даже продажей и залогом всего имущества — остаётся только банкротство
 */
public record Debt(String debtorId, String creditorId, int amount, boolean hopeless) {

    public Debt(String debtorId, String creditorId, int amount) {
        this(debtorId, creditorId, amount, false);
    }
}
