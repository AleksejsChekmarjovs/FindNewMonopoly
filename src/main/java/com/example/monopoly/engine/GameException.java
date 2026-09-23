package com.example.monopoly.engine;

/** Недопустимое действие игрока (не его ход, не та фаза, не хватает денег и т.п.). */
public class GameException extends RuntimeException {

    public GameException(String message) {
        super(message);
    }
}
