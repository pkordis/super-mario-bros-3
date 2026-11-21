package house.x1337.app.smb3.model.game.enemy;

import house.x1337.app.smb3.model.game.MultiTileArea;
import house.x1337.app.smb3.model.ui.tile.Tile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.enumeration.TileType.ENEMY_PART;
import static house.x1337.app.smb3.enumeration.enemy.EnemyType.PARA_GOOMBA_NORMAL;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * An enemy placed in a layer: the grid it covers around its rendering starter cell, and the cells that still hold
 * its parts.
 */
class EnemyPlacementTest {
    private final Tile leftWing = part(21);
    private final Tile rightWing = part(22);
    private final Tile body = part(23);
    private final Tile ground = Tile.builder().id(5).build();

    // A 3x2 enemy: two wings over a body in the middle of the bottom row, which is its rendering starter
    private final EnemyStamp stamp = EnemyStamp
        .builder()
        .enemyType(PARA_GOOMBA_NORMAL)
        .tiles(new Tile[][] {
            {leftWing, NULL_TILE, rightWing},
            {NULL_TILE, body, null}
        })
        .renderingStarter(of(1, 1))
        .build();

    @Test
    @DisplayName("The bounds are the stamp's whole grid around the rendering starter cell")
    void boundsSurroundTheRendererCell() {
        // Execute
        final MultiTileArea bounds = stamp.placedAt(of(4, 3)).bounds();

        // Verify
        assertThat(bounds).isEqualTo(new MultiTileArea(3, 2, 3, 2));
    }

    @Test
    @DisplayName("The held cells are the cells still holding a part, empty parts left out")
    void heldCellsAreThePartsInPlace() {
        // Prepare
        final Tile[][] layer = layerWithStampAt(2, 1);

        // Execute & Verify
        assertThat(stamp.placedAt(of(2, 1)).heldCells(layer)).containsExactly(of(1, 0), of(3, 0), of(2, 1));
    }

    @Test
    @DisplayName("A part painted over or off the layer is not held")
    void paintedOverOrOffTheLayerIsNotHeld() {
        // Prepare - the stamp at the left edge, its left wing off the layer, and its right wing painted over
        final Tile[][] layer = emptyLayer();
        layer[0][1] = ground;
        layer[1][0] = body;

        // Execute & Verify
        assertThat(stamp.placedAt(of(0, 1)).heldCells(layer)).containsExactly(of(0, 1));
    }

    @Test
    @DisplayName("A placement is whole while every part inside the layer is in place")
    void wholeWhileEveryPartInsideTheLayerIsInPlace() {
        // Prepare
        final Tile[][] layer = layerWithStampAt(2, 1);
        final Tile[][] clipped = emptyLayer();
        clipped[0][1] = rightWing;
        clipped[1][0] = body;

        // Execute & Verify
        assertThat(stamp.placedAt(of(2, 1)).isWhole(layer)).isTrue();
        assertThat(stamp.placedAt(of(0, 1)).isWhole(clipped)).as("A part off the layer does not count").isTrue();
        layer[0][3] = ground;
        assertThat(stamp.placedAt(of(2, 1)).isWhole(layer)).as("A wing painted over").isFalse();
    }

    private Tile[][] layerWithStampAt(final int column, final int row) {
        final Tile[][] layer = emptyLayer();
        layer[row - 1][column - 1] = leftWing;
        layer[row - 1][column + 1] = rightWing;
        layer[row][column] = body;
        return layer;
    }

    private Tile[][] emptyLayer() {
        final Tile[][] layer = new Tile[4][6];
        for (final Tile[] row : layer) {
            Arrays.fill(row, NULL_TILE);
        }
        return layer;
    }

    private Tile part(final int id) {
        return Tile.builder().id(id).type(ENEMY_PART).build();
    }
}
