package house.x1337.app.smb3.ui.editor.level.enemy.palette;

import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Arrays;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.enemy.EnemyType.GOOMBA_NORMAL;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what the Enemies palette and the level grid show of an enemy: its parts drawn back into the picture they
 * were imported as.
 *
 * <p>No {@code TileService} in sight: an {@link EnemyStamp} already carries its parts as resolved tiles, so the
 * assembler performs no lookup. Resolving stored ids is {@code EnemyConverter}'s job.
 */
class EnemyPaletteStampingTest {
    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    private EnemyTilesAssembler assembler;

    private Tile topLeft;
    private Tile topRight;
    private Tile bottomLeft;
    private Tile bottomRight;

    @BeforeEach
    void prepare() {
        assembler = new EnemyTilesAssembler();

        topLeft = tile(10, RED);
        topRight = tile(11, BLUE);
        bottomLeft = tile(12, RED);
        bottomRight = tile(13, BLUE);
    }

    @Test
    @DisplayName("The parts are assembled into one image, each part at its own cell")
    void partsAssembleIntoOneImage() {
        // Prepare - a 2x2 enemy, red on the left column and blue on the right
        final EnemyStamp enemy = enemy(topLeft, topRight, bottomLeft, bottomRight);

        // Execute
        final BufferedImage assembled = assembler.assemble(enemy);

        // Verify
        assertThat(assembled.getWidth()).isEqualTo(2 * TILE_SPRITE_SIZE);
        assertThat(assembled.getHeight()).isEqualTo(2 * TILE_SPRITE_SIZE);
        assertThat(assembled.getRGB(0, 0)).as("top-left part").isEqualTo(RED);
        assertThat(assembled.getRGB(TILE_SPRITE_SIZE, 0)).as("top-right part").isEqualTo(BLUE);
        assertThat(assembled.getRGB(0, TILE_SPRITE_SIZE)).as("bottom-left part").isEqualTo(RED);
        assertThat(assembled.getRGB(TILE_SPRITE_SIZE, TILE_SPRITE_SIZE))
            .as("bottom-right part")
            .isEqualTo(BLUE);
    }

    @Test
    @DisplayName("A part whose tile was gone leaves a transparent hole rather than failing the assembly")
    void missingPartLeavesHole() {
        // Prepare - the top-right part could not be resolved, so the converter left NULL_TILE there
        final EnemyStamp enemy = enemy(topLeft, NULL_TILE, bottomLeft, bottomRight);

        // Execute
        final BufferedImage assembled = assembler.assemble(enemy);

        // Verify
        assertThat(assembled.getRGB(TILE_SPRITE_SIZE, 0)).as("transparent").isZero();
        assertThat(assembled.getRGB(0, 0)).isEqualTo(RED);
    }

    @Test
    @DisplayName("A malformed enemy is drawn as one blank part")
    void malformedEnemyIsTolerated() {
        // Prepare - an enemy that lost its grid, as a hand-edited or outdated document might
        final EnemyStamp broken = EnemyStamp.builder().id("broken").enemyType(GOOMBA_NORMAL).build();

        // Execute
        final BufferedImage assembled = assembler.assemble(broken);

        // Verify - an image is produced, never a zero-sized one
        assertThat(broken.isWellFormed()).isFalse();
        assertThat(assembled.getWidth()).isEqualTo(TILE_SPRITE_SIZE);
        assertThat(assembled.getHeight()).isEqualTo(TILE_SPRITE_SIZE);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** A 2x2 enemy anchored on its lower-left part, built from already-resolved tiles. */
    private static EnemyStamp enemy(
        final Tile topLeft,
        final Tile topRight,
        final Tile bottomLeft,
        final Tile bottomRight
    ) {
        return EnemyStamp
            .builder()
            .id("enemy-1")
            .enemyType(GOOMBA_NORMAL)
            .tiles(new Tile[][] {
                {topLeft, topRight},
                {bottomLeft, bottomRight}
            })
            .renderingStarter(of(0, 1))
            .build();
    }

    private static Tile tile(final int id, final int argb) {
        final int[] pixels = new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE];
        Arrays.fill(pixels, argb);
        return Tile
            .builder()
            .id(id)
            .type(TileType.ENEMY_PART)
            .argbData(pixels)
            .build();
    }

}
