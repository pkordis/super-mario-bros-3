package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.event.GameEventBus;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;

import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static house.x1337.app.smb3.GameConstants.PLAYER_END_LEVEL_XVEL;
import static house.x1337.app.smb3.GameConstants.PMETER_LEVELS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerDeathKind.FELL_OFF;
import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.LEFT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_DOWN;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_JUMP;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_LEFT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RIGHT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RUN;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The end-level run-off, {@code Player_EndLevel}: the pad reads as released ({@code Player_Control}, dasm
 * prg008.asm:1329-1349), the player faces right and walks at {@code Player_XVel = $14} on the ground and 0 in the
 * air ({@code PRG029_D257}, prg029.asm:1099-1118), and gravity, collision and the pit check go on. A real player is
 * ticked over a real collision grid, with a real input handler whose keys are pressed.
 */
class PlayerRunOffTest {
    private static final double TOLERANCE = 1e-9;
    private static final double WALK = PLAYER_END_LEVEL_XVEL;
    private static final int COLUMNS = 16;
    private static final int ROWS = 8;
    private static final int FLOOR_ROW = ROWS - 1;
    private static final double LEVEL_WIDTH_PIXELS = COLUMNS * (double) TILE_SPRITE_SIZE;
    private static final double LEVEL_BOTTOM_PIXELS = ROWS * (double) TILE_SPRITE_SIZE;
    private static final double STANDING_Y = FLOOR_ROW * (double) TILE_SPRITE_SIZE - 32;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private GameEngine gameEngine;
    private StaticEnvironmentCollisionGrid grid;

    @BeforeEach
    void prepare() {
        final GameEventBus bus = new GameEventBus();
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getSpawnPoint()).thenReturn(of(0, 0));
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

