package house.x1337.app.smb3.game.player.level;

import com.jme3.scene.Node;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.PipePortalType;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.PlayerMovement;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.sound.SoundPlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.EMPTY_LEVEL_OBJECT;
import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.DOWN;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.RIGHT;
import static house.x1337.app.smb3.enumeration.PipeEntryDirection.UP;
import static house.x1337.app.smb3.enumeration.PipePortalType.IN;
import static house.x1337.app.smb3.enumeration.PipePortalType.IN_AND_OUT;
import static house.x1337.app.smb3.enumeration.PipePortalType.OUT;
import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;
import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.STILL;
import static house.x1337.app.smb3.enumeration.SoundEffect.BLOCK;
import static house.x1337.app.smb3.enumeration.TileType.PIPE_BODY_PART;
import static house.x1337.app.smb3.enumeration.TileType.PIPE_TERMINATION_PART;
import static house.x1337.app.smb3.enumeration.TileType.SOLID;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_HORIZONTAL;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_VERTICAL;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_DOWN;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_LEFT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_RIGHT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_UP;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pipe entry detection over the scene's pipe portals.
 *
 * <p>The scene, 16 columns by 12 rows with a floor on row 11, holds three pipes, each under an {@code IN} portal:
 * <ul>
 *   <li>an upright pipe with its lip on row 8, columns 4-5 (alignment window X 67 to 76, standing at Y 96);</li>
 *   <li>an upside-down pipe hanging from the top with its lip on row 3, columns 9-10 (window X 147 to 156);</li>
 *   <li>a side pipe opening left, its lip on column 13, rows 9-10, its body to the right.</li>
 * </ul>
 */
class PlayerPipeEntryTest {
    private static final int ROWS = 12;
    private static final int COLUMNS = 16;
    private static final double TOLERANCE = 1e-9;

    private static final double ON_UPRIGHT_PIPE_Y = 8 * 16 - 32;
    private static final double UPRIGHT_PIPE_MIDDLE_X = 72;
    private static final double UPSIDE_DOWN_PIPE_MIDDLE_X = 152;
    private static final double ON_FLOOR_Y = 11 * 16 - 32;

    private static final PipePortal UPRIGHT = portal(IN, PIPE_PORTAL_HORIZONTAL, 8, 4);
    private static final PipePortal UPSIDE_DOWN = portal(IN, PIPE_PORTAL_HORIZONTAL, 3, 9);
    private static final PipePortal SIDE = portal(IN, PIPE_PORTAL_VERTICAL, 10, 13);
    private static final List<PipePortal> ALL_PORTALS = List.of(UPRIGHT, UPSIDE_DOWN, SIDE);

    private final SoundPlayer soundPlayer = mock(SoundPlayer.class);
    private Tile[][] tiles;
    private LevelScene levelScene;
    private GameEngine gameEngine;
    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        tiles = new Tile[ROWS][COLUMNS];
        for (final Tile[] row : tiles) {
            Arrays.fill(row, NULL_TILE);
        }
        for (int column = 0; column < COLUMNS; column++) {
            paint(11, column, SOLID);
        }
        paint(8, 4, PIPE_TERMINATION_PART);
        paint(8, 5, PIPE_TERMINATION_PART);
        for (int row = 9; row <= 10; row++) {
            paint(row, 4, PIPE_BODY_PART);
            paint(row, 5, PIPE_BODY_PART);
        }
        paint(3, 9, PIPE_TERMINATION_PART);
        paint(3, 10, PIPE_TERMINATION_PART);
        for (int row = 0; row <= 2; row++) {
            paint(row, 9, PIPE_BODY_PART);
            paint(row, 10, PIPE_BODY_PART);
        }
        paint(9, 13, PIPE_TERMINATION_PART);
        paint(10, 13, PIPE_TERMINATION_PART);
        for (int row = 9; row <= 10; row++) {
            paint(row, 14, PIPE_BODY_PART);
            paint(row, 15, PIPE_BODY_PART);
        }

        levelScene = mock(LevelScene.class);

        // The player is built at the scene's spawn point.

