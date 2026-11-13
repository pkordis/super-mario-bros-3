package house.x1337.app.smb3.game.player.level.animator;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import house.x1337.app.smb3.model.game.player.PlayerOrientation;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.game.player.level.asset.NormalAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.RaccoonAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.ShrunkAnimatorAssets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static house.x1337.app.smb3.GameConstants.PLAYER_END_LEVEL_X_VELOCITY;
import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;
import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;
import static house.x1337.app.smb3.enumeration.PlayerMovement.WALKING;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static house.x1337.app.smb3.enumeration.PlayerOrientationVertical.SUSTAINED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The end-level walk-off's walk cycle: a new walk frame every 2 ticks for every suit, against every 5 ticks for an
 * ordinary walk at the same 1.25 px per tick. Real animators with their real sprites are run against a stand-in
 * player, and the texture they draw is read back from the player's node.
 */
@TestInstance(PER_CLASS)
class PlayerRunOffWalkAnimationTest {
    private static final int TICKS = 40;
    private static final int ORDINARY_WALK_TICKS_PER_FRAME = 5;

    private GameEngine gameEngine;
    private PlayerIdentity identity;

    @BeforeEach
    void prepare() {
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getAssetManager()).thenReturn(new DesktopAssetManager(true));
        identity = mock(PlayerIdentity.class);
        when(identity.getAnimationFramesPath()).thenReturn("mario");
    }

    Stream<PlayerMode> suits() {
        return Stream.of(SHRUNK, NORMAL, RACCOON);
    }

    @ParameterizedTest
    @MethodSource("suits")
    @DisplayName("In the walk-off the walk frame changes every 2 ticks, whatever the suit")
    void theWalkOffChangesFrameEveryTwoTicks(final PlayerMode mode) {
        // Prepare
        final LevelScenePlayerAnimator<?> animator = animatorFor(mode);
        final LevelScenePlayer player = walkingPlayer(true);

        // Execute
        final List<Integer> frameChanges = frameChangeTicks(animator, player);

        // Verify
        assertThat(frameChanges).as("A new frame on every second tick")
            .isEqualTo(everyNthTick(BaseLevelScenePlayerAnimator.RUN_OFF_WALK_TICKS_PER_FRAME));
    }

    @ParameterizedTest
    @MethodSource("suits")
    @DisplayName("Outside the walk-off a walk at the same speed still changes frame every 5 ticks")
    void anOrdinaryWalkStillChangesFrameEveryFiveTicks(final PlayerMode mode) {
        // Prepare
        final LevelScenePlayerAnimator<?> animator = animatorFor(mode);
        final LevelScenePlayer player = walkingPlayer(false);

        // Execute
        final List<Integer> frameChanges = frameChangeTicks(animator, player);

        // Verify
        assertThat(frameChanges).as("A new frame on every fifth tick")
            .isEqualTo(everyNthTick(ORDINARY_WALK_TICKS_PER_FRAME));
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private LevelScenePlayerAnimator<?> animatorFor(final PlayerMode mode) {
        return switch (mode) {
            case SHRUNK -> {
                final ShrunkAnimator animator = new ShrunkAnimator(gameEngine, identity);
                ShrunkAnimatorAssets.loadFor(animator);
                yield animator;
            }
            case NORMAL -> {
                final NormalAnimator animator = new NormalAnimator(gameEngine, identity);
                NormalAnimatorAssets.loadFor(animator);
                yield animator;
            }
            case RACCOON -> {
                final RaccoonAnimator animator = new RaccoonAnimator(gameEngine, identity);
                RaccoonAnimatorAssets.loadFor(animator);
                yield animator;
            }
            default -> throw new IllegalArgumentException("No animator for " + mode);
        };
    }

    private LevelScenePlayer walkingPlayer(final boolean runningOff) {
        final PlayerRuntimeState runtimeState = new PlayerRuntimeState();
        runtimeState.setTo(WALKING);
        final PlayerPosition position = new PlayerPosition();
        position.setDX(PLAYER_END_LEVEL_X_VELOCITY);
        final Node node = new Node("player");

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getNode()).thenReturn(node);
        when(player.getOrientation()).thenReturn(new PlayerOrientation(RIGHT, SUSTAINED));
        when(player.getRuntimeState()).thenReturn(runtimeState);
        when(player.getPosition()).thenReturn(position);
        when(player.isRunningOff()).thenReturn(runningOff);
        return player;
    }

    private List<Integer> frameChangeTicks(final LevelScenePlayerAnimator<?> animator, final LevelScenePlayer player) {
        final List<Integer> changes = new ArrayList<>();
        Texture shown = null;
        for (int tick = 1; tick <= TICKS; tick++) {
            animator.update(player);
            final Texture now = shownTexture(player);
            if (shown != null && now != shown) {
                changes.add(tick);
            }
            shown = now;
        }
        return changes;
    }

    private Texture shownTexture(final LevelScenePlayer player) {
        final Geometry geometry = (Geometry) player.getNode().getChild(0);
        return geometry.getMaterial().getTextureParam("ColorMap").getTextureValue();
    }

    private List<Integer> everyNthTick(final int ticksPerFrame) {
        return IntStream.rangeClosed(1, TICKS)
            .filter(tick -> tick % ticksPerFrame == 0)
            .boxed()
            .toList();
    }
}
