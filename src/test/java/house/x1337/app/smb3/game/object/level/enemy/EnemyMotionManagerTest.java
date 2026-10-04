package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.enemy.EnemyMode;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.enumeration.enemy.GoombaMode.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the enemy manager does with its population each tick: it feeds the broadphase only the enemies
 * that are still live participants in collision, so a defeated one playing out its death animation is
 * intangible.
 *
 * <p>The ROM gets this structurally — a defeated object's state routine never reaches
 * {@code Player_HitEnemy} — so there is no flag to port, only the equivalent effect to pin.
 */
class EnemyMotionManagerTest {
    private StubEnemyMotionManager manager;
    private ActiveObjectGrid<ActiveLevelObject> broadPhase;
    private GameEngine gameEngine;

    @BeforeEach
    void prepare() {
        broadPhase = new ActiveObjectGrid<>(mock(EnemySpawner.class));

        final CameraState cameraState = mock(CameraState.class);
        // A region large enough that every fixture enemy is on screen.
        when(cameraState.getActiveObjectRegion(anyInt()))
            .thenReturn(new AxisAlignedBoundingBox(-1000, -1000, 1000, 1000));

        gameEngine = mock(GameEngine.class);
        when(gameEngine.getCameraState()).thenReturn(cameraState);
        when(gameEngine.getActiveObjectGrid()).thenReturn(broadPhase);

        manager = new StubEnemyMotionManager();
    }

    @Test
    @DisplayName("A live enemy is fed to the broadphase each tick")
    void hittableEnemyIsInserted() {
        // Prepare
        final StubEnemy enemy = enemyAt(32);
        manager.spawn(enemy);

        // Execute
        manager.update();

        // Verify
        assertThat(enemy.motionUpdates).as("it was ticked").isEqualTo(1);
        assertThat(broadPhase.query(enemy.getBounds())).containsExactly(enemy);
    }

    @Test
    @DisplayName("An enemy that is no longer hittable is kept out of the broadphase entirely")
    void unhittableEnemyIsNotInserted() {
        // Prepare - a defeated enemy still on screen, seeing out its death animation
        final StubEnemy enemy = enemyAt(32);
        enemy.setHittable(false);
        manager.spawn(enemy);

        // Execute
        manager.update();

        // Verify - still animated, but unreachable: no stomp, no tail strike, no bump from a live enemy
        assertThat(enemy.motionUpdates).as("its death animation still advances").isEqualTo(1);
        assertThat(broadPhase.query(enemy.getBounds())).as("intangible").isEmpty();
    }

    @Test
    @DisplayName("Becoming unhittable mid-life removes an enemy from the broadphase on the next tick")
    void enemyDropsOutOfTheBroadphaseWhenDefeated() {
        // Prepare
        final StubEnemy enemy = enemyAt(32);
        manager.spawn(enemy);
        manager.update();
        assertThat(broadPhase.query(enemy.getBounds())).as("live to begin with").containsExactly(enemy);

        // Execute - defeated, then the next tick's insert phase runs
        enemy.setHittable(false);
        broadPhase.clear();
        manager.update();

        // Verify
        assertThat(broadPhase.query(enemy.getBounds())).isEmpty();
    }

    @Test
    @DisplayName("An expired enemy is retired from the manager and never inserted")
    void expiredEnemyIsRemoved() {
        // Prepare
        final StubEnemy enemy = enemyAt(32);
        enemy.setExpired(true);
        manager.spawn(enemy);

        // Execute
        manager.update();

        // Verify
        assertThat(manager.getActiveInstances()).isEmpty();
        assertThat(broadPhase.query(enemy.getBounds())).isEmpty();
    }

    @Test
    @DisplayName("Reset detaches every live enemy's sprite before clearing, leaving nothing in the scene")
    void resetDetachesLiveEnemies() {
        // A level restart (e.g. after the player dies) resets the managers while enemies are still alive
        // — the one that just killed the player always is. Clearing the list alone would orphan their
        // sprite geometries in the scene; reset must detach each first.
        // Prepare - two live enemies in the manager
        final StubEnemy first = enemyAt(32);
        final StubEnemy second = enemyAt(64);
        manager.spawn(first);
        manager.spawn(second);

        // Execute
        manager.reset();

        // Verify - each was detached exactly once, and the manager carries nothing into the next scene
        assertThat(first.getDetachCount()).as("first enemy's sprite detached").isEqualTo(1);
        assertThat(second.getDetachCount()).as("second enemy's sprite detached").isEqualTo(1);
        assertThat(manager.getActiveInstances()).as("population dropped").isEmpty();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private StubEnemy enemyAt(final double pixelX) {
        return new StubEnemy(gameEngine, pixelX);
    }

    /** A manager with no behaviour of its own, so only the interface's defaults are under test. */
    @Getter
    private static final class StubEnemyMotionManager implements EnemyMotionManager<StubEnemy> {
        private final Class<StubEnemy> type = StubEnemy.class;
        private final List<StubEnemy> activeInstances = new ArrayList<>();
        private final List<ScorePopupAnimation> activeScorePopups = new ArrayList<>();
    }

    /**
     * A 16x16 enemy already in the scene, counting its own ticks. Deliberately not a {@code Goomba}:
     * spawning one needs jME, and the gate under test belongs to the manager, not to any enemy.
     */
    @Getter
    private static final class StubEnemy implements EnemyLevelObject {
        private final GameEngine gameEngine;
        private final double pixelX;
        private final double pixelY = 0;
        private final EnemyMode mode = NORMAL;
        private final boolean spawnedIntoScene = true;
        private final DimensionsPixels boundsPixels = new DimensionsPixels(16, 16);
        @Setter
        private boolean expired;
        @Setter
        private boolean hittable = true;
        @Setter
        private Reward pendingScoreReward;
        private int motionUpdates;
        private int detachCount;

        private StubEnemy(final GameEngine gameEngine, final double pixelX) {
            this.gameEngine = gameEngine;
            this.pixelX = pixelX;
        }

        @Override
        public void motionUpdate() {
            motionUpdates++;
        }

        @Override
        public boolean isHittable() {
            return hittable;
        }

        @Override
        public void spawnIntoScene() {
            throw new UnsupportedOperationException("Already in the scene");
        }

        @Override
        public Dimensions getSpriteDimensions() {
            return Dimensions.fullTile("StubEnemy");
        }

        @Override
        public Geometry getSpriteGeometry() {
            return null;
        }

        @Override
        public void detach() {
            // Records the call rather than touching the scene graph (no sprite was ever attached) so the
            // manager's reset can be shown to detach each live enemy.
            detachCount++;
        }

        @Override
        public Offset getOffset() {
            return Offset.of(0, 0);
        }

        @Override
        public LevelObjectType getType() {
            return null;
        }

        @Override
        public void onCollisionWith(final LevelScenePlayer player) {
            // Not exercised here.
        }

        @Override
        public void hurt(final LevelScenePlayer player) {
            // Not exercised here: what a hit costs the player belongs to each enemy, and the gate under
            // test is the manager's broadphase admission.
        }
    }
}
