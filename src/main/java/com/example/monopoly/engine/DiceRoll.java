package com.example.monopoly.engine;

public record DiceRoll(int first, int second) {

    public int total() {
        return first + second;
    }

    public boolean isDouble() {
        return first == second;
    }
}
