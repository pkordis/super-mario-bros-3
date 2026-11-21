package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.LevelObjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Map;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static house.x1337.app.smb3.enumeration.TileType.PANEL_WALKABLE_TOP;
import static house.x1337.app.smb3.enumeration.TileType.SOLID;
import static house.x1337.app.smb3.enumeration.TileType.SOLID_RAMP_GENTLE_RISING_LOW;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static house.x1337.app.smb3.model.game.collision.SlopeProfile.GENTLE_RISING_LOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Building the terrain from a level scene: a ramp tile's slope comes from its type.
 */
@TestInstance(PER_CLASS)
class RampTileCollisionGridTest {
    private static final int OPAQUE = 0xFF000000;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        final LevelObjectService levelObjectService = mock(LevelObjectService.class);
        when(levelObjectService.findAllByTiles(any())).thenReturn(Map.of());
        staticBeanFactory = mockStatic(StaticBeanFactory.class, invocation -> {
            final Class<?> type = invocation.getArgument(0);
            if (type == LevelObjectService.class) {
                return levelObjectService;
            }
            if (type == PowerSwitchTimeWindow.class) {
                return new PowerSwitchTimeWindow();
            }
            if (type == StaticEnvironmentCollisionGrid.class) {
                return new StaticEnvironmentCollisionGrid(invocation.getArgument(1), invocation.getArgument(2));
            }
            return mock(type);
        });
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A ramp tile takes the slope of its type, whatever its pixels")
    void aRampTypeMakesASlope() {
        // Prepare - an opaque tile: its pixels say nothing about the slope
        final LevelScene levelScene = sceneWith(tile(1, SOLID_RAMP_GENTLE_RISING_LOW));

        // Execute
        final StaticEnvironmentCollisionGrid grid = levelScene.toCollisionGrid(mock(GameEngine.class));

        // Verify
        assertThat(grid.findSlopeAt(of(0, 0))).contains(GENTLE_RISING_LOW);
    }

    @Test
    @DisplayName("A walkable panel top is a jump-through platform: ground from above, open to heads and sides")
    void aWalkablePanelTopIsAJumpThroughPlatform() {
        // Prepare
        final LevelScene levelScene = sceneWith(tile(3, PANEL_WALKABLE_TOP));
        final GameEngine gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);

        // Execute
        final StaticEnvironmentCollisionGrid grid = levelScene.toCollisionGrid(gameEngine);

        // Verify
        assertThat(grid.getLevelObjectAt(of(0, 0)).isOneWayPlatform()).isTrue();
        assertThat(grid.isGroundSolidTile(of(0, 0))).as("Ground to whatever lands on it").isTrue();
        assertThat(grid.isWallOrCeilingSolidTile(of(0, 0))).as("Open to heads and sides").isFalse();
    }

    @Test
    @DisplayName("A solid tile is not a ramp")
    void aSolidTileIsNotARamp() {
        // Prepare
        final LevelScene levelScene = sceneWith(tile(2, SOLID));

        // Execute
        final StaticEnvironmentCollisionGrid grid = levelScene.toCollisionGrid(mock(GameEngine.class));

        // Verify
        assertThat(grid.isRampAt(of(0, 0))).as("Not a ramp").isFalse();
        assertThat(grid.getLevelObjectAt(of(0, 0)).isCollidable()).as("Solid").isTrue();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private LevelScene sceneWith(final Tile tile) {
        final Tile[][] tiles = new Tile[1][2];
        Arrays.fill(tiles[0], NULL_TILE);
        tiles[0][0] = tile;
        final LevelScene levelScene = new LevelScene();
        levelScene.setDimensions(new LevelSceneDimensions(2, 1));
        levelScene.setStaticEnvironmentLayer(new LevelScene.LevelSceneLayer(STATIC_ENVIRONMENT, true, tiles));
        return levelScene;
    }

    private Tile tile(
        final int id,
        final TileType type
    ) {
        final int[] pixels = new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE];
        Arrays.fill(pixels, OPAQUE);
        return Tile.builder().id(id).type(type).argbData(pixels).build();
    }
}
