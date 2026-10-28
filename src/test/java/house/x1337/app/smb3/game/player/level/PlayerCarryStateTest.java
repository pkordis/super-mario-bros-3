package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.CarriableLevelObject;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.game.player.PlayerOrientation;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.LEFT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.SUSTAINED;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_DOWN;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_LEFT;
import static house.x1337.app.smb3.model.game.player.PlayerRuntimeState.TURN_AROUND_FACE_TICKS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The player's half of the carry: the two flags the ROM keeps rather than one, what a full pair of hands
 * costs, and the camera-facing pose a turn-around strikes.
 *
 * <p>The two flags are {@code Player_IsHolding} and {@code Player_ISHolding_OLD} (smb3.asm:1904-1905).
 * The second is the one {@code Object_HoldKickOrHurtPlayer} actually tests before allowing a pickup
 * (dasm prg000 @ PRG000_D343), and the lag is load-bearing: the release runs in the object loop and the
 * pickup test runs in the player collision pass that follows it, so without the lag one frame could do
 * both.
 */
class PlayerCarryStateTest {
    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private GameEngine gameEngine;
    private PlayerData playerData;

    @BeforeEach
    void prepare() {
        final LevelScene levelScene = mock(LevelScene.class);
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        playerData = mock(PlayerData.class);

        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(PlayerRuntimeState.class))
            .thenAnswer(invocation -> new PlayerRuntimeState());
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    // -------------------------------------------------------------------------
    // The two flags
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("The lag flag trails the live reference by exactly one tick")
    void theLagFlagTrailsTheReference() {
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        final LevelScenePlayer player = carryingPlayer(runtimeState);

        // Execute & Verify - empty-handed to begin with
        player.advanceCarryLag();
        assertThat(runtimeState.isWasCarrying()).isFalse();

        // Execute - something is picked up partway through the tick
        runtimeState.setCarriedObject(mock(CarriableLevelObject.class));

        // Verify - the lag flag has not caught up yet
        assertThat(runtimeState.isCarrying()).isTrue();
        assertThat(runtimeState.isWasCarrying()).as("still last tick's answer").isFalse();

        // Execute - the next tick
        player.advanceCarryLag();

        // Verify
        assertThat(runtimeState.isWasCarrying()).isTrue();
    }

    @Test
    @DisplayName("A release and a pickup cannot happen on the same frame")
    void aReleaseAndAPickupCannotShareAFrame() {
        // Prepare - carrying at the top of the tick, so the lag flag is set
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        final LevelScenePlayer player = carryingPlayer(runtimeState);
        runtimeState.setCarriedObject(mock(CarriableLevelObject.class));
        player.advanceCarryLag();

        // Execute - the object loop drops it; the player collision pass then asks
        player.releaseCarriedObject();

        // Verify
        assertThat(runtimeState.isCarrying()).as("hands are empty again").isFalse();
        assertThat(player.canPickUpCarriable())
            .as("but Player_ISHolding_OLD still says otherwise, so nothing new is taken")
            .isFalse();

        // Execute - the following tick
        player.advanceCarryLag();

        // Verify
        assertThat(player.canPickUpCarriable()).isTrue();
    }

    @Test
    @DisplayName("Two carriables overlapping on one pass yield one carry, not a stranded second object")
    void onlyOneObjectIsEverTaken() {
        // The ROM's guard is Player_ISHolding_OLD alone, which has a one-tick blind spot it gets away
        // with because it has eight object slots and a flag. A single reference does not: the second
        // object would be left in OBJSTATE_HELD with nothing carrying it.
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        final LevelScenePlayer player = carryingPlayer(runtimeState);
        final CarriableLevelObject first = mock(CarriableLevelObject.class);

        // Execute
        assertThat(player.canPickUpCarriable()).isTrue();
        player.pickUp(first);

        // Verify - the guard refuses a second pickup on this same pass, before any object is touched
        assertThat(player.canPickUpCarriable())
            .as("even though Player_ISHolding_OLD alone would still say yes")
            .isFalse();
        assertThat(runtimeState.isWasCarrying()).as("the lag flag has not advanced").isFalse();
        assertThat(runtimeState.getCarriedObject()).isSameAs(first);
        verify(first).onCarried(player);
    }

    // -------------------------------------------------------------------------
    // Release
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Release is one call site: it clears the carry and kicks the object exactly once")
    void theReleaseIsASingleCallSite() {
        // The single release call site, whose body SMB3-10 replaced outright: there is no drop, only a
        // kick (dasm prg000 @ PRG000_CE2C, BIT Pad_Holding / BVC Player_KickObject).
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        final LevelScenePlayer player = carryingPlayer(runtimeState);
        final CarriableLevelObject carried = mock(CarriableLevelObject.class);
        player.pickUp(carried);
        runtimeState.setTurnAroundFaceCounter(TURN_AROUND_FACE_TICKS);

        // Execute - twice, since the object's held state calls back into the player that called it
        player.releaseCarriedObject();
        player.releaseCarriedObject();

        // Verify
        assertThat(runtimeState.getCarriedObject()).isNull();
        verify(carried, times(1)).onKicked(eq(player), anyBoolean());
        assertThat(runtimeState.getTurnAroundFaceCounter())
            .as("the camera-facing pose runs out on its own: PRG008_AF2F decrements and draws on "
                + "Player_PipeFace alone, before it ever tests Player_IsHolding")
            .isEqualTo(TURN_AROUND_FACE_TICKS);
    }

