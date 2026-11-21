package house.x1337.app.smb3.service;

import house.x1337.app.smb3.model.game.MultiTileArea;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.enemy.EnemyPlacement;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.repository.EnemyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.enumeration.TileType.ENEMY_PART;
import static house.x1337.app.smb3.enumeration.enemy.EnemyType.GOOMBA_NORMAL;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Finding the enemies placed in a layer from their parts, which is how the editor removes and protects them. */
class EnemyStampServicePlacementTest {
    private static final Coordinates RENDERER_CELL = of(2, 3);

    private final Tile topLeft = part(31);
    private final Tile topRight = part(32);
    private final Tile bottomLeft = part(33);
    private final Tile bottomRight = part(34);

    // A 2x2 enemy anchored on its bottom-left part
    private final EnemyStamp goomba = stamp("zzz-goomba", topLeft, topRight, bottomLeft, bottomRight);

    private EnemyStampService enemyStampService;
    private Tile[][] layer;

    @BeforeEach
    void prepare() {
        enemyStampService = new EnemyStampService(mock(EnemyRepository.class), mock(TileService.class));
        enemyStampService.upsert(goomba);
        layer = new Tile[6][6];
        for (final Tile[] row : layer) {
            Arrays.fill(row, NULL_TILE);
        }
    }

    @Test
    @DisplayName("Any part of a placed enemy resolves to that enemy and its rendering starter cell")
    void anyPartResolvesToTheEnemy() {
        // Prepare
        place(goomba, RENDERER_CELL);

        // Execute & Verify
        for (final Coordinates cell : goomba.placedAt(RENDERER_CELL).heldCells(layer)) {
            assertThat(enemyStampService.findPlacementHolding(layer, cell))
                .as("The part at %s", cell)
                .contains(goomba.placedAt(RENDERER_CELL));
        }
    }

    @Test
    @DisplayName("A part whose rendering starter part is gone belongs to no enemy, nor does an empty cell")
    void orphanPartBelongsToNoEnemy() {
        // Prepare
        place(goomba, RENDERER_CELL);
        layer[RENDERER_CELL.y()][RENDERER_CELL.x()] = NULL_TILE;

        // Execute & Verify
        assertThat(enemyStampService.findPlacementHolding(layer, of(3, 2))).isEmpty();
        assertThat(enemyStampService.findPlacementHolding(layer, of(0, 0))).isEmpty();
    }

    @Test
    @DisplayName("Of two enemies sharing a rendering starter part, the one with all of its parts in place wins")
    void theWholeEnemyWinsATie() {
        // Prepare - the other enemy sorts first and shares the bottom-left part, but not the others
        final EnemyStamp other = stamp("aaa-other", part(41), part(42), bottomLeft, part(44));
        enemyStampService.upsert(other);
        place(goomba, RENDERER_CELL);

        // Execute & Verify
        assertThat(enemyStampService.findPlacementAt(layer, RENDERER_CELL)).contains(goomba.placedAt(RENDERER_CELL));
        assertThat(enemyStampService.findPlacementHolding(layer, RENDERER_CELL))
            .contains(goomba.placedAt(RENDERER_CELL));
    }

    @Test
    @DisplayName("The enemies overlapping an area are found by their whole grid, empty parts included")
    void overlappingCountsTheWholeGrid() {
        // Prepare - an enemy with an empty top-right part, its renderer on column 1, row 2
        final EnemyStamp holed = stamp("holed", part(51), NULL_TILE, part(53), part(54));
        enemyStampService.upsert(holed);
        place(holed, of(1, 2));

        // Execute
        final List<EnemyPlacement> overEmptyPart =
            enemyStampService.findPlacementsOverlapping(layer, new MultiTileArea(2, 1, 1, 1));
        final List<EnemyPlacement> besideIt =
            enemyStampService.findPlacementsOverlapping(layer, new MultiTileArea(3, 0, 2, 2));

        // Verify
        assertThat(overEmptyPart).extracting(EnemyPlacement::rendererCell).containsExactly(of(1, 2));
        assertThat(besideIt).isEmpty();
    }

    private void place(final EnemyStamp stamp, final Coordinates rendererCell) {
        final MultiTileArea bounds = stamp.placedAt(rendererCell).bounds();
        for (int row = 0; row < stamp.getRows(); row++) {
            for (int column = 0; column < stamp.getColumns(); column++) {
                layer[bounds.row() + row][bounds.column() + column] = stamp.tileAt(of(column, row));
            }
        }
    }

    private EnemyStamp stamp(
        final String id,
        final Tile topLeftPart,
        final Tile topRightPart,
        final Tile bottomLeftPart,
        final Tile bottomRightPart
    ) {
        return EnemyStamp
            .builder()
            .id(id)
            .enemyType(GOOMBA_NORMAL)
            .tiles(new Tile[][] {
                {topLeftPart, topRightPart},
                {bottomLeftPart, bottomRightPart}
            })
            .renderingStarter(of(0, 1))
            .build();
    }

    private Tile part(final int id) {
        return Tile.builder().id(id).type(ENEMY_PART).build();
    }
}
