package com.example.monopoly.engine;

import java.util.ArrayDeque;
import java.util.Deque;

/** Кубики с заранее заданной последовательностью бросков. */
class FixedDice implements Dice {

    private final Deque<DiceRoll> rolls = new ArrayDeque<>();

    FixedDice then(int first, int second) {
        rolls.addLast(new DiceRoll(first, second));
        return this;
    }

    @Override
    public DiceRoll roll() {
        if (rolls.isEmpty()) {
            throw new IllegalStateException("В тесте закончились броски");
        }
        return rolls.removeFirst();
    }
}
