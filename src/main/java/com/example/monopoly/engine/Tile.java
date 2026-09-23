package com.example.monopoly.engine;

import java.util.List;

/**
 * Клетка поля.
 *
 * @param rent для улиц — аренда [без домов, 1 дом, 2, 3, 4, отель]; для прочих пусто
 * @param taxAmount сумма налога для клеток типа TAX
 */
public record Tile(
        int index,
        String name,
        TileType type,
        int price,
        ColorGroup group,
        List<Integer> rent,
        int taxAmount
) {

    static Tile special(int index, String name, TileType type) {
        return new Tile(index, name, type, 0, null, List.of(), 0);
    }

    static Tile street(int index, String name, int price, ColorGroup group, Integer... rent) {
        return new Tile(index, name, TileType.PROPERTY, price, group, List.of(rent), 0);
    }

    static Tile railroad(int index, String name) {
        return new Tile(index, name, TileType.RAILROAD, 200, null, List.of(), 0);
    }

    static Tile utility(int index, String name) {
        return new Tile(index, name, TileType.UTILITY, 150, null, List.of(), 0);
    }

    static Tile tax(int index, String name, int amount) {
        return new Tile(index, name, TileType.TAX, 0, null, List.of(), amount);
    }
}
