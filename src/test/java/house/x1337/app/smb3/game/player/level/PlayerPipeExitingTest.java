package house.x1337.app.smb3.game.player.level;

import com.jme3.scene.Node;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.PipeEntryDirection;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.game.object.level.reward.core.RewardLevelObject;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.ui.tile.Tile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.DOWN;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.LEFT;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.RIGHT;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.UP;
import static house.x1337.app.smb3.enumeration.PipePortalType.OUT;
import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;
import static house.x1337.app.smb3.enumeration.PlayerMovement.WALKING;
import static house.x1337.app.smb3.enumeration.PlayerVisibility.BACKGROUND;
import static house.x1337.app.smb3.enumeration.PlayerVisibility.FOREGROUND;
import static house.x1337.app.smb3.enumeration.TileType.PIPE_BODY_PART;
import static house.x1337.app.smb3.enumeration.TileType.PIPE_TERMINATION_PART;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_HORIZONTAL;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_VERTICAL;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.PIPE_EXIT_TICKS_DOWN;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.PIPE_EXIT_TICKS_SIDE;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.PIPE_EXIT_TICKS_UP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coming out of a pipe (dasm {@code Pipe_Move_Up/Down/Right/Left}, prg029.asm:1626-1740, timed by
 * {@code Event_Countdown_Init}, prg008.asm:735): gameplay stays halted while the player emerges from behind the pipe
 * tiles, moving on every countdown value from the start down to 0, then gets control back.
 *
 * <p>The scene is 16 columns by 12 rows. An upright pipe's lip is at row 8, columns 6-7; an upside-down pipe's lip is
 * at row 3, columns 10-11; a side pipe opens left at column 2 and another opens right at column 13, both on rows 9-10.
 */
class PlayerPipeExitingTest {
    private static final double TOLERANCE = 1e-9;
    private static final int ROWS = 12;
    private static final int COLUMNS = 16;

    private static final PipePortal UPRIGHT = portal(PIPE_PORTAL_HORIZONTAL, 8, 6);
    private static final PipePortal UPSIDE_DOWN = portal(PIPE_PORTAL_HORIZONTAL, 3, 10);
    private static final PipePortal OPENS_LEFT = portal(PIPE_PORTAL_VERTICAL, 10, 2);
    private static final PipePortal OPENS_RIGHT = portal(PIPE_PORTAL_VERTICAL, 10, 13);
    private static final PipePortal ERASED = portal(PIPE_PORTAL_HORIZONTAL, 5, 0);

