package house.x1337.app.smb3.game.level.scene;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.game.camera.LevelSceneVibration;
import house.x1337.app.smb3.game.level.scene.LevelSceneCapabilities.ConsolidatedLayers;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.ui.tile.Tile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.DECORATIONS_LAND;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * What a cell falls back to when its tile is retired at runtime — a brick broken, a coin collected.
 *
 * <p>The fallback has to be whatever sits below that cell's <em>own</em> winning layer. Deriving it from a
 * fixed layer instead is what made a brick painted as terrain indestructible: the brick appeared in both
 * the surface and the fallback view, so breaking it re-exposed itself and it paid out fragments on every
 * hit, forever.
 */
class LevelSceneConsolidationTest {
    private static final int ROWS = 4;
    private static final int COLUMNS = 4;
    private static final int ROW = 2;
    private static final int COLUMN = 1;

    private static final int SKY_TILE_ID = 11;
    private static final int DECORATION_TILE_ID = 22;
    private static final int TERRAIN_BRICK_TILE_ID = 33;
    private static final int INTERACTIVE_BRICK_TILE_ID = 44;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        // LevelScene resolves its vibration collaborator in a field initialiser.
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(LevelSceneVibration.class))
            .thenReturn(mock(LevelSceneVibration.class));
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A brick painted as terrain falls back to the sky behind it, not to another brick")
    void terrainBrickFallsBackToWhatIsBehindIt() {
        // Prepare - the reported case: the brick lives in the static environment layer over plain sky
        final LevelScene levelScene = sceneWith(
            layer(AIR, SKY_TILE_ID),
            layer(STATIC_ENVIRONMENT, TERRAIN_BRICK_TILE_ID)
        );

        // Execute
        final ConsolidatedLayers consolidated = levelScene.consolidateBelow(NON_PLAYABLE_CHARACTERS);

        // Verify
        assertThat(consolidated.surface()[ROW][COLUMN].getId()).isEqualTo(TERRAIN_BRICK_TILE_ID);
        assertThat(consolidated.underlay()[ROW][COLUMN].getId())
            .as("the brick must not be its own fallback")
            .isEqualTo(SKY_TILE_ID);
        assertThat(consolidated.surfaceLayers()[ROW][COLUMN]).isEqualTo(STATIC_ENVIRONMENT);
        assertThat(consolidated.underlayLayers()[ROW][COLUMN]).isEqualTo(AIR);

        // Verify - and this is what the fallback used to be derived from, which is why the brick survived
        // being broken: anchored at the interactive layer, the view below it still contains the brick.
        assertThat(levelScene.getTilesOfLayersBelow(INTERACTIVE_OBJECTS)[ROW][COLUMN].getId())
            .as("the superseded fixed-layer fallback returned the brick itself")
            .isEqualTo(TERRAIN_BRICK_TILE_ID);
    }

    @Test
    @DisplayName("An interactive brick still exposes the walkable decoration beneath it")
    void interactiveBrickExposesTheDecorationBeneath() {
        // Prepare - the case the old fixed-layer fallback was written for, which must keep working
        final LevelScene levelScene = sceneWith(
            layer(DECORATIONS_LAND, DECORATION_TILE_ID),
            layer(INTERACTIVE_OBJECTS, INTERACTIVE_BRICK_TILE_ID)
        );

        // Execute
        final ConsolidatedLayers consolidated = levelScene.consolidateBelow(NON_PLAYABLE_CHARACTERS);

        // Verify
        assertThat(consolidated.surface()[ROW][COLUMN].getId()).isEqualTo(INTERACTIVE_BRICK_TILE_ID);
        assertThat(consolidated.underlay()[ROW][COLUMN].getId()).isEqualTo(DECORATION_TILE_ID);
        assertThat(consolidated.surfaceLayers()[ROW][COLUMN]).isEqualTo(INTERACTIVE_OBJECTS);
        assertThat(consolidated.underlayLayers()[ROW][COLUMN]).isEqualTo(DECORATIONS_LAND);
    }

    @Test
    @DisplayName("Only the tile directly beneath is kept, not the whole stack")
    void onlyTheTileDirectlyBeneathIsKept() {
        // Prepare - three layers deep at the same cell
        final LevelScene levelScene = sceneWith(
            layer(AIR, SKY_TILE_ID),
            layer(DECORATIONS_LAND, DECORATION_TILE_ID),
            layer(INTERACTIVE_OBJECTS, INTERACTIVE_BRICK_TILE_ID)
        );

        // Execute
        final ConsolidatedLayers consolidated = levelScene.consolidateBelow(NON_PLAYABLE_CHARACTERS);

        // Verify
        assertThat(consolidated.surface()[ROW][COLUMN].getId()).isEqualTo(INTERACTIVE_BRICK_TILE_ID);
        assertThat(consolidated.underlay()[ROW][COLUMN].getId()).isEqualTo(DECORATION_TILE_ID);
    }

    @Test
    @DisplayName("A lone tile has no fallback at all")
    void aLoneTileHasNoFallback() {
        // Prepare
        final LevelScene levelScene = sceneWith(layer(STATIC_ENVIRONMENT, TERRAIN_BRICK_TILE_ID));

        // Execute
        final ConsolidatedLayers consolidated = levelScene.consolidateBelow(NON_PLAYABLE_CHARACTERS);

        // Verify
        assertThat(consolidated.underlay()[ROW][COLUMN]).isSameAs(NULL_TILE);
        assertThat(consolidated.underlayLayers()[ROW][COLUMN]).isNull();
    }

    @Test
    @DisplayName("Layers at or above the bound take no part, so a character cannot hide a block")
    void layersAtOrAboveTheBoundAreExcluded() {
        // Prepare - an NPC painted over the brick's cell
        final LevelScene levelScene = sceneWith(
            layer(STATIC_ENVIRONMENT, TERRAIN_BRICK_TILE_ID),
            layer(NON_PLAYABLE_CHARACTERS, INTERACTIVE_BRICK_TILE_ID)
        );

        // Execute
        final ConsolidatedLayers consolidated = levelScene.consolidateBelow(NON_PLAYABLE_CHARACTERS);

        // Verify - the brick still owns the cell
        assertThat(consolidated.surface()[ROW][COLUMN].getId()).isEqualTo(TERRAIN_BRICK_TILE_ID);
        assertThat(consolidated.surfaceLayers()[ROW][COLUMN]).isEqualTo(STATIC_ENVIRONMENT);
    }

    @Test
    @DisplayName("The surface view is unchanged from the plain consolidation it replaces")
    void surfaceMatchesTheLegacyConsolidation() {
        // Prepare
        final LevelScene levelScene = sceneWith(
            layer(AIR, SKY_TILE_ID),
            layer(STATIC_ENVIRONMENT, TERRAIN_BRICK_TILE_ID),
            layer(INTERACTIVE_OBJECTS, INTERACTIVE_BRICK_TILE_ID)
        );

        // Execute
        final Tile[][] legacySurface = levelScene.getTilesOfLayersBelow(NON_PLAYABLE_CHARACTERS);
        final ConsolidatedLayers consolidated = levelScene.consolidateBelow(NON_PLAYABLE_CHARACTERS);

        // Verify
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                assertThat(consolidated.surface()[row][column])
                    .as("cell [%d][%d]", row, column)
                    .isSameAs(legacySurface[row][column]);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** A layer holding one renderable tile at the cell under test, empty everywhere else. */
    private static LevelScene.LevelSceneLayer layer(final LevelSceneLayerType type, final int tileId) {
        final Tile[][] tiles = new Tile[ROWS][COLUMNS];
        for (final Tile[] row : tiles) {
            Arrays.fill(row, NULL_TILE);
        }
        tiles[ROW][COLUMN] = renderableTile(tileId);
        return LevelScene.LevelSceneLayer
            .builder()
            .type(type)
            .visible(true)
            .tiles(tiles)
            .build();
    }

    private static Tile renderableTile(final int tileId) {
        return Tile
            .builder()
            .id(tileId)
            .type(TileType.SOLID)
            .argbData(new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE])
            .build();
    }

    private static LevelScene sceneWith(final LevelScene.LevelSceneLayer... layers) {
        final LevelScene.LevelSceneBuilder builder = LevelScene
            .builder()
            .dimensions(new LevelSceneDimensions(COLUMNS, ROWS));
        for (final LevelScene.LevelSceneLayer layer : layers) {
            switch (layer.getType()) {
                case AIR -> builder.airLayer(layer);
                case DECORATIONS_AIR -> builder.airDecorationsLayer(layer);
                case DECORATIONS_LAND -> builder.landDecorationsLayer(layer);
                case STATIC_ENVIRONMENT -> builder.staticEnvironmentLayer(layer);
                case INTERACTIVE_OBJECTS -> builder.interactiveObjectsLayer(layer);
                case NON_PLAYABLE_CHARACTERS -> builder.nonPlayableCharactersLayer(layer);
            }
        }
        return builder.build();
    }
}
