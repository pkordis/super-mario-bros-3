package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.enemy.EnemyMode;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.CarriableLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.enemy.base.EnemyLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.motion.EnemyMotionManager;
import house.x1337.app.smb3.game.object.level.enemy.spawner.EnemySpawner;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.enumeration.enemy.GoombaMode.NORMAL;
import static house.x1337.app.smb3.model.game.motion.FixedPointVelocity.STILL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private StubEnemyMotionManager manager;
    private ActiveObjectGrid<ActiveLevelObject> broadPhase;
    private PlayerData playerData;
    private GameEngine gameEngine;

    @BeforeEach
    void prepare() {
        broadPhase = new ActiveObjectGrid<>(mock(EnemySpawner.class));

        final CameraState cameraState = mock(CameraState.class);
        // A region large enough that every fixture enemy is on screen.
        when(cameraState.getActiveObjectRegion(anyInt()))
            .thenReturn(new AxisAlignedBoundingBox(-1000, -1000, 1000, 1000));

        playerData = new PlayerData();
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getCameraState()).thenReturn(cameraState);
        when(gameEngine.getActiveObjectGrid()).thenReturn(broadPhase);
        when(gameEngine.getPlayerData()).thenReturn(playerData);

        // The caption is a prototype bean; a mock is enough, since what it looks like is its own test's
        // subject and only its presence matters here. Reward.Data is a prototype too, and is what carries
        // the points and lives this test is about, so that one has to be real.
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(Reward.Data.class))
            .thenAnswer(invocation -> new Reward.Data());
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(ScorePopupAnimation.class), any(), any(), any()))
            .thenAnswer(invocation -> mock(ScorePopupAnimation.class));

        manager = new StubEnemyMotionManager();
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
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

    @Test
    @DisplayName("An enemy the camera has not reached stands still and stays out of the broadphase")
    void offWindowEnemyIsFrozen() {
        // Prepare - well outside the fixture's generous active region
        final StubEnemy enemy = enemyAt(100_000);
        manager.spawn(enemy);

        // Execute
        manager.update();

        // Verify
        assertThat(enemy.motionUpdates).as("not ticked").isZero();
        assertThat(broadPhase.query(enemy.getBounds())).isEmpty();
    }

    @Test
    @DisplayName("An object in the player's hands keeps ticking even outside the activation window")
    void carriedEnemyIsExemptFromTheActivationWindow() {
        // A carried object is not being simulated, it is being placed, from a player who is on screen by
        // definition. Skipping its tick would not pause anything, it would leave it behind where the
        // window lost it and out of the broadphase. Once released it is an ordinary object again, which
        // is why the second half of this asserts the window takes its authority straight back.
        // Prepare - well outside the fixture's active region, but carried
        final StubEnemy enemy = enemyAt(100_000);
        enemy.setCarried(true);
        manager.spawn(enemy);

        // Execute
        manager.update();

        // Verify
        assertThat(enemy.motionUpdates).as("ticked regardless").isEqualTo(1);
        assertThat(broadPhase.query(enemy.getBounds()))
            .as("and still reachable, which the kick and the tail both need")
            .containsExactly(enemy);

        // Execute - put down out there, and the window resumes its authority
        enemy.setCarried(false);
        broadPhase.clear();
        manager.update();

        // Verify
        assertThat(enemy.motionUpdates).isEqualTo(1);
        assertThat(broadPhase.query(enemy.getBounds())).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Awarding, which is where the score actually reaches the player
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A pending reward credits the player's score and raises a caption, in one pass")
    void aPendingRewardCreditsTheScore() {
        // The ROM splits a kill's award in two: the kill calls Score_PopUp, which only QUEUES the value
        // into Scores_Value, and Scores_GiveAndDraw (dasm prg007.asm:2154-2189) later walks that queue
        // giving the points and drawing the sprite. postCollision is that walk, so it has to do both -
        // for a long time it only did the second half and every enemy kill scored nothing.
        // Prepare
        final StubEnemy enemy = enemyAt(32);
        enemy.setPendingScoreReward(Reward.SCORE_400);
        manager.spawn(enemy);

        // Execute
        manager.postCollision();

        // Verify
        assertThat(playerData.getScore()).isEqualTo(400);
        assertThat(manager.getActiveScorePopups()).as("and the caption went up with it").hasSize(1);
        assertThat(enemy.getPendingScoreReward()).as("drained, so it pays exactly once").isNull();
    }

    @Test
    @DisplayName("The 1-up at the top of the kill ladder grants a life rather than silently paying zero")
    void theOneUpAtTheTopOfTheLadderGrantsALife() {
        // add(Reward.Data) and not addPoints: ONE_UP carries a life and no points, so crediting points
        // alone would swallow the reward a long chain exists to earn (prg007.asm:2135 - the 1-up's own
        // ScoreAdd entry is $00).
        // Prepare
        final StubEnemy enemy = enemyAt(32);
        enemy.setPendingScoreReward(Reward.ONE_UP);
        manager.spawn(enemy);
        final int livesBefore = playerData.getLives();

        // Execute
        manager.postCollision();

        // Verify
        assertThat(enemy.getPendingScoreReward()).isNull();
        assertThat(playerData.getLives()).isEqualTo(livesBefore + 1);
        assertThat(playerData.getScore()).as("a 1-up is worth no points").isZero();
    }

    @Test
    @DisplayName("An enemy with nothing pending credits nothing and raises nothing")
    void anEnemyWithNoRewardIsLeftAlone() {
        // Prepare
        final StubEnemy enemy = enemyAt(32);
        manager.spawn(enemy);

        // Execute
        manager.postCollision();

        // Verify
        assertThat(playerData.getScore()).isZero();
        assertThat(manager.getActiveScorePopups()).isEmpty();
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
     *
     * <p>Also carriable, so the activation-window exemption can be exercised without dragging the whole
     * Koopa Troopa in. Carriability is per state everywhere, which for a stub means a plain flag.
     */
    @Getter
    private static final class StubEnemy implements EnemyLevelObject, CarriableLevelObject {
        private final GameEngine gameEngine;
        private final PixelPosition position;
        private final FixedPointVelocity velocity = STILL;
        private final EnemyMode mode = NORMAL;
        private final boolean spawnedIntoScene = true;
        private final DimensionsPixels boundsPixels = new DimensionsPixels(16, 16);
        @Setter
        private boolean expired;
        @Setter
        private boolean hittable = true;
        @Setter
        private boolean carried;
        @Setter
        private Reward pendingScoreReward;
        private int motionUpdates;
        private int detachCount;

        private StubEnemy(final GameEngine gameEngine, final double pixelX) {
            this.gameEngine = gameEngine;
            this.position = new PixelPosition(pixelX, 0);
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
        public boolean isCarriable() {
            return !carried;
        }

        @Override
        public boolean isCarried() {
            return carried;
        }

        @Override
        public LevelScenePlayer getCarrier() {
            return null;
        }

        @Override
        public void onCarried(final LevelScenePlayer player) {
            carried = true;
        }

        @Override
        public void onKicked(
            final LevelScenePlayer player,
            final boolean toTheRight
        ) {
            carried = false;
        }

        @Override
        public void drawCarriedAt(final PixelPosition anchor) {
            // No sprite to place; the gate under test is the manager's, not any renderer's.
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

        /**
         * A constant, so the caption's placement does not drag a whole {@code LevelScene} in. The default
         * reads the scene's row count to flip the Y axis; nothing here asserts where a caption lands.
         */
        @Override
        public WorldOffset getCurrentWorldOffset() {
            return WorldOffset.of(0f, 0f, 0f);
        }
    }
}
