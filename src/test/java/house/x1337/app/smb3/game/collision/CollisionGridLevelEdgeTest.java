package house.x1337.app.smb3.game.collision;

import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the level's horizontal edges are to the two kinds of things that probe the terrain.
 *
 * <p>To an object they are nothing: the ROM's object tile probe reads tile {@code $00} for any X outside
 * the level (dasm prg000 {@code Object_DetectTile} @ PRG000_C832), so a walking enemy carries on past the
 * edge rather than turning at it. To the player they are a wall, which the ROM enforces with the screen
 * clamp in {@code Player_DoScrolling} (prg008 @ PRG008_B1CE) and this port with
 * {@code collidesAtOffset}.
 */
class CollisionGridLevelEdgeTest {
    private static final double TOLERANCE = 1e-9;
    private static final int COLUMNS = 8;
    private static final int ROWS = 8;
    private static final int LEVEL_WIDTH_PIXELS = COLUMNS * TILE_SPRITE_SIZE;

    private StaticEnvironmentCollisionGrid grid;

    @BeforeEach
    void prepare() {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));
        final GameEngine gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);

        grid = new StaticEnvironmentCollisionGrid(gameEngine, new PowerSwitchTimeWindow());
        grid.setObjects(solidFloorOnLastRow());
        grid.setUnderlayObjects(emptyCells());
        grid.setDimensions(new LevelSceneDimensions(COLUMNS, ROWS));
    }

    // -------------------------------------------------------------------------
    // Objects: the sides are open
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Beside the level is no wall to an object, on either side and at any height")
    void besideTheLevelIsOpenToAnObject() {
        // Execute & Verify
        for (int row = 0; row < ROWS; row++) {
            assertThat(grid.isWallOrCeilingSolidTile(-1, row))
                .as("Left of column 0, row %d", row)
                .isFalse();
            assertThat(grid.isWallOrCeilingSolidTile(COLUMNS, row))
                .as("Right of the last column, row %d", row)
                .isFalse();
        }
    }

    @Test
    @DisplayName("Beside the level is no ground to an object either, even level with the floor or below it")
    void besideTheLevelHasNoGround() {
        // Execute & Verify
        assertThat(grid.isGroundSolidTile(-1, ROWS - 1)).as("Left, level with the floor row").isFalse();
        assertThat(grid.isGroundSolidTile(COLUMNS, ROWS - 1)).as("Right, level with the floor row").isFalse();
        assertThat(grid.isGroundSolidTile(-1, ROWS)).as("Left, below the world").isFalse();
        assertThat(grid.isGroundSolidTile(COLUMNS, ROWS)).as("Right, below the world").isFalse();
    }

    @Test
    @DisplayName("Within the level's columns, below the level is open, like the sky above it")
    void aboveAndBelowTheLevelAreOpen() {
        // Execute & Verify - the bottom edge opened in SMB3-14 (CollisionGridLevelBottomTest)
        assertThat(grid.isGroundSolidTile(0, ROWS)).as("Below the level is no floor").isFalse();
        assertThat(grid.isWallOrCeilingSolidTile(COLUMNS - 1, ROWS)).as("Nor a wall").isFalse();
        assertThat(grid.isGroundSolidTile(0, -1)).as("Above the world is open").isFalse();
        assertThat(grid.isWallOrCeilingSolidTile(COLUMNS - 1, -1)).as("For a head as well").isFalse();
    }

    @Test
    @DisplayName("Terrain in the outermost columns is still solid to an object")
    void terrainInTheOutermostColumnsIsSolid() {
        // Execute & Verify
        assertThat(grid.isGroundSolidTile(0, ROWS - 1)).as("Floor in column 0").isTrue();
        assertThat(grid.isGroundSolidTile(COLUMNS - 1, ROWS - 1)).as("Floor in the last column").isTrue();
        assertThat(grid.isWallOrCeilingSolidTile(0, ROWS - 1)).as("Wall in column 0").isTrue();
    }

    // -------------------------------------------------------------------------
    // The player: the sides are still a wall
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Beside the level is still solid to the player's probes, on either side")
    void besideTheLevelIsSolidToThePlayer() {
        // Prepare
        final LevelScenePlayer atTheLeftEdge = largePlayerMovingUp(positionAt(-2, 32, 0));
        final LevelScenePlayer atTheRightEdge = largePlayerMovingUp(positionAt(LEVEL_WIDTH_PIXELS - 14, 32, 0));

        // Execute & Verify - the in-front probes at X + $01 and X + $0E, mid-body
        assertThat(grid.collidesAtOffset(atTheLeftEdge, Offset.of(0x01, 0x0E)))
            .as("X + $01 = -1 lies in column -1")
            .isTrue();
        assertThat(grid.collidesAtOffset(atTheRightEdge, Offset.of(0x0E, 0x0E)))
            .as("X + $0E = the level width lies in the column past the last")
            .isTrue();
    }

    @Test
    @DisplayName("A player pushing past the left edge is nudged back in and has its velocity halted")
    void playerIsHeldAtTheLeftEdge() {
        // Prepare - X = -3 is the right half of its tile, so the in-front probes look LEFT (X + $01 = -2,
        // column -1); the left body edge X + 2 = -1 is a pixel short of alignment
        final PlayerPosition position = positionAt(-3, 32, -1.5);
        final LevelScenePlayer player = largePlayerMovingUp(position);

        // Execute
        final boolean hitWall = grid.handleCollision(player, false);

        // Verify
        assertThat(hitWall).as("The level's left edge registers as a wall").isTrue();
        assertThat(position.getX()).as("Nudged 1 px back towards the level").isCloseTo(-2.0, within(TOLERANCE));
        assertThat(position.getDX()).as("Velocity into the edge is halted").isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("A player pushing past the right edge is nudged back in and has its velocity halted")
    void playerIsHeldAtTheRightEdge() {
        // Prepare - X mod 16 = 3 is the left half, so the in-front probes look RIGHT (X + $0E = width + 1,
        // the column past the last); the right body edge X + 14 is a pixel past alignment
        final PlayerPosition position = positionAt(LEVEL_WIDTH_PIXELS - 13, 32, 1.5);
        final LevelScenePlayer player = largePlayerMovingUp(position);

        // Execute
        final boolean hitWall = grid.handleCollision(player, false);

        // Verify
        assertThat(hitWall).as("The level's right edge registers as a wall").isTrue();
        assertThat(position.getX())
            .as("Nudged 1 px back towards the level")
            .isCloseTo(LEVEL_WIDTH_PIXELS - 14.0, within(TOLERANCE));
        assertThat(position.getDX()).as("Velocity into the edge is halted").isCloseTo(0.0, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private LevelObject[][] solidFloorOnLastRow() {
        final LevelObject solid = mock(LevelObject.class);
        when(solid.isCollidable()).thenReturn(true);

        final LevelObject[][] objects = emptyCells();
        Arrays.fill(objects[ROWS - 1], solid);
        return objects;
    }

    private LevelObject[][] emptyCells() {
        final LevelObject[][] objects = new LevelObject[ROWS][COLUMNS];
        for (final LevelObject[] row : objects) {
            Arrays.fill(row, EMPTY_LEVEL_OBJECT);
        }
        return objects;
    }

    private LevelScenePlayer largePlayerMovingUp(final PlayerPosition position) {
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setTo(JUMPING); // airborne so the "moving up" branch is taken, clear of the floor

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.isLarge()).thenReturn(true);
        return player;
    }

    private PlayerPosition positionAt(final double x, final double y, final double dx) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(x);
        position.setY(y);
        position.setDX(dx);
        position.setDY(-2);
        return position;
    }
}
