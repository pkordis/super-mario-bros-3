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
import static house.x1337.app.smb3.enumeration.PlayerMovement.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.STILL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The level's bottom edge is no floor (SMB3-14). The ROM's player probe returns before fetching a tile
 * once it reaches the level's bottom (dasm prg008 {@code Player_GetTileAndSlope} @ PRG008_B406-PRG008_B414),
 * and so does the object probe (prg000 @ PRG000_C7F1). Only a real tile holds anything up.
 */
class CollisionGridLevelBottomTest {
    private static final double TOLERANCE = 1e-9;
    private static final int COLUMNS = 8;
    private static final int ROWS = 8;
    private static final int FLOOR_ROW = ROWS - 1;

    /** Columns 0 to 3 have a floor on the bottom row; columns 4 to 7 are a pit. */
    private static final int FIRST_PIT_COLUMN = 4;

    /** Where a grounded player's feet ({@code +$20}) are on the level's bottom edge. */
    private static final double FEET_ON_THE_LEVEL_BOTTOM_Y = ROWS * (double) TILE_SPRITE_SIZE - 32;

    /** Where a grounded player stands on the floor row. */
    private static final double STANDING_ON_THE_FLOOR_Y = FLOOR_ROW * (double) TILE_SPRITE_SIZE - 32;

    private StaticEnvironmentCollisionGrid grid;

