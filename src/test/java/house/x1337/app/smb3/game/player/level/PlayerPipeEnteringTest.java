package house.x1337.app.smb3.game.player.level;

import com.jme3.scene.Node;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.PipeEntryDirection;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.reward.core.RewardLevelObject;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.event.GameEvent;
import house.x1337.app.smb3.model.event.LevelSceneResetRequested;
import house.x1337.app.smb3.model.event.PipePortalTransitRequested;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.enumeration.PipeEntryDirection.DOWN;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.LEFT;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.RIGHT;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.UP;
import static house.x1337.app.smb3.enumeration.PipePortalType.IN;
import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;
import static house.x1337.app.smb3.enumeration.PlayerMovement.WALKING;
import static house.x1337.app.smb3.enumeration.PlayerVisibility.BACKGROUND;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_HORIZONTAL;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_VERTICAL;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.PIPE_ENTRY_TICKS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The pipe-entering state, driven by an event countdown:
 * gameplay halts, the player slides into the pipe for 60 ticks behind the pipe tiles with the camera held, then asks
 * for the trip to the linked pipe.
 */
class PlayerPipeEnteringTest {
    private static final double TOLERANCE = 1e-9;
    private static final double START_X = 100;
    private static final double START_Y = 96;

    // 59 moving ticks (countdown 59 to 1) for an upright or upside-down pipe, 44 (59 to 16) for a side pipe
    private static final int VERTICAL_MOVING_TICKS = 59;
    private static final int SIDE_MOVING_TICKS = 44;

    private static final PipePortal HORIZONTAL_PORTAL = PipePortal.builder()
        .type(IN)
        .virtualObjectType(PIPE_PORTAL_HORIZONTAL)
        .coordinates(of(6, 8))
        .build();
    private static final PipePortal VERTICAL_PORTAL = PipePortal.builder()
        .type(IN)
        .virtualObjectType(PIPE_PORTAL_VERTICAL)
        .coordinates(of(7, 10))
        .build();

