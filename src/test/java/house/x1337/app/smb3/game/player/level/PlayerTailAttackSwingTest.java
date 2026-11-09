package house.x1337.app.smb3.game.player.level;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.input.PlayerInputHandler;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.LEFT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static house.x1337.app.smb3.input.PlayerInputHandler.HANDLER_LEFT;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Where the tail swing strikes, and who owns the facing while it runs.
 *
 * <p>Every tail geometry is keyed off the <b>drawn</b> facing and never off the player's intent:
 *
 * <ul>
 *   <li>The tail box's X offset is picked by the drawn facing, and the left-facing offset is {@code +17}: the
 *       box sits <b>behind</b> the drawn facing, where the tail actually is.</li>
 *   <li>The swing mirrors the sprite on counter {@code 11} and back on {@code 3}, so the sprite spins
 *       mid-swing — and the two strike frames straddle that flip ({@code 12} before, {@code 9} after). The
 *       swing therefore covers both sides of the player: behind first, in front second.</li>
 *   <li>The pad's facing update is skipped for the entire countdown, which is what lets the spin survive a
 *       held direction — and stops a held direction from dragging the hitbox onto the wrong side
 *       mid-swing.</li>
 * </ul>
 */
class PlayerTailAttackSwingTest {
    private static final double TOLERANCE = 1e-9;
    private static final double PLAYER_X = 160;
    private static final double PLAYER_Y = 96;

    /** Tail box X offset: {@code +17} behind left-facing art, {@code -10} behind right. */
    private static final double BOX_OFFSET_BEHIND_LEFT_FACING = 17;
    private static final double BOX_OFFSET_BEHIND_RIGHT_FACING = -10;
    private static final double BOX_WIDTH = 10;

    /** The tail box starts 16 px below the sprite's top and is 15 px tall. */
    private static final double BOX_TOP_OFFSET = 16;
    private static final double BOX_HEIGHT = 15;

    private static final int STRIKE_FRAME_EARLY = 12;
    private static final int STRIKE_FRAME_LATE = 9;
    private static final int SWING_START = 18;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private GameEngine gameEngine;
    private PlayerData playerData;

    @BeforeEach
    void prepare() {
        final LevelScene levelScene = mock(LevelScene.class);
        // The player is built at the scene's spawn point.
        when(levelScene.getSpawnPoint()).thenReturn(of(0, 0));
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        playerData = mock(PlayerData.class);

        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        // The one collaborator the geometry reads: a real state object, so the countdown under test is
        // the one the player actually consults. Everything else the constructor pulls is unused here and
        // is left as the static mock's default.
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(PlayerRuntimeState.class))
            .thenAnswer(invocation -> new PlayerRuntimeState());
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("The early strike frame hits behind a right-facing player, the late one in front")
    void strikeSidesStraddleTheSwingFlip() {
        // Prepare
        final LevelScenePlayer player = raccoonFacing(RIGHT);

        // Execute - 12, before the sprite is mirrored
        atSwingFrame(player, STRIKE_FRAME_EARLY);

        // Verify - the tail is still trailing behind him, and so is the box
        assertThat(player.isTailAttackStriking()).isTrue();
        assertThat(player.getTailAttackSwingFacing()).isEqualTo(RIGHT);
        assertBoxSpans(player.getTailAttackBounds(), BOX_OFFSET_BEHIND_RIGHT_FACING);

        // Execute - 9, after the mirror
        atSwingFrame(player, STRIKE_FRAME_LATE);

        // Verify - swung round to the front
        assertThat(player.isTailAttackStriking()).isTrue();
        assertThat(player.getTailAttackSwingFacing()).isEqualTo(LEFT);
        assertBoxSpans(player.getTailAttackBounds(), BOX_OFFSET_BEHIND_LEFT_FACING);
    }

    @Test
    @DisplayName("A left-facing swing covers the mirror image of those two sides")
    void strikeSidesMirrorForALeftFacingSwing() {
        // Prepare
        final LevelScenePlayer player = raccoonFacing(LEFT);

        // Execute & Verify - behind a left-facing player is to his right
        atSwingFrame(player, STRIKE_FRAME_EARLY);
        assertBoxSpans(player.getTailAttackBounds(), BOX_OFFSET_BEHIND_LEFT_FACING);

        atSwingFrame(player, STRIKE_FRAME_LATE);
        assertBoxSpans(player.getTailAttackBounds(), BOX_OFFSET_BEHIND_RIGHT_FACING);
    }

    @Test
    @DisplayName("The swing is drawn mirrored only on the counter range of its flip window")
    void theMirrorCoversExactlyTheFlipWindow() {
        // Prepare
        final LevelScenePlayer player = raccoonFacing(RIGHT);

        // Execute & Verify - the mirror lands on 11 and is undone on 3
        atSwingFrame(player, SWING_START);
        assertThat(player.getTailAttackSwingFacing()).as("Not yet mirrored").isEqualTo(RIGHT);

        atSwingFrame(player, 11);
        assertThat(player.getTailAttackSwingFacing()).as("Mirrored from 11").isEqualTo(LEFT);

        atSwingFrame(player, 4);
        assertThat(player.getTailAttackSwingFacing()).as("Still mirrored at 4").isEqualTo(LEFT);

        atSwingFrame(player, 3);
        assertThat(player.getTailAttackSwingFacing()).as("Mirrored back on 3").isEqualTo(RIGHT);

        atSwingFrame(player, 0);
        assertThat(player.getTailAttackSwingFacing()).as("And after the swing").isEqualTo(RIGHT);
    }

