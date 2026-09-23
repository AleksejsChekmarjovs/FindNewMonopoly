package com.example.monopoly.engine;

import java.util.ArrayDeque;
import java.util.Deque;

public class Player {

    private final String id;
    private final String name;
    private int money;
    private int position;
    private boolean inJail;
    private int jailTurns;
    private boolean bankrupt;
    /** Из какой колоды каждая карточка «Освободиться из тюрьмы» — чтобы вернуть её туда же. */
    private final Deque<DeckType> jailFreeCards = new ArrayDeque<>();

    public Player(String id, String name, int money) {
        this.id = id;
        this.name = name;
        this.money = money;
    }

    public String id() { return id; }
    public String name() { return name; }
    public int money() { return money; }
    public int position() { return position; }
    public boolean inJail() { return inJail; }
    public int jailTurns() { return jailTurns; }
    public boolean bankrupt() { return bankrupt; }
    public int jailFreeCards() { return jailFreeCards.size(); }

    void addMoney(int amount) { money += amount; }
    void setPosition(int position) { this.position = position; }
    void setBankrupt() { bankrupt = true; money = 0; }

    void goToJail() {
        position = Board.JAIL_INDEX;
        inJail = true;
        jailTurns = 0;
    }

    void releaseFromJail() {
        inJail = false;
        jailTurns = 0;
    }

    void incrementJailTurns() { jailTurns++; }

    void addJailFreeCard(DeckType deck) { jailFreeCards.addLast(deck); }
    DeckType takeJailFreeCard() { return jailFreeCards.removeFirst(); }
}
