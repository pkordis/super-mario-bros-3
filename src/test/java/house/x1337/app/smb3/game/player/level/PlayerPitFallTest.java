package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.event.GameEventBus;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.death.DeathMusicStandIn;
import house.x1337.app.smb3.game.player.level.death.PlayerDeathSequence;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.event.GameEvent;
import house.x1337.app.smb3.model.event.PlayerDeathStarted;
import house.x1337.app.smb3.model.event.PlayerDied;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static house.x1337.app.smb3.GameConstants.PLAYER_PIT_DEATH_COUNTDOWN_TICKS;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerDeathKind.FELL_OFF;
import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A real player, ticked through {@code updateFrame} over a real collision grid with a pit in it: the
 * open bottom edge lets it fall, the pit check kills it once its sprite's top leaves the view (dasm
 * prg008 @ PRG008_A3FA), and the death then runs as {@code Player_Die_FellOff} (prg029) does, ending
 * through the death-music events.
 */
class PlayerPitFallTest {
    private static final double TOLERANCE = 1e-9;
    private static final int COLUMNS = 16;
    private static final int ROWS = 8;
    private static final int FLOOR_ROW = ROWS - 1;
    private static final double LEVEL_BOTTOM_PIXELS = ROWS * (double) TILE_SPRITE_SIZE;

    /** Columns 0 to 3 have a floor on the bottom row; the rest is a pit. */
    private static final int FIRST_PIT_COLUMN = 4;

    /** A grounded player's sprite top, standing level with the floor row. */
    private static final double STANDING_Y = FLOOR_ROW * (double) TILE_SPRITE_SIZE - 32;

    /** Where the old invisible floor held a player up: feet on the level's bottom edge. */
    private static final double ON_THE_OLD_FLOOR_Y = LEVEL_BOTTOM_PIXELS - 32;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private GameEventBus bus;
    private DeathMusicStandIn standIn;
    private List<GameEvent> published;
    private GameEngine gameEngine;

    @BeforeEach
    void prepare() {
        bus = new GameEventBus();
        published = new ArrayList<>();
        bus.subscribe(PlayerDeathStarted.class, published::add);
        bus.subscribe(PlayerDied.class, published::add);
        standIn = new DeathMusicStandIn(bus);
        standIn.subscribe();
        new PlayerDeathSequence(bus).subscribe();

        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

        // The view sits at the level's bottom, as a clamped camera puts it in a level at least as tall
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

        final StaticEnvironmentCollisionGrid grid = new StaticEnvironmentCollisionGrid(
            gameEngine,
            new PowerSwitchTimeWindow()
        );
        grid.setObjects(floorWithAPit());
        grid.setUnderlayObjects(emptyCells());
        grid.setDimensions(new LevelSceneDimensions(COLUMNS, ROWS));
        when(gameEngine.getCollisionGrid()).thenReturn(grid);

        // The player's own collaborators: a fresh state, and inert stand-ins for input, scroll and art
        staticBeanFactory = mockStatic(StaticBeanFactory.class, invocation -> {
            final Class<?> type = invocation.getArgument(0);
            return type == PlayerRuntimeState.class ? new PlayerRuntimeState() : mock(type);
        });
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @ParameterizedTest
    @EnumSource(value = PlayerMode.class, names = {"SHRUNK", "NORMAL", "RACCOON"})
    @DisplayName("Standing over a pit, the player falls through the level's bottom and dies there in any suit")
    void thePlayerFallsThroughTheBottomAndDies(final PlayerMode mode) {
        // Prepare
        final LevelScenePlayer player = playerAt(mode, FIRST_PIT_COLUMN * TILE_SPRITE_SIZE + 32);
        final PlayerPosition position = player.getPosition();
        final PlayerRuntimeState runtimeState = player.getRuntimeState();

        // Execute & Verify
        int tick = 0;
        while (!runtimeState.isDying() && tick < 120) {
            player.updateFrame();
            tick++;
            assertThat(!runtimeState.isInAir() && position.getY() > STANDING_Y)
                .as("Tick %d: standing below the floor row, on nothing (the old floor held it at Y %s)",
                    tick, ON_THE_OLD_FLOOR_Y)
                .isFalse();
        }
        assertThat(runtimeState.getDeathKind()).as("Died in the pit").isEqualTo(FELL_OFF);
        assertThat(position.getY())
            .as("On the first tick its top was at or past the level's bottom, at most one 4 px step past")
            .isBetween(LEVEL_BOTTOM_PIXELS, LEVEL_BOTTOM_PIXELS + 4);
        assertThat(published).as("The death is announced").containsExactly(new PlayerDeathStarted(FELL_OFF));
    }

    @Test
    @DisplayName("Dead in the pit, the player neither moves nor is drawn, and the level ends with the music")
    void thePitDeathHoldsStillThenEndsWithTheMusic() {
        // Prepare - fallen to its death; the engine's halted tick runs the player, then the managers
        final LevelScenePlayer player = playerAt(SHRUNK, FIRST_PIT_COLUMN * TILE_SPRITE_SIZE + 32);
        for (int tick = 0; tick < 120 && !player.getRuntimeState().isDying(); tick++) {
            player.updateFrame();
        }
        assertThat(player.getRuntimeState().isDyingByFallingOff()).as("Died in the pit").isTrue();
        standIn.updateWhileHalted();
        final double deathX = player.getPosition().getX();
        final double deathY = player.getPosition().getY();

        // Execute & Verify - the 191 ticks after the death's own leave the level running
        for (int tick = 1; tick < PLAYER_PIT_DEATH_COUNTDOWN_TICKS; tick++) {
            haltedTick(player);
            assertThat(player.getPosition().getY()).as("Tick %d: no rise, no fall", tick)
                .isCloseTo(deathY, within(TOLERANCE));
            assertThat(player.getRuntimeState().isSpriteDrawnThisTick()).as("Tick %d: not drawn", tick).isFalse();
        }
        assertThat(published).as("No PlayerDied while the music plays").hasSize(1);

        haltedTick(player);
        assertThat(published.getLast()).as("The 192nd tick after the death's ends the level")
            .isInstanceOf(PlayerDied.class);

        for (int tick = 0; tick < 300; tick++) {
            haltedTick(player);
        }
        assertThat(published).as("Exactly once").hasSize(2);
        assertThat(player.getPosition().getX()).isCloseTo(deathX, within(TOLERANCE));
    }

    @Test
    @DisplayName("A player on a real floor tile in the bottom row keeps standing and never dies")
    void aPlayerOnTheBottomRowIsSafe() {
        // Prepare
        final LevelScenePlayer player = playerAt(SHRUNK, TILE_SPRITE_SIZE);

        // Execute
        for (int tick = 0; tick < 120; tick++) {
            player.updateFrame();
        }

        // Verify
        assertThat(player.getRuntimeState().isDying()).isFalse();
        assertThat(player.getRuntimeState().isInAir()).isFalse();
        assertThat(player.getPosition().getY()).isCloseTo(STANDING_Y, within(TOLERANCE));
        assertThat(published).isEmpty();
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

    /** One tick of a game the death has halted, in the engine's order: players, then the managers. */
    private void haltedTick(final LevelScenePlayer player) {
        player.updateFrame();
        standIn.updateWhileHalted();
    }

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
}
