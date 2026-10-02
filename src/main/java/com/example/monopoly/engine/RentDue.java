package com.example.monopoly.engine;

/** Аренда к оплате: текущий игрок попал на клетку {@code tileIndex} игрока {@code ownerId}. */
public record RentDue(String ownerId, int tileIndex, int amount) {
}