    private final Tile[][] tiles = new Tile[ROWS][COLUMNS];
    private GameEngine gameEngine;
    private CameraState cameraState;
    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        for (final Tile[] row : tiles) {
            Arrays.fill(row, NULL_TILE);
        }
        paint(8, 6, PIPE_TERMINATION_PART);
        paint(8, 7, PIPE_TERMINATION_PART);
        for (int row = 9; row < ROWS; row++) {
            paint(row, 6, PIPE_BODY_PART);
            paint(row, 7, PIPE_BODY_PART);
        }
        paint(3, 10, PIPE_TERMINATION_PART);
        paint(3, 11, PIPE_TERMINATION_PART);
        for (int row = 0; row < 3; row++) {
            paint(row, 10, PIPE_BODY_PART);
            paint(row, 11, PIPE_BODY_PART);
        }
        for (int row = 9; row <= 10; row++) {
            paint(row, 2, PIPE_TERMINATION_PART);
            paint(row, 3, PIPE_BODY_PART);
            paint(row, 13, PIPE_TERMINATION_PART);
            paint(row, 12, PIPE_BODY_PART);
        }

        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));
        when(levelScene.getStaticEnvironmentLayer()).thenReturn(LevelSceneLayer.builder().tiles(tiles).build());
        cameraState = mock(CameraState.class);
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCameraState()).thenReturn(cameraState);
        when(gameEngine.getRootNode()).thenReturn(new Node("Root"));

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
    @DisplayName("Starting the exit drops velocity and the duck, hides the player behind the pipe and frees the camera")
    void startingTheExit() {
        // Prepare
        final LevelScenePlayer player = player(NORMAL);
        player.getPosition().setDX(1.5);
        player.getPosition().setDY(-2);
        player.getRuntimeState().duck();

        // Execute
        player.beginPipeExit(UPRIGHT);

        // Verify
        assertThat(player.getRuntimeState().isExitingPipe()).isTrue();
        assertThat(player.getRuntimeState().getPipeExitDirection()).isEqualTo(UP);
        assertThat(player.getRuntimeState().getPipeCountdown()).isEqualTo(PIPE_EXIT_TICKS_UP);
        assertThat(player.getPosition().getDX()).isZero();
        assertThat(player.getPosition().getDY()).isZero();
        assertThat(player.getRuntimeState().isDucking()).isFalse();
        assertThat(player.getVisibility()).as("Behind the foreground layers, where the pipe is").isEqualTo(BACKGROUND);
        assertThat(player.getPosition().getPrevY())
            .as("No interpolation from the old spot")
            .isCloseTo(player.getPosition().getY(), within(TOLERANCE));
        verify(cameraState).setTarget(player.getNode());
    }

    @ParameterizedTest
    @EnumSource(value = PlayerMode.class, names = {"SHRUNK", "NORMAL", "RACCOON"})
    @DisplayName("Out of an upright pipe the player rises 0.5 px a tick for 64 ticks and stands on the lip, centred")
    void upwardExitRisesOntoTheLip(final PlayerMode mode) {
        // Prepare - the lip's top edge is Y 128 and the pipe's middle is X 104
        final LevelScenePlayer player = player(mode);
        player.beginPipeExit(UPRIGHT);
        final double startY = player.getPosition().getY();

        // Execute & Verify
        assertThat(player.getPosition().getX()).as("Centred: (X & $F0) | 8").isCloseTo(104, within(TOLERANCE));
        assertThat(startY + 32).as("Feet start 32 px below the lip").isCloseTo(160, within(TOLERANCE));
        for (int tick = 1; tick <= PIPE_EXIT_TICKS_UP + 1; tick++) {
            assertThat(player.getRuntimeState().isExitingPipe()).as("Before tick %d", tick).isTrue();
            player.updateFrame();
            assertThat(player.getPosition().getY())
                .as("Tick %d", tick)
                .isCloseTo(startY - 0.5 * tick, within(TOLERANCE));
        }
        assertThat(player.getRuntimeState().isExitingPipe()).as("Done after 64 ticks").isFalse();
        assertThat(player.getPosition().getY() + 32).as("Feet on the lip").isCloseTo(128, within(TOLERANCE));
        assertThat(player.getRuntimeState().isInAir()).isFalse();
        assertThat(player.getPosition().getDX()).isZero();
        assertThat(player.getPosition().getDY()).isZero();
        assertThat(player.getVisibility()).isEqualTo(FOREGROUND);
    }

    @Test
    @DisplayName("Out of an upside-down pipe the player drops 3.5 px a tick for 8 ticks, then falls with DY 3.5")
    void downwardExitDropsThenFalls() {
        // Prepare - the lip's bottom edge is Y 64
        final LevelScenePlayer player = player(NORMAL);
        player.beginPipeExit(UPSIDE_DOWN);
        final double startY = player.getPosition().getY();

        // Execute & Verify
        assertThat(startY + 32).as("Feet start on the lip's bottom edge").isCloseTo(64, within(TOLERANCE));
        assertThat(player.getPosition().getX()).isCloseTo(168, within(TOLERANCE));
        for (int tick = 1; tick <= PIPE_EXIT_TICKS_DOWN + 1; tick++) {
            player.updateFrame();
            assertThat(player.getPosition().getY())
                .as("Tick %d", tick)
                .isCloseTo(startY + 3.5 * tick, within(TOLERANCE));
        }
        assertThat(player.getRuntimeState().isExitingPipe()).isFalse();
        assertThat(player.getPosition().getY() - startY).isCloseTo(28, within(TOLERANCE));
        assertThat(player.getRuntimeState().isInAir()).as("Falls out").isTrue();
        assertThat(player.getPosition().getDY()).isCloseTo(3.5, within(TOLERANCE));
        assertThat(player.getPosition().getDX()).isZero();
        assertThat(player.getRuntimeState().isDucking()).isFalse();
        assertThat(player.getVisibility()).isEqualTo(FOREGROUND);
    }

    @ParameterizedTest
    @CsvSource({"LEFT, 32, -1, LEFT, RIGHT", "RIGHT, 208, 1, RIGHT, LEFT"})
    @DisplayName("Out of a side pipe the player walks 0.5 px a tick for 33 ticks, facing out and drawn 2 px higher")
    void sideExitWalksOut(
        final PipeEntryDirection direction,
        final double startX,
        final int sign,
        final PlayerOrientationHorizontal facing,
        final PlayerOrientationHorizontal facingBefore
    ) {
        // Prepare - the mouths' bottom edge is Y 176
        final LevelScenePlayer player = player(NORMAL);
        player.getOrientation().setHorizontal(facingBefore);
        player.beginPipeExit(direction == LEFT ? OPENS_LEFT : OPENS_RIGHT);

        // Execute & Verify
        assertThat(player.getRuntimeState().getPipeExitDirection()).isEqualTo(direction);
        assertThat(player.getOrientation().getHorizontal()).isEqualTo(facing);
        assertThat(player.getPosition().getX())
            .as("Inside the mouth, not centred")
            .isCloseTo(startX, within(TOLERANCE));
        assertThat(player.getPosition().getY() + 32).as("Feet on the mouth's bottom").isCloseTo(176, within(TOLERANCE));
        for (int tick = 1; tick <= PIPE_EXIT_TICKS_SIDE; tick++) {
            player.updateFrame();
            assertThat(player.getPosition().getX())
                .as("Tick %d", tick)
                .isCloseTo(startX + sign * 0.5 * tick, within(TOLERANCE));
            assertThat(player.pipeStepUp()).as("Tick %d: drawn 2 px higher", tick).isEqualTo(2f / 16);
        }
        player.updateFrame();
        assertThat(player.getRuntimeState().isExitingPipe()).as("Done after 33 ticks").isFalse();
        assertThat(player.getPosition().getX()).isCloseTo(startX + sign * 16.5, within(TOLERANCE));
        assertThat(player.pipeStepUp()).as("Back to the ground once out").isZero();
        assertThat(player.getRuntimeState().isInAir()).isFalse();
        assertThat(player.getVisibility()).isEqualTo(FOREGROUND);
    }

    @Test
    @DisplayName("The side exit's walk frame moves on every fourth tick")
    void sideExitWalkBeatMovesEveryFourTicks() {
        // Prepare
        final LevelScenePlayer player = player(NORMAL);
        player.beginPipeExit(OPENS_LEFT);
        final List<Integer> beats = new ArrayList<>();

        // Execute
        for (int tick = 1; tick <= PIPE_EXIT_TICKS_SIDE; tick++) {
            player.updateFrame();
            beats.add(player.getRuntimeState().getPipeWalkBeat());
        }

        // Verify - ticks since the start stand in for Counter_1: beat = (ticks & $0C) >> 2
        for (int tick = 1; tick <= PIPE_EXIT_TICKS_SIDE; tick++) {
            assertThat(beats.get(tick - 1)).as("Tick %d", tick).isEqualTo((tick & 0x0C) >> 2);
        }
    }

    @Test
    @DisplayName("The exit halts gameplay, and the player can be neither hurt nor powered up")
    void theExitHaltsGameplay() {
        // Prepare
        final LevelScenePlayer player = player(SHRUNK);
        player.beginPipeExit(UPRIGHT);

        // Execute
        player.onRewardConsumption(mock(RewardLevelObject.class));

        // Verify
        assertThat(player.isHaltingGameplay()).as("Level_PipeMove is part of Player_HaltGame").isTrue();
        assertThat(player.isHurtable()).isFalse();
        assertThat(player.getRuntimeState().isTransitioning()).as("The reward is ignored").isFalse();
        assertThat(player.getRuntimeState().isCarriedObjectInFront()).as("A held object sits in front").isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = PipeEntryDirection.class)
    @DisplayName("The player ignores input and never dies in a pit while coming out")
    void inputAndPitsAreIgnored(final PipeEntryDirection direction) {
        // Prepare - every key held and pressed, and the player already below the visible bottom
        final LevelScenePlayer player = player(NORMAL);
        final PlayerInputHandler inputHandler = player.getInputHandler();
        when(inputHandler.isActive(anyString())).thenReturn(true);
        when(inputHandler.consumePress(anyString())).thenReturn(true);
        when(cameraState.isBelowVisibleBottom(any())).thenReturn(true);
        player.beginPipeExit(portalFor(direction));

        // Execute
        while (player.getRuntimeState().isExitingPipe()) {
            assertThat(player.isHaltingGameplay()).isTrue();
            player.updateFrame();
        }

        // Verify
        assertThat(player.getRuntimeState().isDying()).isFalse();
        assertThat(player.getRuntimeState().getMovement()).as("No movement state from input").isNotEqualTo(WALKING);
        assertThat(player.getMode()).as("No size toggle").isEqualTo(NORMAL);
        assertThat(player.getPosition().getDX()).isZero();
        assertThat(player.isHaltingGameplay()).as("Control is back").isFalse();
    }

    @Test
    @DisplayName("A player that was going into a pipe comes out of the next one with the entry forgotten")
    void exitingClearsTheEntry() {
        // Prepare
        final LevelScenePlayer player = player(NORMAL);
        player.beginPipeEntry(UPRIGHT, DOWN);

        // Execute
        player.beginPipeExit(UPSIDE_DOWN);

        // Verify
        assertThat(player.getRuntimeState().isEnteringPipe()).isFalse();
        assertThat(player.getRuntimeState().getPipeEntryDirection()).isNull();
        assertThat(player.getRuntimeState().isExitingPipe()).isTrue();
        assertThat(player.getRuntimeState().getPipeCountdown()).isEqualTo(PIPE_EXIT_TICKS_DOWN);
    }

    @Test
    @DisplayName("A portal whose pipe was erased puts the player straight on top of it, with no exit to play out")
    void erasedPipeStandsThePlayerOnThePortal() {
        // Prepare
        final LevelScenePlayer player = player(NORMAL);

        // Execute
        player.beginPipeExit(ERASED);

        // Verify
        assertThat(player.getRuntimeState().isExitingPipe()).isFalse();
        assertThat(player.isHaltingGameplay()).isFalse();
        assertThat(player.getPosition().getX()).isCloseTo(0, within(TOLERANCE));
        assertThat(player.getPosition().getY() + 32)
            .as("Feet on the portal's top edge")
            .isCloseTo(80, within(TOLERANCE));
        assertThat(player.getVisibility()).isEqualTo(FOREGROUND);
    }

    private PipePortal portalFor(final PipeEntryDirection direction) {
        return switch (direction) {
            case UP -> UPRIGHT;
            case DOWN -> UPSIDE_DOWN;
            case LEFT -> OPENS_LEFT;
            case RIGHT -> OPENS_RIGHT;
        };
    }

    private LevelScenePlayer player(final PlayerMode mode) {
        final LevelScenePlayer player = new LevelScenePlayer(gameEngine, new PlayerData());
        player.setMode(mode);
        player.getPosition().setX(40);
        player.getPosition().setY(40);
        player.getPosition().snapshotPrevious();
        return player;
    }

    private void paint(final int row, final int column, final TileType type) {
        tiles[row][column] = Tile.builder().id(100 + row * COLUMNS + column).type(type).build();
    }

    private static PipePortal portal(
        final VirtualObjectType virtualObjectType,
        final int row,
        final int column
    ) {
        return PipePortal.builder().type(OUT).virtualObjectType(virtualObjectType).row(row).column(column).build();
    }
}
