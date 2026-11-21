package house.x1337.app.smb3.game.object.level.block;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.block.animator.NoteBlockAnimator;
import house.x1337.app.smb3.game.object.level.block.motion.BrickBlockMotionManager;
import house.x1337.app.smb3.game.object.level.block.motion.NoteBlockBounceMotionManager;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.game.sound.SoundPlayer;
import house.x1337.app.smb3.game.time.PowerSwitchTimeWindow;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.service.LevelObjectData;
import house.x1337.app.smb3.service.LevelObjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.Map;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.NOTE_BLOCK_NO_REWARD;
import static house.x1337.app.smb3.enumeration.SoundEffect.BLOCK;
import static house.x1337.app.smb3.enumeration.SoundEffect.BREAKING_BLOCK;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The note block bounces, however it is struck and whoever strikes it, and never breaks, empties or turns into a
 * coin.
 */
class NoteBlockTest {
    private static final Coordinates OFFSET = of(3, 4);

    private final SoundPlayer soundPlayer = mock(SoundPlayer.class);
    private final NoteBlockAnimator noteBlockAnimator = mock(NoteBlockAnimator.class);
    private final NoteBlockBounceMotionManager bounceManager = mock(NoteBlockBounceMotionManager.class);
    private final BrickBlockMotionManager brickBlockMotionManager = mock(BrickBlockMotionManager.class);
    private final LevelObjectService levelObjectService = mock(LevelObjectService.class);
    private final StaticEnvironmentCollisionGrid collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
    private final PowerSwitchTimeWindow powerSwitchTimeWindow = new PowerSwitchTimeWindow();
    private final GameEngine gameEngine = mock(GameEngine.class);
    private final PlayerPosition position = new PlayerPosition();
    private final PlayerRuntimeState runtimeState = new PlayerRuntimeState();

    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class, invocation -> {
            final Class<?> type = invocation.getArgument(0);
            if (type == SoundPlayer.class) {
                return soundPlayer;
            }
            if (type == NoteBlockAnimator.class) {
                return noteBlockAnimator;
            }
            if (type == NoteBlockBounceMotionManager.class) {
                return bounceManager;
            }
            if (type == BrickBlockMotionManager.class) {
                return brickBlockMotionManager;
            }
            if (type == LevelObjectService.class) {
                return levelObjectService;
            }
            if (type == PowerSwitchTimeWindow.class) {
                return powerSwitchTimeWindow;
            }
            return mock(type);
        });
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("Struck from below any number of times, it bounces up every time and never breaks or empties")
    void aHeadHitBouncesItUp(final boolean large) {
        // Prepare
        final Block noteBlock = noteBlock();
        final LevelScenePlayer player = player(large);

        // Execute
        for (int hit = 0; hit < 3; hit++) {
            noteBlock.onCollisionFromBelow(player);
        }

        // Verify
        verify(bounceManager, times(3)).spawnBounceUp(gameEngine, OFFSET, noteBlockAnimator);
        verify(player, times(3)).bounceOffBumpedBlock();
        verifyNoInteractions(levelObjectService, brickBlockMotionManager, collisionGrid);
        verify(soundPlayer, never()).play(BREAKING_BLOCK);
    }

    @Test
    @DisplayName("A tail bounces it up with the bump sound and pushes the player down, also during a P-Switch")
    void aTailBouncesItUp() {
        // Prepare
        final Block noteBlock = noteBlock();
        final LevelScenePlayer player = player(true);
        powerSwitchTimeWindow.activate();

        // Execute
        noteBlock.onTailAttack(player);

        // Verify
        verify(bounceManager).spawnBounceUp(gameEngine, OFFSET, noteBlockAnimator);
        verify(soundPlayer).play(BLOCK);
        verify(player).bounceOffBumpedBlock();
        verifyNoInteractions(levelObjectService, brickBlockMotionManager, collisionGrid);
    }

    @Test
    @DisplayName("A kicked shell bounces it up with the bump sound and leaves the player alone")
    void aKickedShellBouncesItUp() {
        // Prepare
        final Block noteBlock = noteBlock();
        final LevelScenePlayer player = player(false);

        // Execute
        noteBlock.onKickedObjectStrike(player);

        // Verify
        verify(bounceManager).spawnBounceUp(gameEngine, OFFSET, noteBlockAnimator);
        verify(soundPlayer).play(BLOCK);
        verify(player, never()).bounceOffBumpedBlock();
    }

    @Test
    @DisplayName("Landed on, it sinks with the player riding it, with the bump sound")
    void landingOnItStartsTheRide() {
        // Prepare
        final Block noteBlock = noteBlock();
        final LevelScenePlayer player = player(false);

        // Execute
        noteBlock.onCollisionFromAbove(player);

        // Verify
        verify(bounceManager).spawnBounceDown(gameEngine, OFFSET, noteBlockAnimator, player);
        verify(soundPlayer).play(BLOCK);
    }

    @Test
    @DisplayName("Landing on it while it still bounces, or while riding another note block, starts nothing")
    void landingDuringABounceStartsNothing() {
        // Prepare
        final Block noteBlock = noteBlock();
        final LevelScenePlayer player = player(false);
        when(bounceManager.isBounceActiveAt(OFFSET)).thenReturn(true, false);

        // Execute
        noteBlock.onCollisionFromAbove(player);
        runtimeState.setRidingNoteBlock(true);
        noteBlock.onCollisionFromAbove(player);

        // Verify
        verify(bounceManager, never()).spawnBounceDown(any(), any(), any(), any());
        verifyNoInteractions(soundPlayer);
    }

    @Test
    @DisplayName("Players pass into it only while it sinks; for everything else it stays solid")
    void itLetsPlayersThroughOnlyWhileSinking() {
        // Prepare
        final Block noteBlock = noteBlock();
        when(bounceManager.isSinkingAt(OFFSET)).thenReturn(false, true);

        // Execute & Verify
        assertThat(noteBlock.isPassableForPlayers()).as("At rest").isFalse();
        assertThat(noteBlock.isPassableForPlayers()).as("Sinking").isTrue();
        assertThat(noteBlock.isCollidable()).as("Solid ground to enemies and power-ups").isTrue();
    }

    @Test
    @DisplayName("During a P-Switch it stays a solid note block and is never collected as a coin")
    void itIsNotSubstitutedByAPowerSwitch() {
        // Prepare
        final Block noteBlock = noteBlock();
        final LevelScenePlayer player = player(false);
        powerSwitchTimeWindow.activate();

        // Execute
        noteBlock.onPlayerOverlap(player);

        // Verify
        assertThat(noteBlock.isCollidable()).isTrue();
        verifyNoInteractions(collisionGrid, noteBlockAnimator);
        assertThat(player.getPlayerData().getCoins()).isZero();
    }

    @Test
    @DisplayName("It is painted by the note block animator")
    void itIsPaintedByTheNoteBlockAnimator() {
        // Prepare
        final Block noteBlock = noteBlock();

        // Execute
        final Object animator = noteBlock.getAnimator();

        // Verify
        assertThat(animator).isSameAs(noteBlockAnimator);
        assertThat(noteBlock.getType()).isEqualTo(NOTE_BLOCK_NO_REWARD);
    }

    private Block noteBlock() {
        final Block block = new Block(gameEngine, mock(ImageResource.class), OFFSET);
        block.configure(new LevelObjectData(Map.of("blockType", "NOTE_BLOCK_NO_REWARD")));
        return block;
    }

    private LevelScenePlayer player(final boolean large) {
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.getPlayerData()).thenReturn(new PlayerData());
        when(player.getGameEngine()).thenReturn(gameEngine);
        when(player.getCollisionGrid()).thenReturn(collisionGrid);
        when(player.isLarge()).thenReturn(large);
        return player;
    }
}
