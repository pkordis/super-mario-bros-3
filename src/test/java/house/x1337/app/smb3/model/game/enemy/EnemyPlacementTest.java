package house.x1337.app.smb3.model.game.enemy;

import house.x1337.app.smb3.model.game.PixelArea;
import house.x1337.app.smb3.model.ui.tile.Tile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.enumeration.enemy.EnemyType.PARA_GOOMBA_RED;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * An enemy placement is the pixel its stamp's rendering starter part starts on. The stamp is a 3 by 2 Para-Goomba
 * whose rendering starter is the middle of its lower row.
 */
class EnemyPlacementTest {
    private final EnemyStamp stamp = EnemyStamp
        .builder()
        .id("para-goomba")
        .enemyType(PARA_GOOMBA_RED)
        .tiles(new Tile[2][3])
        .renderingStarter(of(1, 1))
        .build();

    @Test
    @DisplayName("Placed on a cell, the rendering starter starts on the cell's top-left pixel")
    void placedOnACell() {
        // Execute
        final EnemyPlacement placement = stamp.placedAt(of(107, 22));

        // Verify
        assertThat(placement).isEqualTo(new EnemyPlacement("para-goomba", 1712, 352));
        assertThat(placement.rendererCell()).isEqualTo(of(107, 22));
        assertThat(placement.offsetInCell()).isEqualTo(of(0, 0));
    }

    @Test
    @DisplayName("A nudged placement keeps its cell while inside it, and its offset is the pixels into the cell")
    void nudgedWithinItsCell() {
        // Prepare
        final EnemyPlacement placement = stamp.placedAt(of(107, 22));

        // Execute
        final EnemyPlacement nudged = placement.movedBy(15, 7);
        final EnemyPlacement intoTheNext = placement.movedBy(16, -1);

        // Verify
        assertThat(nudged.rendererCell()).isEqualTo(of(107, 22));
        assertThat(nudged.offsetInCell()).isEqualTo(of(15, 7));
        assertThat(intoTheNext.rendererCell()).as("A pixel left of the cell above").isEqualTo(of(108, 21));
        assertThat(intoTheNext.offsetInCell()).isEqualTo(of(0, 15));
    }

    @Test
    @DisplayName("A placement left of or above the scene's origin counts its cell and offset from below and right")
    void negativePixels() {
        // Execute
        final EnemyPlacement placement = new EnemyPlacement("para-goomba", -1, -17);

        // Verify
        assertThat(placement.rendererCell()).isEqualTo(of(-1, -2));
        assertThat(placement.offsetInCell()).isEqualTo(of(15, 15));
    }

    @Test
    @DisplayName("The box covers the stamp's whole grid around the rendering starter, empty parts included")
    void boundsCoverTheWholeGrid() {
        // Prepare
        final EnemyPlacement placement = new EnemyPlacement("para-goomba", 1717, 359);

        // Execute
        final PixelArea bounds = stamp.boundsOf(placement);

        // Verify - one part to the left and one row up of the rendering starter
        assertThat(bounds).isEqualTo(new PixelArea(1701, 343, 48, 32));
    }
}