    @Test
    @DisplayName("Releasing with empty hands touches nothing, so a caller need not ask first")
    void releasingEmptyHandedDoesNothing() {
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        final LevelScenePlayer player = carryingPlayer(runtimeState);
        final CarriableLevelObject neverCarried = mock(CarriableLevelObject.class);

        // Execute
        player.releaseCarriedObject();

        // Verify
        assertThat(runtimeState.getCarriedObject()).isNull();
        verify(neverCarried, never()).onKicked(any(), anyBoolean());
    }

    // -------------------------------------------------------------------------
    // What a full pair of hands costs
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A carrying player cannot duck, however hard DOWN is held")
    void carryingForcesThePlayerToStandUp() {
        // The one restriction prg008 really does place on a carrying player that this port has an
        // equivalent for: Player_IsHolding is ORed into the force-stand-up condition at PRG008_A6F2,
        // beside the small-suit, Frog, slide and Kuribo tests.
        // Prepare - a big player who can and does duck
        final PlayerInputHandler inputHandler = mock(PlayerInputHandler.class);
        when(inputHandler.isActive(HANDLER_DOWN)).thenReturn(true);
        final LevelScenePlayer player = bigPlayer();

        player.handleDucking(inputHandler);
        assertThat(player.getRuntimeState().isDucking()).as("ducking to begin with").isTrue();

        // Execute - hands full, same input
        player.getRuntimeState().setCarriedObject(mock(CarriableLevelObject.class));
        player.handleDucking(inputHandler);

        // Verify
        assertThat(player.getRuntimeState().isDucking()).isFalse();
    }

    // -------------------------------------------------------------------------
    // The turn-around pose
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Turning around with full hands arms the seven-tick camera-facing pose")
    void turningAroundWhileCarryingFacesTheCamera() {
        // Prepare - facing right, carrying, and the pad asking for left
        final PlayerInputHandler inputHandler = mock(PlayerInputHandler.class);
        when(inputHandler.isActive(HANDLER_LEFT)).thenReturn(true);
        final LevelScenePlayer player = bigPlayer();
        player.getOrientation().setHorizontal(RIGHT);
        player.getRuntimeState().setCarriedObject(mock(CarriableLevelObject.class));

        // Execute
        player.handleHorizontalMovement(inputHandler);

        // Verify
        assertThat(player.getOrientation().getHorizontal()).isEqualTo(LEFT);
        assertThat(player.getRuntimeState().getTurnAroundFaceCounter()).isEqualTo(TURN_AROUND_FACE_TICKS);

        // Execute - the same direction held on the following tick, so the facing does not change again
        player.getRuntimeState().setTurnAroundFaceCounter(0);
        player.handleHorizontalMovement(inputHandler);

        // Verify - Player_FlipBits == Player_FlipBits_OLD, so nothing is re-armed
        assertThat(player.getRuntimeState().getTurnAroundFaceCounter()).isZero();
    }

    @Test
    @DisplayName("Turning around with empty hands does not face the camera")
    void turningAroundEmptyHandedDoesNothing() {
        // Prepare
        final PlayerInputHandler inputHandler = mock(PlayerInputHandler.class);
        when(inputHandler.isActive(HANDLER_LEFT)).thenReturn(true);
        final LevelScenePlayer player = bigPlayer();
        player.getOrientation().setHorizontal(RIGHT);

        // Execute
        player.handleHorizontalMovement(inputHandler);

        // Verify
        assertThat(player.getOrientation().getHorizontal()).isEqualTo(LEFT);
        assertThat(player.getRuntimeState().getTurnAroundFaceCounter()).isZero();
    }

    @Test
    @DisplayName("The camera-facing counter yields seven posed ticks, matching the ROM's set-then-decrement")
    void theCameraFacingPoseLastsSevenTicks() {
        // Set to 7 and decremented in the same breath the face-forward frame is chosen (dasm prg008 @
        // PRG008_AF3F into PRG008_AF45), so the counter values 7 down to 1 are all drawn facing forward.
        // Counter arithmetic rather than rendering: the animator needs jME to assert against.
        // Prepare
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setTurnAroundFaceCounter(TURN_AROUND_FACE_TICKS);

        // Execute
        int posedTicks = 0;
        for (int tick = 0; tick < 20; tick++) {
            if (runtimeState.isTurningAroundWhileCarrying()) {
                posedTicks++;
            }
            runtimeState.decrementTurnAroundFace();
        }

        // Verify
        assertThat(posedTicks).isEqualTo(TURN_AROUND_FACE_TICKS);
        assertThat(runtimeState.isTurningAroundWhileCarrying()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** A player whose carry entry points run for real against the given state, and nothing else does. */
    private static LevelScenePlayer carryingPlayer(final PlayerRuntimeState runtimeState) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.asLevelScenePlayer()).thenReturn(player);
        when(player.getOrientation()).thenReturn(new PlayerOrientation(RIGHT, SUSTAINED));
        doCallRealMethod().when(player).isCarrying();
        doCallRealMethod().when(player).canPickUpCarriable();
        doCallRealMethod().when(player).pickUp(any());
        doCallRealMethod().when(player).releaseCarriedObject();
        doCallRealMethod().when(player).kickCarriedObject(any());
        doCallRealMethod().when(player).advanceCarryLag();
        return player;
    }

    /** A real big Mario, so the movement and ducking mixins run end to end. */
    private LevelScenePlayer bigPlayer() {
        final LevelScenePlayer player = new LevelScenePlayer(gameEngine, playerData);
        player.setMode(NORMAL);
        return player;
    }
}
