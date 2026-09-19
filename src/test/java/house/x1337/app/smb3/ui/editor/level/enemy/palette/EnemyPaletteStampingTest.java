package house.x1337.app.smb3.ui.editor.level.enemy.palette;

import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.game.enemy.Enemy;
import house.x1337.app.smb3.model.ui.tile.Tile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Arrays;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.enemy.EnemyType.GOOMBA;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what the Enemies palette shows and what a click on the level grid does: an enemy's parts are drawn
 * back into the picture they were imported as, and stamping puts the rendering-starter part on the
 * clicked cell with every other part at its stored offset.
 *
 * <p>No {@code TileService} in sight: an {@link Enemy} already carries its parts as resolved tiles, so
 * neither the assembler nor the stamper performs a lookup. Resolving stored ids is
 * {@code EnemyConverter}'s job.
 */
class EnemyPaletteStampingTest {
    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    private EnemyTilesAssembler assembler;
    private EnemyLevelSceneGridStamper stamper;

    private Tile topLeft;
    private Tile topRight;
    private Tile bottomLeft;
    private Tile bottomRight;

    @BeforeEach
    void prepare() {
        assembler = new EnemyTilesAssembler();
        stamper = new EnemyLevelSceneGridStamper();

        topLeft = tile(10, RED);
        topRight = tile(11, BLUE);
        bottomLeft = tile(12, RED);
        bottomRight = tile(13, BLUE);
    }

    @Test
    @DisplayName("The parts are assembled into one image, each part at its own cell")
    void partsAssembleIntoOneImage() {
        // Prepare - a 2x2 enemy, red on the left column and blue on the right
        final Enemy enemy = enemy(topLeft, topRight, bottomLeft, bottomRight);

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
        final Enemy enemy = enemy(topLeft, NULL_TILE, bottomLeft, bottomRight);

        // Execute
        final BufferedImage assembled = assembler.assemble(enemy);

        // Verify
        assertThat(assembled.getRGB(TILE_SPRITE_SIZE, 0)).as("transparent").isZero();
        assertThat(assembled.getRGB(0, 0)).isEqualTo(RED);
    }

    @Test
    @DisplayName("Stamping puts the rendering starter on the clicked cell and the rest around it")
    void stampAnchorsOnTheClickedCell() {
        // Prepare - a 2x2 enemy anchored bottom-left, clicked at column 1, row 3 of a 4x4 layer
        final Enemy enemy = enemy(topLeft, topRight, bottomLeft, bottomRight);
        final Tile[][] layer = emptyLayer(4, 4);

        // Execute
        final int written = stamper.stamp(layer, enemy, 1, 3);

        // Verify - the clicked cell holds the anchor part, the enemy extends up and to the right
        assertThat(written).isEqualTo(4);
        assertThat(layer[3][1]).as("clicked cell holds the rendering starter part").isEqualTo(bottomLeft);
        assertThat(layer[3][2]).isEqualTo(bottomRight);
        assertThat(layer[2][1]).isEqualTo(topLeft);
        assertThat(layer[2][2]).isEqualTo(topRight);
        assertThat(layer[0][0]).as("untouched cells stay empty").isEqualTo(NULL_TILE);
    }

    @Test
    @DisplayName("Parts falling outside the scene are clipped, not wrapped")
    void stampClipsAtTheEdges() {
        // Prepare - the same enemy clicked on the top-left cell, so its upper row falls off the scene
        final Enemy enemy = enemy(topLeft, topRight, bottomLeft, bottomRight);
        final Tile[][] layer = emptyLayer(4, 4);

        // Execute
        final int written = stamper.stamp(layer, enemy, 0, 0);

        // Verify
        assertThat(written).as("only the anchor row fits").isEqualTo(2);
        assertThat(layer[0][0]).isEqualTo(bottomLeft);
        assertThat(layer[0][1]).isEqualTo(bottomRight);
        assertThat(layer[3][0]).as("nothing wrapped to the opposite edge").isEqualTo(NULL_TILE);
    }

    @Test
    @DisplayName("A malformed enemy is drawn as one blank part and stamps nothing")
    void malformedEnemyIsTolerated() {
        // Prepare - an enemy that lost its grid, as a hand-edited or outdated document might
        final Enemy broken = Enemy.builder().id("broken").enemyType(GOOMBA).build();
        final Tile[][] layer = emptyLayer(4, 4);

        // Execute & Verify - an image is produced, never a zero-sized one, and nothing is written
        final BufferedImage assembled = assembler.assemble(broken);
        assertThat(broken.isWellFormed()).isFalse();
        assertThat(assembled.getWidth()).isEqualTo(TILE_SPRITE_SIZE);
        assertThat(assembled.getHeight()).isEqualTo(TILE_SPRITE_SIZE);
        assertThat(stamper.stamp(layer, broken, 1, 1)).isZero();
        assertThat(layer[1][1]).isEqualTo(NULL_TILE);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** A 2x2 enemy anchored on its lower-left part, built from already-resolved tiles. */
    private static Enemy enemy(
        final Tile topLeft,
        final Tile topRight,
        final Tile bottomLeft,
        final Tile bottomRight
    ) {
        return Enemy
            .builder()
            .id("enemy-1")
            .enemyType(GOOMBA)
            .tiles(new Tile[][] {
                {topLeft, topRight},
                {bottomLeft, bottomRight}
            })
            .renderingStarterRow(1)
            .renderingStarterColumn(0)
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

    private static Tile[][] emptyLayer(final int rows, final int columns) {
        final Tile[][] tiles = new Tile[rows][columns];
        for (final Tile[] row : tiles) {
            Arrays.fill(row, NULL_TILE);
        }
        return tiles;
    }
}
