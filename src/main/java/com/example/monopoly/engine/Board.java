package com.example.monopoly.engine;

import static com.example.monopoly.engine.ColorGroup.*;
import static com.example.monopoly.engine.TileType.*;

import java.util.List;

/** Классическое поле из 40 клеток. Названия можно заменить на свои — логика от них не зависит. */
public final class Board {

    public static final int SIZE = 40;
    public static final int JAIL_INDEX = 10;

    private final List<Tile> tiles = List.of(
            Tile.special(0, "Старт", GO),
            Tile.street(1, "Mediterranean Ave", 60, BROWN, 2, 10, 30, 90, 160, 250),
            Tile.special(2, "Общественная казна", COMMUNITY_CHEST),
            Tile.street(3, "Baltic Ave", 60, BROWN, 4, 20, 60, 180, 320, 450),
            Tile.tax(4, "Подоходный налог", 200),
            Tile.railroad(5, "Reading Railroad"),
            Tile.street(6, "Oriental Ave", 100, LIGHT_BLUE, 6, 30, 90, 270, 400, 550),
            Tile.special(7, "Шанс", CHANCE),
            Tile.street(8, "Vermont Ave", 100, LIGHT_BLUE, 6, 30, 90, 270, 400, 550),
            Tile.street(9, "Connecticut Ave", 120, LIGHT_BLUE, 8, 40, 100, 300, 450, 600),
            Tile.special(10, "Тюрьма", JAIL),
            Tile.street(11, "St. Charles Place", 140, PINK, 10, 50, 150, 450, 625, 750),
            Tile.utility(12, "Электростанция"),
            Tile.street(13, "States Ave", 140, PINK, 10, 50, 150, 450, 625, 750),
            Tile.street(14, "Virginia Ave", 160, PINK, 12, 60, 180, 500, 700, 900),
            Tile.railroad(15, "Pennsylvania Railroad"),
            Tile.street(16, "St. James Place", 180, ORANGE, 14, 70, 200, 550, 750, 950),
            Tile.special(17, "Общественная казна", COMMUNITY_CHEST),
            Tile.street(18, "Tennessee Ave", 180, ORANGE, 14, 70, 200, 550, 750, 950),
            Tile.street(19, "New York Ave", 200, ORANGE, 16, 80, 220, 600, 800, 1000),
            Tile.special(20, "Бесплатная стоянка", FREE_PARKING),
            Tile.street(21, "Kentucky Ave", 220, RED, 18, 90, 250, 700, 875, 1050),
            Tile.special(22, "Шанс", CHANCE),
            Tile.street(23, "Indiana Ave", 220, RED, 18, 90, 250, 700, 875, 1050),
            Tile.street(24, "Illinois Ave", 240, RED, 20, 100, 300, 750, 925, 1100),
            Tile.railroad(25, "B&O Railroad"),
            Tile.street(26, "Atlantic Ave", 260, YELLOW, 22, 110, 330, 800, 975, 1150),
            Tile.street(27, "Ventnor Ave", 260, YELLOW, 22, 110, 330, 800, 975, 1150),
            Tile.utility(28, "Водопровод"),
            Tile.street(29, "Marvin Gardens", 280, YELLOW, 24, 120, 360, 850, 1025, 1200),
            Tile.special(30, "Отправляйтесь в тюрьму", GO_TO_JAIL),
            Tile.street(31, "Pacific Ave", 300, GREEN, 26, 130, 390, 900, 1100, 1275),
            Tile.street(32, "North Carolina Ave", 300, GREEN, 26, 130, 390, 900, 1100, 1275),
            Tile.special(33, "Общественная казна", COMMUNITY_CHEST),
            Tile.street(34, "Pennsylvania Ave", 320, GREEN, 28, 150, 450, 1000, 1200, 1400),
            Tile.railroad(35, "Short Line"),
            Tile.special(36, "Шанс", CHANCE),
            Tile.street(37, "Park Place", 350, DARK_BLUE, 35, 175, 500, 1100, 1300, 1500),
            Tile.tax(38, "Налог на роскошь", 100),
            Tile.street(39, "Boardwalk", 400, DARK_BLUE, 50, 200, 600, 1400, 1700, 2000)
    );

    public Tile tile(int index) {
        return tiles.get(index);
    }

    public List<Tile> tiles() {
        return tiles;
    }

    public List<Tile> streetsOf(ColorGroup group) {
        return tiles.stream().filter(t -> t.group() == group).toList();
    }
}