    private final List<GameEvent> published = new ArrayList<>();
    private GameEngine gameEngine;
    private CameraState cameraState;
    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        final LevelScene levelScene = mock(LevelScene.class);
        // The player is built at the scene's spawn point.
        when(levelScene.getSpawnPoint()).thenReturn(of(0, 0));
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(16, 12));
        cameraState = mock(CameraState.class);
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCameraState()).thenReturn(cameraState);
        when(gameEngine.getRootNode()).thenReturn(new Node("Root"));
        doAnswer(invocation -> published.add(invocation.getArgument(0))).when(gameEngine).publish(any());

        staticBeanFactory = mockStatic(StaticBeanFactory.class, invocation -> {
            final Class<?> type = invocation.getArgument(0);
            return type == PlayerRuntimeState.class ? new PlayerRuntimeState() : mock(type);
        });
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Going into a pipe drops velocity and the duck, hides the player behind the pipe and holds the camera")
    void startingTheEntry() {
        // Prepare
        final LevelScenePlayer player = player(NORMAL);
        player.getPosition().setDX(1.5);
        player.getPosition().setDY(-2);
        player.getRuntimeState().duck();

        // Execute
        player.beginPipeEntry(HORIZONTAL_PORTAL, DOWN);

        // Verify
        assertThat(player.getRuntimeState().getPipeCountdown()).isEqualTo(PIPE_ENTRY_TICKS);
        assertThat(player.getPosition().getDX()).isZero();
        assertThat(player.getPosition().getDY()).isZero();
        assertThat(player.getRuntimeState().isDucking()).isFalse();
        assertThat(player.getVisibility()).as("Behind the foreground layers, where the pipe is").isEqualTo(BACKGROUND);
        verify(cameraState).hold();
    }

    @ParameterizedTest
    @CsvSource({"RIGHT, LEFT, RIGHT", "LEFT, RIGHT, LEFT"})
    @DisplayName("A side pipe turns the player to face the way it walks in")
    void aSidePipeTurnsThePlayer(
        final PipeEntryDirection direction,
        final PlayerOrientationHorizontal facingBefore,
        final PlayerOrientationHorizontal facingAfter
    ) {
        // Prepare
        final LevelScenePlayer player = player(NORMAL);
        player.getOrientation().setHorizontal(facingBefore);

        // Execute
        player.beginPipeEntry(VERTICAL_PORTAL, direction);

        // Verify
        assertThat(player.getOrientation().getHorizontal()).isEqualTo(facingAfter);
    }

    @Test
    @DisplayName("The entry halts gameplay, and the player can be neither hurt nor powered up")
    void theEntryHaltsGameplay() {
        // Prepare
        final LevelScenePlayer player = player(SHRUNK);
        player.beginPipeEntry(HORIZONTAL_PORTAL, DOWN);

        // Execute
        player.onRewardConsumption(mock(RewardLevelObject.class));

        // Verify
        assertThat(player.isHaltingGameplay()).as("Level_PipeMove is part of Player_HaltGame").isTrue();
        assertThat(player.isHurtable()).isFalse();
        assertThat(player.getRuntimeState().isTransitioning()).as("The reward is ignored").isFalse();
    }

    @ParameterizedTest
    @CsvSource({"SHRUNK, DOWN, 1", "NORMAL, DOWN, 1", "RACCOON, DOWN, 1", "NORMAL, UP, -1", "SHRUNK, UP, -1"})
    @DisplayName("Up or down, the player moves 0.5 px every tick of the countdown, 29.5 px in all")
    void verticalEntryMovesHalfAPixelEveryTick(
        final PlayerMode mode,
        final PipeEntryDirection direction,
        final int sign
    ) {
        // Prepare
        final LevelScenePlayer player = player(mode);
        player.beginPipeEntry(HORIZONTAL_PORTAL, direction);

        // Execute & Verify
        for (int tick = 1; tick <= VERTICAL_MOVING_TICKS; tick++) {
            player.updateFrame();
            assertThat(player.getPosition().getY())
                .as("Tick %d", tick)
                .isCloseTo(START_Y + sign * 0.5 * tick, within(TOLERANCE));
            assertThat(player.getRuntimeState().isSpriteDrawnThisTick()).as("Tick %d: drawn", tick).isTrue();
        }
        player.updateFrame();
        assertThat(player.getPosition().getY()).isCloseTo(START_Y + sign * 29.5, within(TOLERANCE));
        assertThat(player.getPosition().getX()).isCloseTo(START_X, within(TOLERANCE));
        assertThat(player.getRuntimeState().isSpriteDrawnThisTick()).as("Not drawn on the last tick").isFalse();
    }

    @ParameterizedTest
    @CsvSource({"RIGHT, 1", "LEFT, -1"})
    @DisplayName("Into a side pipe, the player walks 0.5 px a tick while the countdown is at least 16, then vanishes")
    void sideEntryWalksWhileTheCountdownIsAtLeastSixteen(final PipeEntryDirection direction, final int sign) {
        // Prepare
        final LevelScenePlayer player = player(NORMAL);
        player.beginPipeEntry(VERTICAL_PORTAL, direction);
        assertThat(player.pipeStepUp()).as("No step up on the entry tick").isZero();

        // Execute & Verify
        for (int tick = 1; tick <= SIDE_MOVING_TICKS; tick++) {
            player.updateFrame();
            assertThat(player.getPosition().getX())
                .as("Tick %d", tick)
                .isCloseTo(START_X + sign * 0.5 * tick, within(TOLERANCE));
            assertThat(player.pipeStepUp()).as("Tick %d: drawn 2 px higher", tick).isEqualTo(2f / 16);
            assertThat(player.getRuntimeState().isSpriteDrawnThisTick()).as("Tick %d: drawn", tick).isTrue();
        }
        for (int tick = SIDE_MOVING_TICKS + 1; tick <= PIPE_ENTRY_TICKS; tick++) {
            player.updateFrame();
            assertThat(player.getPosition().getX())
                .as("Tick %d", tick)
                .isCloseTo(START_X + sign * 22, within(TOLERANCE));
            assertThat(player.pipeStepUp()).as("Tick %d", tick).isZero();
            assertThat(player.getRuntimeState().isSpriteDrawnThisTick()).as("Tick %d: hidden", tick).isFalse();
        }
        assertThat(player.getPosition().getY()).as("Only the drawing steps up").isCloseTo(START_Y, within(TOLERANCE));
    }

    @Test
    @DisplayName("The side pipe walk frame moves on every fourth tick")
    void sideWalkBeatMovesEveryFourTicks() {
        // Prepare
        final LevelScenePlayer player = player(NORMAL);
        player.beginPipeEntry(VERTICAL_PORTAL, RIGHT);
        final List<Integer> beats = new ArrayList<>();

        // Execute
        for (int tick = 1; tick <= SIDE_MOVING_TICKS; tick++) {
            player.updateFrame();
            beats.add(player.getRuntimeState().getPipeWalkBeat());
        }

        // Verify - ticks since the entry stand in for the global frame counter: beat = (ticks & 12) >> 2
        for (int tick = 1; tick <= SIDE_MOVING_TICKS; tick++) {
            assertThat(beats.get(tick - 1)).as("Tick %d", tick).isEqualTo((tick & 12) >> 2);
        }
    }

    @ParameterizedTest
    @EnumSource(value = PipeEntryDirection.class)
    @DisplayName("The player ignores input and never dies in a pit while in the pipe")
    void inputAndPitsAreIgnored(final PipeEntryDirection direction) {
        // Prepare - every key held and pressed, and the player already below the visible bottom
        final LevelScenePlayer player = player(NORMAL);
        final PlayerInputHandler inputHandler = player.getInputHandler();
        when(inputHandler.isActive(anyString())).thenReturn(true);
        when(inputHandler.consumePress(anyString())).thenReturn(true);
        when(cameraState.isBelowVisibleBottom(any())).thenReturn(true);
        player.beginPipeEntry(direction == UP || direction == DOWN ? HORIZONTAL_PORTAL : VERTICAL_PORTAL, direction);

        // Execute
        for (int tick = 1; tick <= PIPE_ENTRY_TICKS; tick++) {
            player.updateFrame();
        }

        // Verify
        assertThat(player.getRuntimeState().isDying()).isFalse();
        assertThat(player.getRuntimeState().getMovement()).as("No movement state from input").isNotEqualTo(WALKING);
        assertThat(player.getMode()).as("No size toggle").isEqualTo(NORMAL);
        assertThat(player.getPosition().getDX()).isZero();
        assertThat(player.getPosition().getDY()).isZero();
    }

    @Test
    @DisplayName("Exactly one transit request is published, on the tick the countdown reaches 0, and no reset request")
    void oneTransitRequestWhenTheCountdownRunsOut() {
        // Prepare
        final LevelScenePlayer player = player(NORMAL);
        when(player.getInputHandler().getId()).thenReturn("player-one");
        player.beginPipeEntry(HORIZONTAL_PORTAL, DOWN);

        // Execute & Verify
        for (int tick = 1; tick < PIPE_ENTRY_TICKS; tick++) {
            player.updateFrame();
            assertThat(published).as("Tick %d", tick).isEmpty();
        }
        player.updateFrame();
        assertThat(published)
            .as("Tick 60")
            .containsExactly(
                PipePortalTransitRequested.builder()
                    .pipePortalId(HORIZONTAL_PORTAL.getId())
                    .inputHandlerId("player-one")
                    .build()
            );
        for (int tick = 0; tick < 30; tick++) {
            player.updateFrame();
        }
        assertThat(published).as("Still one while the trip runs").hasSize(1);
        assertThat(published).noneMatch(LevelSceneResetRequested.class::isInstance);
        assertThat(player.isHaltingGameplay()).as("Still halting until then").isTrue();
    }

    private LevelScenePlayer player(final PlayerMode mode) {
        final LevelScenePlayer player = new LevelScenePlayer(gameEngine, new PlayerData());
        player.setMode(mode);
        player.getPosition().setX(START_X);
        player.getPosition().setY(START_Y);
        player.getPosition().snapshotPrevious();
        return player;
    }
}