        final CameraState cameraState = mock(CameraState.class);
        when(cameraState.getPendingRows()).thenReturn(ROWS);
        when(cameraState.getActiveObjectRegion(anyInt()))
            .thenReturn(new AxisAlignedBoundingBox(0, LEVEL_BOTTOM_PIXELS - 192, 256, LEVEL_BOTTOM_PIXELS));
        doCallRealMethod().when(cameraState).isBelowVisibleBottom(any());

        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCameraState()).thenReturn(cameraState);
        doAnswer(invocation -> {
            bus.publish(invocation.getArgument(0));
            return null;
        }).when(gameEngine).publish(any());

        grid = new StaticEnvironmentCollisionGrid(gameEngine, new PowerSwitchTimeWindow());
        grid.setObjects(floorUpTo(COLUMNS));
        grid.setUnderlayObjects(emptyCells());
        grid.setDimensions(new LevelSceneDimensions(COLUMNS, ROWS));
        when(gameEngine.getCollisionGrid()).thenReturn(grid);

        // A fresh state and a real input handler; inert stand-ins for scroll and art
        staticBeanFactory = mockStatic(StaticBeanFactory.class, invocation -> {
            final Class<?> type = invocation.getArgument(0);
            if (type == PlayerRuntimeState.class) {
                return new PlayerRuntimeState();
            }
            if (type == PlayerInputHandler.class) {
                return new PlayerInputHandler(invocation.<GameEngine>getArgument(1));
            }
            return mock(type);
        });
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Held and pressed keys do nothing: the player faces right and walks 1.25 px per tick")
    void heldAndPressedKeysDoNothing() {
        // Prepare - facing left, holding left, down and run, with a fresh jump press
        final LevelScenePlayer player = playerAt(NORMAL, 3 * TILE_SPRITE_SIZE);
        player.getOrientation().setHorizontal(LEFT);
        final PlayerInputHandler input = player.getInputHandler();
        for (final String key : new String[] {HANDLER_LEFT, HANDLER_DOWN, HANDLER_RUN, HANDLER_JUMP}) {
            input.onAction(key, true, 0f);
        }
        assertThat(player.isCarryButtonHeld()).as("B is held before the touch").isTrue();

        // Execute
        player.startRunningOff();

        // Verify
        assertThat(player.getOrientation().getHorizontal()).as("Faces right at once").isEqualTo(RIGHT);
        assertThat(player.isCarryButtonHeld()).as("B no longer reads as held, so a carry is let go").isFalse();
        final PlayerPosition position = player.getPosition();
        double x = position.getX();
        for (int tick = 1; tick <= 40; tick++) {
            player.updateFrame();
            assertThat(position.getX() - x).as("Tick %d: the walk", tick).isCloseTo(WALK, within(TOLERANCE));
            assertThat(position.getY()).as("Tick %d: no jump", tick).isCloseTo(STANDING_Y, within(TOLERANCE));
            assertThat(player.getRuntimeState().isDucking()).as("Tick %d: no duck", tick).isFalse();
            assertThat(player.getOrientation().getHorizontal()).as("Tick %d: facing", tick).isEqualTo(RIGHT);
            x = position.getX();
        }
    }

    @Test
    @DisplayName("Touched in the air, the player drops straight down, then walks once it lands")
    void inTheAirThePlayerDropsStraightDown() {
        // Prepare - 24 px above the floor, falling and moving right fast
        final LevelScenePlayer player = playerAt(SHRUNK, 3 * TILE_SPRITE_SIZE);
        final PlayerPosition position = player.getPosition();
        position.setY(STANDING_Y - 24);
        position.setDX(2.5);
        player.getRuntimeState().setTo(FALLING);
        final double touchX = position.getX();

        // Execute
        player.startRunningOff();
        int tick = 0;
        while (player.getRuntimeState().isInAir() && tick < 120) {
            player.updateFrame();
            tick++;
            assertThat(position.getX()).as("Tick %d in the air: straight down", tick)
                .isCloseTo(touchX, within(TOLERANCE));
        }

        // Verify
        assertThat(player.getRuntimeState().isInAir()).as("Landed").isFalse();
        player.updateFrame();
        player.updateFrame();
        assertThat(position.getX()).as("Walking after the landing").isCloseTo(touchX + WALK, within(TOLERANCE));
    }

    @Test
    @DisplayName("The player walks out past the level's right edge at its height, without stopping or falling")
    void thePlayerWalksOutPastTheRightEdge() {
        // Prepare
        final LevelScenePlayer player = playerAt(NORMAL, (COLUMNS - 3) * TILE_SPRITE_SIZE);
        final PlayerPosition position = player.getPosition();

        // Execute & Verify
        player.startRunningOff();
        double x = position.getX();
        for (int tick = 1; tick <= 80; tick++) {
            player.updateFrame();
            assertThat(position.getX() - x).as("Tick %d: no stop", tick).isCloseTo(WALK, within(TOLERANCE));
            assertThat(position.getY()).as("Tick %d: no fall", tick).isCloseTo(STANDING_Y, within(TOLERANCE));
            assertThat(player.getRuntimeState().isInAir()).as("Tick %d: walking", tick).isFalse();
            x = position.getX();
        }
        assertThat(position.getX()).as("Out of the level").isGreaterThan(LEVEL_WIDTH_PIXELS);
        assertThat(player.getRuntimeState().isDying()).isFalse();
    }

    @Test
    @DisplayName("Outside the run-off the level's right edge still stops the player")
    void theRightEdgeIsStillSolidOutsideTheRunOff() {
        // Prepare
        final LevelScenePlayer player = playerAt(NORMAL, (COLUMNS - 3) * TILE_SPRITE_SIZE);
        player.getInputHandler().onAction(HANDLER_RIGHT, true, 0f);

        // Execute
        for (int tick = 0; tick < 120; tick++) {
            player.updateFrame();
        }

        // Verify - the in-front probe at X + 14 stays within a pixel of the edge (the wall nudge is a pixel a tick)
        assertThat(player.getPosition().getX() + 14).isLessThan(LEVEL_WIDTH_PIXELS + 1);
    }

    @Test
    @DisplayName("A pit on the way still kills, as in the ROM")
    void aPitStillKills() {
        // Prepare - floor only under the first four columns
        grid.setObjects(floorUpTo(4));
        final LevelScenePlayer player = playerAt(SHRUNK, TILE_SPRITE_SIZE);

        // Execute
        player.startRunningOff();
        for (int tick = 0; tick < 300 && !player.getRuntimeState().isDying(); tick++) {
            player.updateFrame();
        }

        // Verify
        assertThat(player.getRuntimeState().isDying()).isTrue();
        assertThat(player.getRuntimeState().getDeathKind()).isEqualTo(FELL_OFF);
    }

    @Test
    @DisplayName("The P-meter drains, though B is held")
    void thePowerMeterDrains() {
        // Prepare
        final LevelScenePlayer player = playerAt(NORMAL, TILE_SPRITE_SIZE);
        player.getPlayerData().setPlayerPower(PMETER_LEVELS);
        player.getInputHandler().onAction(HANDLER_RUN, true, 0f);

        // Execute
        player.startRunningOff();
        for (int tick = 0; tick < 100; tick++) {
            player.updateFrame();
        }

        // Verify
        assertThat(player.getPlayerData().getPlayerPower()).isLessThan(PMETER_LEVELS);
    }

    @Test
    @DisplayName("Once the run-off is halted at the exit the player stands where it stopped")
    void aHaltedRunOffStandsStill() {
        // Prepare
        final LevelScenePlayer player = playerAt(NORMAL, 3 * TILE_SPRITE_SIZE);
        final PlayerPosition position = player.getPosition();
        player.startRunningOff();
        player.updateFrame();
        final double haltedX = position.getX();

        // Execute
        player.haltRunOff();
        for (int tick = 0; tick < 60; tick++) {
            player.updateFrame();
        }

        // Verify
        assertThat(player.isRunOffHalted()).isTrue();
        assertThat(position.getX()).as("No walk").isCloseTo(haltedX, within(TOLERANCE));
        assertThat(position.getY()).as("No fall").isCloseTo(STANDING_Y, within(TOLERANCE));
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private LevelScenePlayer playerAt(final PlayerMode mode, final double x) {
        final LevelScenePlayer player = new LevelScenePlayer(gameEngine, new PlayerData());
        player.setMode(mode);
        player.getPosition().setX(x);
        player.getPosition().setY(STANDING_Y);
        player.getPosition().snapshotPrevious();
        return player;
    }

    private LevelObject[][] floorUpTo(final int columns) {
        final LevelObject solid = mock(LevelObject.class);
        when(solid.isCollidable()).thenReturn(true);
        final LevelObject[][] objects = emptyCells();
        Arrays.fill(objects[FLOOR_ROW], 0, columns, solid);
        return objects;
    }

    private LevelObject[][] emptyCells() {
        final LevelObject[][] objects = new LevelObject[ROWS][COLUMNS];
        for (final LevelObject[] row : objects) {
            Arrays.fill(row, EMPTY_LEVEL_OBJECT);
        }
        return objects;
    }
}
