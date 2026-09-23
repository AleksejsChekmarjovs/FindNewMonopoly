package com.example.monopoly.engine;

/** Цветовые группы улиц и стоимость одного дома в группе. */
public enum ColorGroup {
    BROWN(50),
    LIGHT_BLUE(50),
    PINK(100),
    ORANGE(100),
    RED(150),
    YELLOW(150),
    GREEN(200),
    DARK_BLUE(200);

    private final int houseCost;

    ColorGroup(int houseCost) {
        this.houseCost = houseCost;
    }

    public int houseCost() {
        return houseCost;
    }
}
