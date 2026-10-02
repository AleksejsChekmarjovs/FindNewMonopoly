package com.example.monopoly.engine;

/**
 * Платёж, который текущий игрок должен подтвердить кнопкой: аренда за клетку другого игрока или налог.
 *
 * @param creditorId кому платить: владелец клетки (аренда) или {@code null} — банку (налог)
 * @param tileIndex клетка, за которую платёж
 */
public record PaymentDue(String creditorId, int tileIndex, int amount) {
}
