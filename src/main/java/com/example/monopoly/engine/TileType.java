package com.example.monopoly.engine;

public enum TileType {
    GO,
    PROPERTY,
    RAILROAD,
    UTILITY,
    TAX,
    CHANCE,
    COMMUNITY_CHEST,
    JAIL,
    FREE_PARKING,
    GO_TO_JAIL;

    public boolean isOwnable() {
        return this == PROPERTY || this == RAILROAD || this == UTILITY;
    }
}
