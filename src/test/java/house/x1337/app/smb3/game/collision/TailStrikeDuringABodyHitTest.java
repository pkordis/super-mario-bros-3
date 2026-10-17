package house.x1337.app.smb3.game.collision;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.enemy.EnemySpawner;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.RIGHT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The rare original-game moment where one frame both hurts the player and kills the enemy that hurt him.
 *
 * <p>The ROM resolves the two in a fixed order inside the object loop (dasm prg000 @ PRG000_C9B6): the
 * enemy's handler runs first and ends in {@code Player_HitEnemy} (reached through
 * {@code Object_HandleBumpUnderneath}), and {@code Object_HitByTailOrBouncer} follows immediately after.
 * So an enemy whose box overlaps the player's {@code Player_BoundBox} <em>and</em> the tail box pays the
 * player a hit and then dies to the swing, on the same frame.
 *
 * <p>Nothing in {@code Player_GetHurt} (@ PRG000_D9D3 ... PRG000_DA6D) interferes: it writes the queued
 * suit, {@code Player_SuitLost}, {@code Player_FlashInv} and {@code Player_Flip}, and never
 * {@code Player_TailAttack}. That is the whole reason the kill still lands — which is what this test
 * exists to hold onto, because the port used to clear the swing counter while neutralising motion for the
 * suit-loss poof, and the strike dispatched later in the same tick then found nothing to fire.
 */
class TailStrikeDuringABodyHitTest {
    /** The player's reference top; its box spans X {@code [+3, +13]}, Y {@code [+5, +30]}. */
    private static final double PLAYER_Y = 64;

    /** {@code $09}: the swing's late kick frame, mirrored, so the box is in front at {@code +17..+27}. */
    private static final int STRIKE_FRAME_LATE = 0x09;

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

    @Test
    @DisplayName("An enemy touching the player is hurt-dealing and tail-killed on the same tick")
    void aTouchingEnemyBothHurtsAndDies() {
        // Prepare - a box wedged into the seam: X [12,28] clips the player's [3,13] by a pixel and sits
        // inside the tail's [17,27]; Y [80,90] is inside the player's [69,94] and the tail's [80,95].
        final ActiveObjectGrid<ActiveLevelObject> grid = new ActiveObjectGrid<>(mock(EnemySpawner.class));
        final HurtingEnemy enemy = new HurtingEnemy(new AxisAlignedBoundingBox(12, 80, 28, 90));
        grid.insert(enemy);
        final LevelScenePlayer player = raccoonMidSwing();

        // Execute
        grid.resolveActiveObjectCollisions(List.of(player));

        // Verify - the hit cost the suit, and the swing still killed what dealt it
        assertThat(enemy.events).containsExactly("overlap", "tail");
        assertThat(player.getRuntimeState().getQueuedMode())
            .as("The raccoon suit was poofed off by the body hit")
            .isEqualTo(NORMAL);
        assertThat(player.getRuntimeState().getPlayerTailAttackCountdown())
            .as("And the hit left the swing counter alone, as Player_GetHurt does")
            .isEqualTo(STRIKE_FRAME_LATE);
    }

    @Test
    @DisplayName("The same contact one frame off a kick frame only hurts, and kills nothing")
    void offAStrikeFrameTheContactOnlyHurts() {
        // Prepare - identical geometry, counter one short of $09
        final ActiveObjectGrid<ActiveLevelObject> grid = new ActiveObjectGrid<>(mock(EnemySpawner.class));
        final HurtingEnemy enemy = new HurtingEnemy(new AxisAlignedBoundingBox(12, 80, 28, 90));
        grid.insert(enemy);
        final LevelScenePlayer player = raccoonMidSwing();
        player.getRuntimeState().setPlayerTailAttackCountdown(STRIKE_FRAME_LATE - 1);

        // Execute
        grid.resolveActiveObjectCollisions(List.of(player));

        // Verify
        assertThat(enemy.events).containsExactly("overlap");
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** A raccoon player on the late kick frame of a swing, facing right, standing at the origin. */
    private LevelScenePlayer raccoonMidSwing() {
        final LevelScenePlayer player = new LevelScenePlayer(gameEngine, playerData);
        player.setMode(RACCOON);
        player.getOrientation().setHorizontal(RIGHT);
        player.getPosition().setX(0);
        player.getPosition().setY(PLAYER_Y);
        player.getPosition().setDY(1.0);
        player.getRuntimeState().setPlayerTailAttackCountdown(STRIKE_FRAME_LATE);
        return player;
    }

    /**
     * An enemy double that answers a body overlap exactly as every enemy here does — by hurting the
     * player — and records what it was dispatched, in order.
     */
    private static final class HurtingEnemy implements ActiveLevelObject {
        private final AxisAlignedBoundingBox bounds;
        private final List<String> events = new ArrayList<>();

        private HurtingEnemy(final AxisAlignedBoundingBox bounds) {
            this.bounds = bounds;
        }

        @Override
        public AxisAlignedBoundingBox getBounds() {
            return bounds;
        }

        @Override
        public boolean resolvesDirectionalPlayerCollision() {
            return true;
        }

        @Override
        public void onCollisionWith(final LevelScenePlayer player) {
            // The undirected notification carries no response for an enemy.
        }

        @Override
        public void onPlayerOverlap(final LevelScenePlayer player) {
            events.add("overlap");
            player.onHurt();
        }

        @Override
        public void onTailAttack(final LevelScenePlayer player) {
            events.add("tail");
        }

        @Override
        public double getPixelX() {
            return bounds.left();
        }

        @Override
        public double getPixelY() {
            return bounds.top();
        }

        @Override
        public int getXVelocityFixedPoint() {
            return 0;
        }

        @Override
        public int getYVelocityFixedPoint() {
            return 0;
        }

        @Override
        public Dimensions getSpriteDimensions() {
            return null;
        }

        @Override
        public Geometry getSpriteGeometry() {
            return null;
        }

        @Override
        public Offset getOffset() {
            return null;
        }

        @Override
        public LevelObjectType getType() {
            return null;
        }

        @Override
        public GameEngine getGameEngine() {
            return null;
        }
    }
}
