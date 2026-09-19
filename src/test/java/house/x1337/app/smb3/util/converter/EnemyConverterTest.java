package house.x1337.app.smb3.util.converter;

import house.x1337.app.smb3.model.game.enemy.Enemy;
import house.x1337.app.smb3.model.repository.EnemyRecord;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.util.provider.TilesProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.enumeration.enemy.EnemyType.GOOMBA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the enemy's domain/persistence mapping: an {@link Enemy} carries resolved {@link Tile}s, an
 * {@link EnemyRecord} carries a flat row-major {@code int[]} of ids plus the grid's dimensions, and the
 * two convert both ways through a {@link TilesProvider} — the same asymmetry
 * {@code LevelSceneLayerConverter} handles for a level's layers.
 */
class EnemyConverterTest {
    private Map<Integer, Tile> tilesById;
    private EnemyConverter converter;

    @BeforeEach
    void prepare() {
        tilesById = new HashMap<>();
        // Ids deliberately start above 0: NULL_TILE's own id is 0, and the padding path looks tiles up by
        // that id, so a stub registered under 0 would masquerade as the fill tile.
        for (final int id : new int[] {1, 2, 3, 11, 12, 13}) {
            tilesById.put(id, Tile.builder().id(id).build());
        }
        // The converter only needs a tiles provider; EnemyService supplies TileService in production.
        converter = () -> tileId -> Optional.ofNullable(tilesById.get(tileId));
    }

    @Test
    @DisplayName("Stored ids are resolved row-major into the enemy's grid of tiles")
    void resolvesStoredIdsIntoTheGrid() {
        // Prepare - a 2x3 grid stored flat
        final EnemyRecord record = record(2, 3, new int[] {1, 2, 3, 11, 12, 13}, 1, 0);

        // Execute
        final Enemy enemy = converter.toEnemy(record);

        // Verify - shape comes from rows/columns, and each cell is the resolved tile
        assertThat(enemy.getRows()).isEqualTo(2);
        assertThat(enemy.getColumns()).isEqualTo(3);
        assertThat(enemy.tileAt(0, 0)).isSameAs(tilesById.get(1));
        assertThat(enemy.tileAt(0, 2)).isSameAs(tilesById.get(3));
        assertThat(enemy.tileAt(1, 2)).isSameAs(tilesById.get(13));
        assertThat(enemy.renderingStarterTileId()).isEqualTo(11);
        assertThat(enemy.isWellFormed()).isTrue();
    }

    @Test
    @DisplayName("A part whose tile is gone becomes NULL_TILE, so the grid never has gaps")
    void unknownIdsBecomeNullTile() {
        // Prepare - id 999 is not in the tiles collection
        final EnemyRecord record = record(1, 2, new int[] {1, 999}, 0, 0);

        // Execute
        final Enemy enemy = converter.toEnemy(record);

        // Verify
        assertThat(enemy.tileAt(0, 0)).isSameAs(tilesById.get(1));
        assertThat(enemy.tileAt(0, 1)).as("missing part is filled, not null").isSameAs(NULL_TILE);
    }

    @Test
    @DisplayName("A record with fewer ids than cells is still resolved into a full grid")
    void shortIdRunIsPadded() {
        final Enemy enemy = converter.toEnemy(record(2, 2, new int[] {1, 2}, 0, 0));

        assertThat(enemy.getRows()).isEqualTo(2);
        assertThat(enemy.getColumns()).isEqualTo(2);
        assertThat(enemy.tileAt(1, 0)).isSameAs(NULL_TILE);
        assertThat(enemy.tileAt(1, 1)).isSameAs(NULL_TILE);
    }

    @Test
    @DisplayName("Converting a record up and back down preserves every field")
    void roundTripsFromRecord() {
        // Prepare
        final EnemyRecord original = record(2, 3, new int[] {1, 2, 3, 11, 12, 13}, 1, 0);
        original.setDescription("Angry Goomba");
        original.setUpdatedAt(1234L);

        // Execute
        final EnemyRecord restored = converter.toEnemyRecord(converter.toEnemy(original));

        // Verify - rows/columns are re-derived from the grid, and the flat run is rebuilt in order
        assertThat(restored).isEqualTo(original);
        assertThat(restored.getTileIds()).containsExactly(1, 2, 3, 11, 12, 13);
        assertThat(restored.getRows()).isEqualTo(2);
        assertThat(restored.getColumns()).isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private static EnemyRecord record(
        final int rows,
        final int columns,
        final int[] tileIds,
        final int starterRow,
        final int starterColumn
    ) {
        return EnemyRecord
            .builder()
            .id("enemy-1")
            .enemyType(GOOMBA)
            .rows(rows)
            .columns(columns)
            .tileIds(tileIds)
            .renderingStarterRow(starterRow)
            .renderingStarterColumn(starterColumn)
            .build();
    }
}