    @BeforeEach
    void prepare() {
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));
        final GameEngine gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);

        grid = new StaticEnvironmentCollisionGrid(gameEngine, new PowerSwitchTimeWindow());
        grid.setObjects(floorWithAPit());
        grid.setUnderlayObjects(emptyCells());
        grid.setDimensions(new LevelSceneDimensions(COLUMNS, ROWS));
    }

    // -------------------------------------------------------------------------
    // The three queries
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Below the level is open to the player's probes, in every column of the level")
    void belowTheLevelIsOpenToThePlayer() {
        // Prepare - feet probe +$04,+$20 from a sprite whose feet are on the level's bottom edge
        final LevelScenePlayer player = smallPlayer(positionAt(0, FEET_ON_THE_LEVEL_BOTTOM_Y));

        // Execute & Verify
        for (int column = 0; column < COLUMNS; column++) {
            player.getPosition().setX(column * (double) TILE_SPRITE_SIZE);
            assertThat(grid.collidesAtOffset(player, Offset.of(0x04, 0x20)))
                .as("Column %d, the row below the level", column)
                .isFalse();
            assertThat(grid.collidesAtOffset(player, Offset.of(0x04, 0x20 + 3 * TILE_SPRITE_SIZE)))
                .as("Column %d, well below the level", column)
                .isFalse();
        }
    }

    @Test
    @DisplayName("Beside the level is still a wall to the player, below the level as well")
    void besideTheLevelIsStillSolidToThePlayerBelowIt() {
        // Prepare
        final LevelScenePlayer leftOfTheLevel = smallPlayer(positionAt(-16, FEET_ON_THE_LEVEL_BOTTOM_Y));
        final LevelScenePlayer rightOfTheLevel = smallPlayer(positionAt(COLUMNS * TILE_SPRITE_SIZE,
            FEET_ON_THE_LEVEL_BOTTOM_Y));

        // Execute & Verify
        assertThat(grid.collidesAtOffset(leftOfTheLevel, Offset.of(0x04, 0x20))).as("Left, below").isTrue();
        assertThat(grid.collidesAtOffset(leftOfTheLevel, Offset.of(0x04, 0x10))).as("Left, inside").isTrue();
        assertThat(grid.collidesAtOffset(rightOfTheLevel, Offset.of(0x04, 0x20))).as("Right, below").isTrue();
        assertThat(grid.collidesAtOffset(rightOfTheLevel, Offset.of(0x04, 0x10))).as("Right, inside").isTrue();
    }

    @Test
    @DisplayName("Below the level is no floor, wall or ceiling to an object, in every column of the level")
    void belowTheLevelIsOpenToAnObject() {
        // Execute & Verify
        for (int column = 0; column < COLUMNS; column++) {
            assertThat(grid.isGroundSolidTile(column, ROWS)).as("Ground, column %d", column).isFalse();
            assertThat(grid.isWallOrCeilingSolidTile(column, ROWS)).as("Wall, column %d", column).isFalse();
            assertThat(grid.isGroundSolidTile(column, ROWS + 5)).as("Ground far below, column %d", column)
                .isFalse();
        }
    }

    @Test
    @DisplayName("The floor row's real tiles are still solid to an object and to the player")
    void theFloorRowIsStillSolid() {
        // Prepare
        final LevelScenePlayer player = smallPlayer(positionAt(0, STANDING_ON_THE_FLOOR_Y));

        // Execute & Verify
        assertThat(grid.isGroundSolidTile(0, FLOOR_ROW)).as("Ground in column 0").isTrue();
        assertThat(grid.isWallOrCeilingSolidTile(FIRST_PIT_COLUMN - 1, FLOOR_ROW)).as("Wall at the pit's edge")
            .isTrue();
        assertThat(grid.isGroundSolidTile(FIRST_PIT_COLUMN, FLOOR_ROW)).as("No ground in the pit").isFalse();
        assertThat(grid.collidesAtOffset(player, Offset.of(0x04, 0x20))).as("The player's feet").isTrue();
    }

    // -------------------------------------------------------------------------
    // The player's collision pass
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A grounded player whose feet reach the level's bottom over a pit falls instead of standing on it")
    void aPlayerOverAPitFallsThroughTheBottomEdge() {
        // Prepare - Y = rows * 16 - 32 is exactly where the old invisible floor held the player up
        final PlayerPosition position = positionAt(FIRST_PIT_COLUMN * TILE_SPRITE_SIZE + 16,
            FEET_ON_THE_LEVEL_BOTTOM_Y);
        final LevelScenePlayer player = smallPlayer(position);
        player.getRuntimeState().setTo(STILL);

        // Execute
        grid.handleCollision(player, false);

        // Verify
        assertThat(player.getRuntimeState().getMovement()).as("Walked off: airborne").isEqualTo(FALLING);
        assertThat(position.getY()).as("Not snapped").isCloseTo(FEET_ON_THE_LEVEL_BOTTOM_Y, within(TOLERANCE));
    }

    @Test
    @DisplayName("A player falling past the level's bottom keeps falling")
    void aFallingPlayerIsNotStoppedAtTheBottomEdge() {
        // Prepare - 2 px into the cell below the level, falling
        final PlayerPosition position = positionAt(FIRST_PIT_COLUMN * TILE_SPRITE_SIZE + 16,
            FEET_ON_THE_LEVEL_BOTTOM_Y + 2);
        position.setDY(3.0);
        final LevelScenePlayer player = smallPlayer(position);
        player.getRuntimeState().setTo(FALLING);

        // Execute
        grid.handleCollision(player, false);

        // Verify
        assertThat(player.getRuntimeState().getMovement()).as("Still falling").isEqualTo(FALLING);
        assertThat(position.getDY()).as("Speed kept").isCloseTo(3.0, within(TOLERANCE));
        assertThat(position.getY()).as("Not lifted back up").isCloseTo(FEET_ON_THE_LEVEL_BOTTOM_Y + 2,
            within(TOLERANCE));
    }

    @Test
    @DisplayName("A player on a real floor tile in the bottom row stays standing")
    void aPlayerOnTheBottomRowStaysStanding() {
        // Prepare
        final PlayerPosition position = positionAt(16, STANDING_ON_THE_FLOOR_Y);
        final LevelScenePlayer player = smallPlayer(position);
        player.getRuntimeState().setTo(STILL);

        // Execute
        grid.handleCollision(player, false);

        // Verify
        assertThat(player.getRuntimeState().getMovement()).as("Still standing").isEqualTo(STILL);
        assertThat(position.getY()).as("On the floor").isCloseTo(STANDING_ON_THE_FLOOR_Y, within(TOLERANCE));
        assertThat(position.getDY()).isCloseTo(0.0, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private LevelObject[][] floorWithAPit() {
        final LevelObject solid = mock(LevelObject.class);
        when(solid.isCollidable()).thenReturn(true);

        final LevelObject[][] objects = emptyCells();
        Arrays.fill(objects[FLOOR_ROW], 0, FIRST_PIT_COLUMN, solid);
        return objects;
    }

    private LevelObject[][] emptyCells() {
        final LevelObject[][] objects = new LevelObject[ROWS][COLUMNS];
        for (final LevelObject[] row : objects) {
            Arrays.fill(row, EMPTY_LEVEL_OBJECT);
        }
        return objects;
    }

    private LevelScenePlayer smallPlayer(final PlayerPosition position) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(new PlayerRuntimeState());
        when(player.isLarge()).thenReturn(false);
        return player;
    }

    private PlayerPosition positionAt(final double x, final double y) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(x);
        position.setY(y);
        return position;
    }
}
