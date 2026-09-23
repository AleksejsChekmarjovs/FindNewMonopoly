package com.example.monopoly.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BoardTest {

    private final Board board = new Board();

    @Test
    void hasFortyTilesWithMatchingIndexes() {
        assertThat(board.tiles()).hasSize(Board.SIZE);
        for (int i = 0; i < Board.SIZE; i++) {
            assertThat(board.tile(i).index()).isEqualTo(i);
        }
    }

    @Test
    void groupSizesMatchClassicRules() {
        assertThat(board.streetsOf(ColorGroup.BROWN)).hasSize(2);
        assertThat(board.streetsOf(ColorGroup.DARK_BLUE)).hasSize(2);
        for (ColorGroup g : new ColorGroup[]{ColorGroup.LIGHT_BLUE, ColorGroup.PINK, ColorGroup.ORANGE,
                ColorGroup.RED, ColorGroup.YELLOW, ColorGroup.GREEN}) {
            assertThat(board.streetsOf(g)).as(g.name()).hasSize(3);
        }
    }

    @Test
    void everyStreetHasSixRentValues() {
        board.tiles().stream()
                .filter(t -> t.type() == TileType.PROPERTY)
                .forEach(t -> assertThat(t.rent()).as(t.name()).hasSize(6));
    }

    @Test
    void fourRailroadsAndTwoUtilities() {
        assertThat(board.tiles()).filteredOn(t -> t.type() == TileType.RAILROAD).hasSize(4);
        assertThat(board.tiles()).filteredOn(t -> t.type() == TileType.UTILITY).hasSize(2);
    }
}