    @Test
    @DisplayName("The tail box always covers the lower body, whichever side it is on")
    void theBoxCoversTheLowerBodyOnBothSides() {
        // Prepare
        final LevelScenePlayer player = raccoonFacing(RIGHT);

        // Execute
        atSwingFrame(player, STRIKE_FRAME_LATE);
        final AxisAlignedBoundingBox bounds = player.getTailAttackBounds();

        // Verify - sprite top + 16, height 15: waist to just above the feet, never the chest
        assertThat(bounds.top()).isCloseTo(PLAYER_Y + BOX_TOP_OFFSET, within(TOLERANCE));
        assertThat(bounds.bottom()).isCloseTo(PLAYER_Y + BOX_TOP_OFFSET + BOX_HEIGHT, within(TOLERANCE));
    }

    @Test
    @DisplayName("The block probe stays in front of the player, since it only fires after the mirror")
    void blockProbeLandsInFrontOfThePlayer() {
        // Prepare - the tail hits blocks on 9 alone, which is inside the flip window, so
        // the behind-the-drawn-facing offset resolves to the tile the player is facing.
        final LevelScenePlayer player = raccoonFacing(RIGHT);

        // Execute
        atSwingFrame(player, STRIKE_FRAME_LATE);

        // Verify
        assertThat(player.isTailAttackStrikingBlocks()).isTrue();
        assertThat(player.getTailAttackBlockProbeX()).isCloseTo(PLAYER_X + 21, within(TOLERANCE));
        assertThat(player.getTailAttackBlockProbeY()).isCloseTo(PLAYER_Y + 28, within(TOLERANCE));
    }

    @Test
    @DisplayName("A direction held during the swing cannot steer it")
    void heldDirectionCannotSteerTheSwing() {
        // Prepare - mid-swing, with LEFT held: the pad's facing update is skipped for the whole
        // countdown, so neither the sprite nor the hitbox may turn round with the player's intent
        final LevelScenePlayer player = raccoonFacing(RIGHT);
        atSwingFrame(player, STRIKE_FRAME_EARLY);
        final PlayerInputHandler inputHandler = mock(PlayerInputHandler.class);
        when(inputHandler.isActive(HANDLER_LEFT)).thenReturn(true);

        // Execute
        player.handleHorizontalMovement(inputHandler);

        // Verify
        assertThat(player.getOrientation().getHorizontal()).isEqualTo(RIGHT);
        assertBoxSpans(player.getTailAttackBounds(), BOX_OFFSET_BEHIND_RIGHT_FACING);

        // Execute - the swing has run out
        atSwingFrame(player, 0);
        player.handleHorizontalMovement(inputHandler);

        // Verify - the facing is the player's again
        assertThat(player.getOrientation().getHorizontal()).isEqualTo(LEFT);
    }

    @Test
    @DisplayName("The swing counter keeps ticking after the suit is lost, so the facing unfreezes")
    void theCounterRunsDownEvenWithoutATailAndFreesTheFacing() {
        // Prepare — the state a hit taken mid-swing leaves behind: the counter survives the hurt (getting
        // hurt never writes it), and the poof has since made the player tailless. The counter is run down
        // outside the player's update and outside every halt, so losing the tail must not strand it — while
        // it is nonzero the pad cannot turn the player, and a stranded counter means a sprite that never
        // mirrors again.
        final LevelScenePlayer player = raccoonFacing(RIGHT);
        atSwingFrame(player, STRIKE_FRAME_LATE);
        player.setMode(NORMAL);
        final PlayerInputHandler inputHandler = mock(PlayerInputHandler.class);
        when(inputHandler.isActive(HANDLER_LEFT)).thenReturn(true);

        // Execute & Verify — still frozen while the swing has frames left
        player.tickTailAttackCountdown();
        player.handleHorizontalMovement(inputHandler);
        assertThat(player.getOrientation().getHorizontal()).isEqualTo(RIGHT);

        // Execute — the rest of the countdown
        for (int tick = 0; tick < STRIKE_FRAME_LATE; tick++) {
            player.tickTailAttackCountdown();
        }

        // Verify
        assertThat(player.getRuntimeState().getPlayerTailAttackCountdown()).isZero();
        player.handleHorizontalMovement(inputHandler);
        assertThat(player.getOrientation().getHorizontal())
            .as("The facing follows the pad again once the swing has run out")
            .isEqualTo(LEFT);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private void assertBoxSpans(
        final AxisAlignedBoundingBox bounds,
        final double expectedLeftOffset
    ) {
        assertThat(bounds.left()).isCloseTo(PLAYER_X + expectedLeftOffset, within(TOLERANCE));
        assertThat(bounds.right()).isCloseTo(PLAYER_X + expectedLeftOffset + BOX_WIDTH, within(TOLERANCE));
    }

    private static void atSwingFrame(final LevelScenePlayer player, final int countdown) {
        player.getRuntimeState().setPlayerTailAttackCountdown(countdown);
    }

    private LevelScenePlayer raccoonFacing(final PlayerOrientationHorizontal facing) {
        final LevelScenePlayer player = new LevelScenePlayer(gameEngine, playerData);
        player.setMode(RACCOON);
        player.getOrientation().setHorizontal(facing);
        player.getPosition().setX(PLAYER_X);
        player.getPosition().setY(PLAYER_Y);
        return player;
    }
}