        when(levelScene.getSpawnPoint()).thenReturn(of(0, 0));
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));
        when(levelScene.getStaticEnvironmentLayer()).thenReturn(LevelSceneLayer.builder().tiles(tiles).build());
        when(levelScene.getPipePortals()).thenReturn(ALL_PORTALS);
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCameraState()).thenReturn(mock(CameraState.class));
        when(gameEngine.getRootNode()).thenReturn(new Node("Root"));

        staticBeanFactory = mockStatic(StaticBeanFactory.class, invocation -> {
            final Class<?> type = invocation.getArgument(0);
            if (type == SoundPlayer.class) {
                return soundPlayer;
            }
            return type == PlayerRuntimeState.class ? new PlayerRuntimeState() : mock(type);
        });
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    // -------------------------------------------------------------------------
    // Upright pipe: DOWN
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = PlayerMode.class, names = {"SHRUNK", "NORMAL", "RACCOON"})
    @DisplayName("Holding DOWN on an upright pipe enters it anywhere inside the window, and nowhere outside it")
    void downEntersAnUprightPipeInsideTheWindow(final PlayerMode mode) {
        // Execute & Verify
        for (int x = 60; x <= 84; x++) {
            final LevelScenePlayer player = player(mode, x, ON_UPRIGHT_PIPE_Y, STILL);
            hold(player, HANDLER_DOWN);
            final boolean expected = x >= 67 && x <= 76;
            assertThat(player.enterPipeIfPossible()).as("X %d", x).isEqualTo(expected);
            assertThat(player.getRuntimeState().getEnteredPipePortal())
                .as("X %d", x)
                .isEqualTo(expected ? UPRIGHT : null);
            assertThat(player.getRuntimeState().getPipeEntryDirection())
                .as("X %d", x)
                .isEqualTo(expected ? DOWN : null);
        }
    }

    @Test
    @DisplayName("An upright pipe is not entered holding another direction, or on a press that is not held")
    void downMustBeHeld() {
        // Prepare
        final LevelScenePlayer holdingUp = player(NORMAL, UPRIGHT_PIPE_MIDDLE_X, ON_UPRIGHT_PIPE_Y, STILL);
        hold(holdingUp, HANDLER_UP);
        final LevelScenePlayer pressedOnly = player(NORMAL, UPRIGHT_PIPE_MIDDLE_X, ON_UPRIGHT_PIPE_Y, STILL);
        when(pressedOnly.getInputHandler().consumePress(anyString())).thenReturn(true);

        // Execute & Verify
        assertThat(holdingUp.enterPipeIfPossible()).isFalse();
        assertThat(pressedOnly.enterPipeIfPossible()).as("A fresh press is not Pad_Holding").isFalse();
    }

    @Test
    @DisplayName("An upright pipe is not entered while airborne over it, even after a ceiling hit")
    void downNeedsTheGround() {
        // Prepare
        final LevelScenePlayer player = player(NORMAL, UPRIGHT_PIPE_MIDDLE_X, ON_UPRIGHT_PIPE_Y, JUMPING);
        player.getRuntimeState().setHitCeiling(true);
        hold(player, HANDLER_DOWN);
        hold(player, HANDLER_UP);

        // Execute & Verify
        assertThat(player.enterPipeIfPossible()).isFalse();
    }

    @Test
    @DisplayName("No pipe is entered while tail attacking or while already going into one")
    void neverWhileTailAttackingOrAlreadyInAPipe() {
        // Prepare
        final LevelScenePlayer swinging = player(RACCOON, UPRIGHT_PIPE_MIDDLE_X, ON_UPRIGHT_PIPE_Y, STILL);
        swinging.getRuntimeState().setPlayerTailAttackCountdown(5);
        hold(swinging, HANDLER_DOWN);
        final LevelScenePlayer inAPipe = player(NORMAL, UPRIGHT_PIPE_MIDDLE_X, ON_UPRIGHT_PIPE_Y, STILL);
        inAPipe.getRuntimeState().startEnteringPipe(SIDE, RIGHT);
        hold(inAPipe, HANDLER_DOWN);

        // Execute & Verify
        assertThat(swinging.enterPipeIfPossible()).isFalse();
        assertThat(inAPipe.enterPipeIfPossible()).isFalse();
        assertThat(inAPipe.getRuntimeState().getEnteredPipePortal()).as("Left as it was").isEqualTo(SIDE);
        assertThat(inAPipe.getRuntimeState().getPipeEntryDirection()).as("Left as it was").isEqualTo(RIGHT);
    }

    @Test
    @DisplayName("A pipe with no portal, or only an OUT one, cannot be entered; IN_AND_OUT can")
    void onlyEnterablePortalsLetThePlayerIn() {
        // Prepare
        final PipePortal inAndOut = portal(IN_AND_OUT, PIPE_PORTAL_HORIZONTAL, 8, 4);

        // Execute & Verify
        when(levelScene.getPipePortals()).thenReturn(null);
        assertThat(standingOnTheUprightPipeHoldingDown().enterPipeIfPossible()).as("No portal list").isFalse();
        when(levelScene.getPipePortals()).thenReturn(List.of());
        assertThat(standingOnTheUprightPipeHoldingDown().enterPipeIfPossible()).as("No portal").isFalse();
        when(levelScene.getPipePortals()).thenReturn(List.of(portal(OUT, PIPE_PORTAL_HORIZONTAL, 8, 4)));
        assertThat(standingOnTheUprightPipeHoldingDown().enterPipeIfPossible()).as("OUT").isFalse();
        when(levelScene.getPipePortals()).thenReturn(List.of(inAndOut));
        final LevelScenePlayer player = standingOnTheUprightPipeHoldingDown();
        assertThat(player.enterPipeIfPossible()).as("IN_AND_OUT").isTrue();
        assertThat(player.getRuntimeState().getEnteredPipePortal()).isSameAs(inAndOut);
    }

    @Test
    @DisplayName("A portal whose pipe lip was erased after placing it cannot be entered")
    void anErasedLipCannotBeEntered() {
        // Prepare
        paint(8, 4, SOLID);
        paint(8, 5, SOLID);

        // Execute & Verify
        assertThat(standingOnTheUprightPipeHoldingDown().enterPipeIfPossible()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Upside-down pipe: UP
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = PlayerMode.class, names = {"SHRUNK", "NORMAL", "RACCOON"})
    @DisplayName("Holding UP with the head against an upside-down pipe enters it inside the window only")
    void upEntersAnUpsideDownPipeInsideTheWindow(final PlayerMode mode) {
        // Execute & Verify
        for (int x = 140; x <= 164; x++) {
            final LevelScenePlayer player = player(mode, x, headAgainstTheLipY(mode), JUMPING);
            player.getRuntimeState().setHitCeiling(true);
            hold(player, HANDLER_UP);
            final boolean expected = x >= 147 && x <= 156;
            assertThat(player.enterPipeIfPossible()).as("X %d", x).isEqualTo(expected);
            assertThat(player.getRuntimeState().getEnteredPipePortal())
                .as("X %d", x)
                .isEqualTo(expected ? UPSIDE_DOWN : null);
            assertThat(player.getRuntimeState().getPipeEntryDirection())
                .as("X %d", x)
                .isEqualTo(expected ? UP : null);
        }
    }

    @Test
    @DisplayName("An upside-down pipe needs a ceiling hit this tick, no duck, UP held and the player in the air")
    void upNeedsACeilingHitInTheAir() {
        // Prepare
        final LevelScenePlayer noHit = underTheUpsideDownPipe(JUMPING);
        final LevelScenePlayer ducking = underTheUpsideDownPipe(JUMPING);
        ducking.getRuntimeState().setHitCeiling(true);
        ducking.getRuntimeState().duck();
        final LevelScenePlayer holdingDown = underTheUpsideDownPipe(JUMPING);
        holdingDown.getRuntimeState().setHitCeiling(true);
        when(holdingDown.getInputHandler().isActive(HANDLER_UP)).thenReturn(false);
        hold(holdingDown, HANDLER_DOWN);
        final LevelScenePlayer grounded = underTheUpsideDownPipe(STILL);
        grounded.getRuntimeState().setHitCeiling(true);

        // Execute & Verify
        assertThat(noHit.enterPipeIfPossible()).as("No ceiling hit").isFalse();
        assertThat(ducking.enterPipeIfPossible()).as("Ducking").isFalse();
        assertThat(holdingDown.enterPipeIfPossible()).as("Wrong direction").isFalse();
        assertThat(grounded.enterPipeIfPossible()).as("On the ground").isFalse();
    }

    // -------------------------------------------------------------------------
    // Side pipe: LEFT / RIGHT
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = PlayerMode.class, names = {"SHRUNK", "NORMAL", "RACCOON"})
    @DisplayName("Standing against a side pipe's lower lip holding RIGHT enters it; LEFT does not")
    void rightEntersASidePipeFromItsLeft(final PlayerMode mode) {
        // Prepare - flush against the lip, as the wall nudge leaves the player
        final double flushX = mode == SHRUNK ? 195 : 194;
        final LevelScenePlayer holdingRight = player(mode, flushX, ON_FLOOR_Y, STILL);
        hold(holdingRight, HANDLER_RIGHT);
        final LevelScenePlayer holdingLeft = player(mode, flushX, ON_FLOOR_Y, STILL);
        hold(holdingLeft, HANDLER_LEFT);

        // Execute & Verify
        assertThat(holdingRight.enterPipeIfPossible()).isTrue();
        assertThat(holdingRight.getRuntimeState().getEnteredPipePortal()).isEqualTo(SIDE);
        assertThat(holdingRight.getRuntimeState().getPipeEntryDirection()).isEqualTo(RIGHT);
        assertThat(holdingLeft.enterPipeIfPossible()).isFalse();
    }

    @Test
    @DisplayName("A side pipe is entered through its lower cell only, and only from the ground")
    void sidePipeNeedsTheLowerCellAndTheGround() {
        // Prepare - a tile higher, the probe in front reads the lip's upper cell
        final LevelScenePlayer upperCell = player(NORMAL, 194, ON_FLOOR_Y - 16, STILL);
        hold(upperCell, HANDLER_RIGHT);
        final LevelScenePlayer airborne = player(NORMAL, 194, ON_FLOOR_Y, JUMPING);
        airborne.getRuntimeState().setHitCeiling(true);
        hold(airborne, HANDLER_RIGHT);

        // Execute & Verify
        assertThat(upperCell.enterPipeIfPossible()).isFalse();
        assertThat(airborne.enterPipeIfPossible()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Through the tick
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("On the tick the player goes into an upright pipe the tick ends there")
    void enteringEndsTheTick() {
        // Prepare
        withCollisionGrid();
        final LevelScenePlayer player = standingOnTheUprightPipeHoldingDown();
        player.getRuntimeState().setTurnAroundFaceCounter(3);

        // Execute
        player.updateFrame();

        // Verify
        assertThat(player.getRuntimeState().getEnteredPipePortal()).isEqualTo(UPRIGHT);
        assertThat(player.getRuntimeState().getPipeEntryDirection()).isEqualTo(DOWN);
        assertThat(player.getRuntimeState().getTurnAroundFaceCounter())
            .as("The rest of the tick, where Player_PipeFace is decremented, did not run")
            .isEqualTo(3);
        assertThat(player.getPosition().getY()).isCloseTo(ON_UPRIGHT_PIPE_Y, within(TOLERANCE));
    }

    @Test
    @DisplayName("Jumping into an upside-down pipe holding UP enters it on the tick the head strikes the lip")
    void jumpingIntoAnUpsideDownPipeEntersIt() {
        // Prepare - rising, two pixels below where this tick's step puts the head inside the lip
        withCollisionGrid();
        final LevelScenePlayer player = player(NORMAL, UPSIDE_DOWN_PIPE_MIDDLE_X, 58, JUMPING);
        player.getPosition().setDY(-2);
        hold(player, HANDLER_UP);

        // Execute
        player.updateFrame();

        // Verify
        assertThat(player.getRuntimeState().isHitCeiling()).isTrue();
        assertThat(player.getRuntimeState().getEnteredPipePortal()).isEqualTo(UPSIDE_DOWN);
        assertThat(player.getRuntimeState().getPipeEntryDirection()).isEqualTo(UP);
    }

    @Test
    @DisplayName("The head striking something solid plays the bump sound once, and the next tick plays nothing")
    void aHeadStrikePlaysTheBumpSoundOnce() {
        // Prepare - rising into the upside-down pipe's lip, without UP held, so the player bumps it
        withCollisionGrid();
        final LevelScenePlayer player = player(NORMAL, UPSIDE_DOWN_PIPE_MIDDLE_X, 58, JUMPING);
        player.getPosition().setDY(-2);

        // Execute
        player.updateFrame();
        final boolean hitTheCeiling = player.getRuntimeState().isHitCeiling();
        player.updateFrame();

        // Verify
        assertThat(hitTheCeiling).isTrue();
        verify(player.getSoundPlayer(), times(1)).play(BLOCK);
    }

    @Test
    @DisplayName("Holding DOWN on an upright pipe without a portal only ducks")
    void anUprightPipeWithoutAPortalOnlyDucks() {
        // Prepare
        withCollisionGrid();
        when(levelScene.getPipePortals()).thenReturn(List.of());
        final LevelScenePlayer player = standingOnTheUprightPipeHoldingDown();

        // Execute
        for (int tick = 0; tick < 10; tick++) {
            player.updateFrame();
        }

        // Verify
        assertThat(player.getRuntimeState().isEnteringPipe()).isFalse();
        assertThat(player.getRuntimeState().isDucking()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private LevelScenePlayer standingOnTheUprightPipeHoldingDown() {
        final LevelScenePlayer player = player(NORMAL, UPRIGHT_PIPE_MIDDLE_X, ON_UPRIGHT_PIPE_Y, STILL);
        hold(player, HANDLER_DOWN);
        return player;
    }

    private LevelScenePlayer underTheUpsideDownPipe(final PlayerMovement movement) {
        final LevelScenePlayer player = player(
            NORMAL,
            UPSIDE_DOWN_PIPE_MIDDLE_X,
            headAgainstTheLipY(NORMAL),
            movement
        );
        hold(player, HANDLER_UP);
        return player;
    }

    /** The head probe is Y+6 when big and Y+16 when small; either way it lands inside the lip, row 3. */
    private static double headAgainstTheLipY(final PlayerMode mode) {
        return 3 * 16 + 8 - (mode == SHRUNK ? 16 : 6);
    }

    private LevelScenePlayer player(
        final PlayerMode mode,
        final double x,
        final double y,
        final PlayerMovement movement
    ) {
        final LevelScenePlayer player = new LevelScenePlayer(gameEngine, new PlayerData());
        player.setMode(mode);
        player.getPosition().setX(x);
        player.getPosition().setY(y);
        player.getPosition().snapshotPrevious();
        player.getRuntimeState().setTo(movement);
        return player;
    }

    private static void hold(final LevelScenePlayer player, final String key) {
        final PlayerInputHandler inputHandler = player.getInputHandler();
        when(inputHandler.isActive(key)).thenReturn(true);
    }

    private void withCollisionGrid() {
        final LevelObject solid = mock(LevelObject.class);
        when(solid.isCollidable()).thenReturn(true);
        final LevelObject[][] objects = new LevelObject[ROWS][COLUMNS];
        final LevelObject[][] underlay = new LevelObject[ROWS][COLUMNS];
        for (int row = 0; row < ROWS; row++) {
            Arrays.fill(underlay[row], EMPTY_LEVEL_OBJECT);
            for (int column = 0; column < COLUMNS; column++) {
                objects[row][column] = tiles[row][column] == NULL_TILE ? EMPTY_LEVEL_OBJECT : solid;
            }
        }
        final StaticEnvironmentCollisionGrid grid = new StaticEnvironmentCollisionGrid(
            gameEngine,
            new PowerSwitchTimeWindow()
        );
        grid.setObjects(objects);
        grid.setUnderlayObjects(underlay);
        grid.setDimensions(new LevelSceneDimensions(COLUMNS, ROWS));
        when(gameEngine.getCollisionGrid()).thenReturn(grid);
    }

    private void paint(final int row, final int column, final TileType type) {
        tiles[row][column] = Tile.builder().id(100 + row * COLUMNS + column).type(type).build();
    }

    private static PipePortal portal(
        final PipePortalType type,
        final VirtualObjectType virtualObjectType,
        final int row,
        final int column
    ) {
        return PipePortal.builder()
            .type(type)
            .virtualObjectType(virtualObjectType)
            .coordinates(of(column, row))
            .build();
    }
}
