package com.example.monopoly.engine;

import java.util.concurrent.ThreadLocalRandom;

/** Кубики вынесены в интерфейс, чтобы в тестах подставлять заранее известные броски. */
public interface Dice {

    DiceRoll roll();

    static Dice random() {
        return () -> {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            return new DiceRoll(r.nextInt(1, 7), r.nextInt(1, 7));
        };
    }
}
