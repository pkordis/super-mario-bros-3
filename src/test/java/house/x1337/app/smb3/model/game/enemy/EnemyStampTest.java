package house.x1337.app.smb3.model.game.enemy;

import house.x1337.app.smb3.model.ui.tile.Tile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.enumeration.enemy.EnemyType.GOOMBA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the {@link EnemyStamp} domain model's behavior: the grid addressing, the anchor lookup, the derived
 * extent and the well-formedness guard. An enemy holds resolved {@link Tile}s, so none of this needs a
 * tile lookup — conversion from stored ids is {@code EnemyConverter}'s job and is tested separately.
 */
class EnemyStampTest {
    @Test
    @DisplayName("Rows and columns are the grid's own shape, not stored fields")
    void extentIsDerivedFromTheGrid() {
        final EnemyStamp enemy = enemy(2, 3, 1, 0);

        assertThat(enemy.getRows()).isEqualTo(2);
        assertThat(enemy.getColumns()).isEqualTo(3);

        // A damaged document with no grid reports no extent rather than throwing.
        final EnemyStamp empty = EnemyStamp.builder().enemyType(GOOMBA).build();
        assertThat(empty.getRows()).isZero();
        assertThat(empty.getColumns()).isZero();
    }

    @Test
    @DisplayName("Parts are addressed by row and column, and the rendering starter resolves to its own tile")
    void gridIsAddressedByRowAndColumn() {
        // Prepare - a 2-row, 3-column enemy anchored bottom-left; ids encode the cell as (row+1)*10 + column
        final EnemyStamp enemy = enemy(2, 3, 1, 0);

        // Execute & Verify
        assertThat(enemy.tileAt(0, 0).getId()).isEqualTo(10);
        assertThat(enemy.tileAt(0, 2).getId()).isEqualTo(12);
        assertThat(enemy.tileAt(1, 0).getId()).isEqualTo(20);
        assertThat(enemy.tileAt(1, 2).getId()).isEqualTo(22);
        assertThat(enemy.renderingStarterTile().getId())
            .as("The anchor is the lower-left part of this grid")
            .isEqualTo(20);
        assertThat(enemy.renderingStarterTileId())
            .as("A level placement is keyed on the anchor's tile id")
            .isEqualTo(20);
    }

    @Test
    @DisplayName("An enemy knows whether a tile is one of its parts")
    void containsItsOwnParts() {
        final EnemyStamp enemy = enemy(2, 3, 1, 0);

        assertThat(enemy.containsTile(10)).as("its first part").isTrue();
        assertThat(enemy.containsTile(22)).as("its last part").isTrue();
        assertThat(enemy.containsTile(999)).as("a tile belonging to nothing").isFalse();
    }

    @Test
    @DisplayName("A complete enemy is well formed; a damaged one is rejected rather than throwing")
    void wellFormednessGuardsDamagedDocuments() {
        assertThat(enemy(2, 3, 1, 0).isWellFormed()).isTrue();

        assertThat(EnemyStamp.builder().enemyType(GOOMBA).build().isWellFormed())
            .as("no grid at all")
            .isFalse();
        assertThat(enemy(2, 3, 2, 0).isWellFormed())
            .as("anchor row outside the grid")
            .isFalse();
        assertThat(enemy(2, 3, 1, 3).isWellFormed())
            .as("anchor column outside the grid")
            .isFalse();

        // A ragged grid, as a hand-edited document might produce.
        final EnemyStamp ragged = enemy(2, 3, 0, 0);
        ragged.getTiles()[1] = new Tile[] {tile(20)};
        assertThat(ragged.isWellFormed()).as("rows of differing length").isFalse();
    }

    @Test
    @DisplayName("An enemy is named by its description, falling back to the kind of enemy it is")
    void nameFallsBackToTheEnemyType() {
        final EnemyStamp described = enemy(1, 1, 0, 0);
        described.setDescription("Angry Goomba");
        assertThat(described.getName()).isEqualTo("Angry Goomba");

        assertThat(enemy(1, 1, 0, 0).getName())
            .as("no description: the type's label")
            .isEqualTo(GOOMBA.getLabel());
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** An enemy whose part at {@code [r][c]} is a tile with id {@code (r + 1) * 10 + c}. */
    private static EnemyStamp enemy(
        final int rows,
        final int columns,
        final int starterRow,
        final int starterColumn
    ) {
        final Tile[][] tiles = new Tile[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                tiles[row][column] = tile((row + 1) * 10 + column);
            }
        }
        return EnemyStamp
            .builder()
            .id("enemy-1")
            .enemyType(GOOMBA)
            .tiles(tiles)
            .renderingStarterRow(starterRow)
            .renderingStarterColumn(starterColumn)
            .build();
    }

    private static Tile tile(final int id) {
        return Tile.builder().id(id).build();
    }
}
