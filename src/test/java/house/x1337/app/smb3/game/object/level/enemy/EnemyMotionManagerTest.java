package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.enemy.EnemyMode;
import house.x1337.app.smb3.game.collision.ActiveObjectGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.CarriableLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.enemy.base.EnemyLevelObject;
import house.x1337.app.smb3.game.object.level.enemy.motion.EnemyMotionManager;
import house.x1337.app.smb3.game.object.level.enemy.placement.EnemySpawner;
import house.x1337.app.smb3.game.object.level.reward.animation.ScorePopupAnimation;
import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
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

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.enemy.GoombaMode.NORMAL;
import static house.x1337.app.smb3.game.object.level.ActiveLevelObject.FALLEN_OUT_OF_THE_LEVEL_Y_PIXELS;
import static house.x1337.app.smb3.model.game.motion.FixedPointVelocity.STILL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the enemy manager does with its population each tick: it feeds the broadphase only the enemies
 * that are still live participants in collision, so a defeated one playing out its death animation is
 * intangible.
 *
 * <p>The ROM gets this structurally — a defeated object's state routine never reaches
 * {@code Player_HitEnemy} — so there is no flag to port, only the equivalent effect to pin.
 *
 * <p>It is also the one place that decides where an enemy may be (SMB3-3): a live enemy past the ROM's
 * deletion edges or fallen out of the level is deleted and its placement released
 * ({@code Object_DeleteOffScreen}), one above or below the screen carries on, and a defeated one plays
 * out its death and leaves its placement spent ({@code Object_SetDeadEmpty}). The spawner end of the same
 * lifecycle is {@code EnemySpawnerTest}.
 */
class EnemyMotionManagerTest {
    private static final int LEVEL_COLUMNS = 10_000;
    private static final int LEVEL_ROWS = 15;
    private static final double LEVEL_WIDTH_PIXELS = LEVEL_COLUMNS * (double) TILE_SPRITE_SIZE;
    private static final double SCREEN_WIDTH_PIXELS = 256;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private StubEnemyMotionManager manager;
    private EnemySpawner enemySpawner;
    private ActiveObjectGrid<ActiveLevelObject> broadPhase;
    private PlayerData playerData;
    private GameEngine gameEngine;

    @BeforeEach
    void prepare() {
        // A mock spawner behind a real grid: the grid forwards each placement decision to it, so what the
        // manager decided about an enemy's placement can be verified there.
        enemySpawner = mock(EnemySpawner.class);
        broadPhase = new ActiveObjectGrid<>(enemySpawner);

        final CameraState cameraState = mock(CameraState.class);
        // The screen: wide enough that every fixture enemy is well inside the deletion edges.
        when(cameraState.getActiveObjectRegion(anyInt()))
            .thenReturn(new AxisAlignedBoundingBox(-1000, -1000, 1000, 1000));

        playerData = new PlayerData();
        gameEngine = mock(GameEngine.class);
        when(gameEngine.getCameraState()).thenReturn(cameraState);
        when(gameEngine.getActiveObjectGrid()).thenReturn(broadPhase);
        when(gameEngine.getPlayerData()).thenReturn(playerData);

        // Wide enough that every fixture enemy, the carried one far out at x = 100 000 included, is inside
        // the level unless a test walks it out on purpose.
        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(LEVEL_COLUMNS, LEVEL_ROWS));
        when(gameEngine.getLevelScene()).thenReturn(levelScene);

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
    @DisplayName("An enemy its own logic expired is retired, and its placement stays spent")
    void expiredEnemyIsRemoved() {
        // Prepare - a squash run out, say: Object_SetDeadEmpty, which leaves Level_ObjectsSpawned set
        final StubEnemy enemy = enemyAt(32);
        enemy.setExpired(true);
        manager.spawn(enemy);

        // Execute
        manager.update();

        // Verify
        assertThat(manager.getActiveInstances()).isEmpty();
        assertThat(broadPhase.query(enemy.getBounds())).isEmpty();
        verify(enemySpawner).retirePlacementOf(enemy);
        verify(enemySpawner, never()).releasePlacementOf(any());
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
    // The spawn frame (ObjState_Initializing)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A freshly spawned enemy only initialises on its first tick, and moves from the next")
    void aFreshSpawnTakesAnInitFrame() {
        // Prepare - just built from its placement
        final StubEnemy enemy = enemyAt(32);
        enemy.setSpawnedIntoScene(false);
        enemy.setStepPixels(1);
        manager.spawn(enemy);

        // Execute - the spawn frame
        manager.update();

        // Verify - Level_PrepareNewObject + ObjInit_*, then the slot is done for the frame
        assertThat(enemy.getSpawnIntoSceneCount()).as("Initialised").isEqualTo(1);
        assertThat(enemy.motionUpdates).as("But not moved").isZero();
        assertThat(broadPhase.query(enemy.getBounds())).as("Nor offered to the collision pass").isEmpty();

        // Execute - the next frame, in its normal state
        broadPhase.clear();
        manager.update();

        // Verify
        assertThat(enemy.getSpawnIntoSceneCount()).as("Initialised once").isEqualTo(1);
        assertThat(enemy.motionUpdates).isEqualTo(1);
        assertThat(enemy.getPosition().x()).isCloseTo(33, within(1e-9));
        assertThat(broadPhase.query(enemy.getBounds())).containsExactly(enemy);
    }

    @Test
    @DisplayName("An enemy that adopted another's slot carries straight on, with no init frame")
    void anAdoptedSlotHasNoInitFrame() {
        // Prepare - the Para-Goomba's Goomba: Level_ObjectID rewritten, Objects_State left as it was
        final StubEnemy enemy = enemyAt(32);
        enemy.setSpawnedIntoScene(false);
        enemy.setAdoptedASlot(true);
        manager.spawn(enemy);

        // Execute
        manager.update();

        // Verify
        assertThat(enemy.getSpawnIntoSceneCount()).as("Its sprite was still built").isEqualTo(1);
        assertThat(enemy.motionUpdates).as("And it moved on the same tick").isEqualTo(1);
        assertThat(broadPhase.query(enemy.getBounds())).containsExactly(enemy);
    }

    // -------------------------------------------------------------------------
    // Leaving the screen (Object_DeleteOffScreen)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A live enemy beside the screen is deleted once and its placement released to spawn again")
    void anEnemyBesideTheScreenIsDeletedAndReleased() {
        // Prepare - well to the right of the fixture's active region, but still inside the level
        final StubEnemy enemy = enemyAt(100_000);
        manager.spawn(enemy);

        // Execute
        manager.update();
        manager.update();

        // Verify - thrown away, not frozen: the respawn comes from the placement, not from this instance
        assertThat(enemy.motionUpdates).as("Not ticked").isZero();
        assertThat(enemy.isExpired()).as("Through the ordinary expiry path").isTrue();
        assertThat(enemy.getDetachCount()).as("Its sprite was detached once").isEqualTo(1);
        assertThat(manager.getActiveInstances()).as("It left the population").isEmpty();
        assertThat(broadPhase.query(enemy.getBounds())).isEmpty();
        verify(enemySpawner, times(1)).releasePlacementOf(enemy);
        verify(enemySpawner, never()).retirePlacementOf(any());
    }

    @Test
    @DisplayName("The deletion edges are the ROM's: at 48 px left of the screen, and beyond 32 px right of it")
    void theDeletionEdgesAreTheRoms() {
        // Prepare - the fixture's screen spans x = -1000 to 1000; Object_DeleteOffScreen compares Objects_X
        final StubEnemy keptLeft = enemyAt(-1047);
        final StubEnemy deletedLeft = enemyAt(-1048);
        final StubEnemy keptRight = enemyAt(1032);
        final StubEnemy deletedRight = enemyAt(1033);
        manager.spawn(keptLeft);
        manager.spawn(deletedLeft);
        manager.spawn(keptRight);
        manager.spawn(deletedRight);

        // Execute
        manager.update();

        // Verify
        assertThat(manager.getActiveInstances()).containsExactly(keptLeft, keptRight);
        verify(enemySpawner).releasePlacementOf(deletedLeft);
        verify(enemySpawner).releasePlacementOf(deletedRight);
        verify(enemySpawner, never()).releasePlacementOf(keptLeft);
        verify(enemySpawner, never()).releasePlacementOf(keptRight);
    }

    @Test
    @DisplayName("An enemy above or below the screen but within its columns carries on as normal")
    void anEnemyAboveTheScreenKeepsRunning() {
        // Prepare - far above the screen (in a non-vertical level the ROM's test is X only)
        final StubEnemy enemy = enemyAt(32, -5000);
        manager.spawn(enemy);

        // Execute
        manager.update();

        // Verify - the ROM runs every occupied slot each frame, seen or not
        assertThat(enemy.motionUpdates).as("Ticked").isEqualTo(1);
        assertThat(manager.getActiveInstances()).as("Kept").containsExactly(enemy);
        assertThat(broadPhase.query(enemy.getBounds())).as("And collidable").containsExactly(enemy);
        verify(enemySpawner, never()).releasePlacementOf(any());
    }

    @Test
    @DisplayName("A defeated enemy plays its death out beside the screen rather than being deleted")
    void aDefeatedEnemyIsExemptFromDeletionOffScreen() {
        // Prepare - ObjState_Killed and ObjState_Squashed never call Object_DeleteOffScreen
        final StubEnemy enemy = enemyAt(100_000);
        enemy.setDefeated(true);
        enemy.setHittable(false);
        manager.spawn(enemy);

        // Execute
        manager.update();

        // Verify
        assertThat(enemy.motionUpdates).as("Its death animation still advances").isEqualTo(1);
        assertThat(manager.getActiveInstances()).containsExactly(enemy);
        verify(enemySpawner, never()).releasePlacementOf(any());
    }

    @Test
    @DisplayName("An object in the player's hands is never deleted off screen, but is once it is put down")
    void carriedEnemyIsExemptFromDeletionOffScreen() {
        // A carried object is not being simulated, it is being placed, from a player who is on screen by
        // definition (ObjState_Held never calls Object_DeleteOffScreen). Once released it is an ordinary
        // object again, which is why the second half of this asserts the deletion applies straight away.
        // Prepare - far beyond the fixture's screen, but carried
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

        // Execute - put down out there, and the off-screen deletion applies again
        enemy.setCarried(false);
        broadPhase.clear();
        manager.update();

        // Verify - beside the screen and live, so deleted, with its placement free to spawn it again
        assertThat(enemy.motionUpdates).isEqualTo(1);
        assertThat(broadPhase.query(enemy.getBounds())).isEmpty();
        assertThat(manager.getActiveInstances()).isEmpty();
        verify(enemySpawner).releasePlacementOf(enemy);
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
    // Leaving the level sideways (Object_DeleteOffScreen at a clamped camera)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("An enemy walking off the level's left edge is deleted once, 48 px out, and its placement released")
    void enemyPastTheLeftEdgeIsDeletedOnce() {
        // Prepare - the camera clamped at the level's start; one pixel short of x = -48, stepping left
        givenTheCameraClampedAtTheLeftEdge();
        final StubEnemy enemy = enemyAt(-47);
        enemy.setStepPixels(-1);
        manager.spawn(enemy);

        // Execute - the tick that steps it onto -48, then the one that finds it there
        manager.update();
        manager.update();

        // Verify
        assertThat(enemy.motionUpdates).as("Ticked once, then judged before its next tick").isEqualTo(1);
        assertThat(enemy.isExpired()).as("Through the ordinary expiry path").isTrue();
        assertThat(enemy.getDetachCount()).as("Its sprite was detached").isEqualTo(1);
        assertThat(manager.getActiveInstances()).as("And it left the population").isEmpty();

        // Execute - later ticks find nothing left to delete
        manager.update();
        manager.update();

        // Verify
        assertThat(enemy.getDetachCount()).as("Detached once, not once per tick").isEqualTo(1);
        verify(enemySpawner, times(1).description("Alive, so its placement can spawn it again"))
            .releasePlacementOf(enemy);
    }

    @Test
    @DisplayName("An enemy walking off the level's right edge is deleted once, past 32 px out")
    void enemyPastTheRightEdgeIsDeletedOnce() {
        // Prepare - the camera clamped at the level's end; exactly 32 px out, stepping right
        givenTheCameraClampedAtTheRightEdge();
        final StubEnemy enemy = enemyAt(LEVEL_WIDTH_PIXELS + 32);
        enemy.setStepPixels(1);
        manager.spawn(enemy);

        // Execute
        manager.update();
        manager.update();

        // Verify
        assertThat(enemy.motionUpdates).as("Still live at +32, gone at +33").isEqualTo(1);
        assertThat(enemy.isExpired()).isTrue();
        assertThat(enemy.getDetachCount()).as("Detached once").isEqualTo(1);
        assertThat(manager.getActiveInstances()).isEmpty();
        verify(enemySpawner, times(1)).releasePlacementOf(enemy);
    }

    @Test
    @DisplayName("An enemy wholly beyond the level's edge but inside the ROM's margin stays in play")
    void enemyJustBeyondTheEdgeIsKept() {
        // Prepare - both wholly out of the level, neither far enough out to be deleted
        givenTheCameraClampedAtTheLeftEdge();
        final StubEnemy leftward = enemyAt(-40);
        leftward.setStepPixels(-1);
        manager.spawn(leftward);

        // Execute
        manager.update();

        // Verify - the ROM has no level-edge rule of its own; only the screen-relative test applies
        assertThat(leftward.hasLeftTheLevelSideways()).as("Out of the level").isTrue();
        assertThat(leftward.motionUpdates).as("Still ticked").isEqualTo(1);
        assertThat(manager.getActiveInstances()).containsExactly(leftward);
        assertThat(leftward.isExpired()).isFalse();
        verify(enemySpawner, never()).releasePlacementOf(any());
    }

    // -------------------------------------------------------------------------
    // Falling out of the bottom of the level (Objects_YHi >= 2)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("A live enemy that reaches Y 512 is deleted once and its placement released")
    void aLiveEnemyBelowTheLevelIsReleased() {
        // Prepare - on screen horizontally, at the ROM's two-page line; a pixel higher is still in play
        final StubEnemy fallen = enemyAt(32, FALLEN_OUT_OF_THE_LEVEL_Y_PIXELS);
        final StubEnemy notYet = enemyAt(64, FALLEN_OUT_OF_THE_LEVEL_Y_PIXELS - 1);
        manager.spawn(fallen);
        manager.spawn(notYet);

        // Execute
        manager.update();
        manager.update();

        // Verify - Object_DeleteOffScreen's Objects_YHi >= 2 branch goes to Object_Delete, before the tick
        assertThat(fallen.motionUpdates).as("Deleted before it moved").isZero();
        assertThat(fallen.isExpired()).isTrue();
        assertThat(fallen.getDetachCount()).isEqualTo(1);
        assertThat(manager.getActiveInstances()).containsExactly(notYet);
        verify(enemySpawner, times(1)).releasePlacementOf(fallen);
        verify(enemySpawner, never()).retirePlacementOf(any());
    }

    @Test
    @DisplayName("A defeated enemy that reaches Y 512 is retired for good")
    void aDefeatedEnemyBelowTheLevelIsRetired() {
        // Prepare - a corpse falling, as from a tail strike or a shell
        final StubEnemy enemy = enemyAt(32, FALLEN_OUT_OF_THE_LEVEL_Y_PIXELS);
        enemy.setDefeated(true);
        enemy.setHittable(false);
        manager.spawn(enemy);

        // Execute
        manager.update();
        manager.update();

        // Verify - Object_FallAndDelete goes to Object_SetDeadEmpty, which keeps the spawned bit set
        assertThat(enemy.isExpired()).isTrue();
        assertThat(enemy.getDetachCount()).isEqualTo(1);
        assertThat(manager.getActiveInstances()).isEmpty();
        verify(enemySpawner, times(1)).retirePlacementOf(enemy);
        verify(enemySpawner, never()).releasePlacementOf(any());
    }

    @Test
    @DisplayName("In a level taller than the NES allows, the line drops to the level's own bottom edge")
    void aTallLevelMovesTheLineToItsBottom() {
        // Prepare - 40 rows is 640 px, so 512 px is still inside the level
        when(gameEngine.getLevelScene().getDimensions()).thenReturn(new LevelSceneDimensions(LEVEL_COLUMNS, 40));
        final StubEnemy inside = enemyAt(32, FALLEN_OUT_OF_THE_LEVEL_Y_PIXELS);
        final StubEnemy below = enemyAt(64, 40.0 * TILE_SPRITE_SIZE);
        manager.spawn(inside);
        manager.spawn(below);

        // Execute
        manager.update();

        // Verify
        assertThat(manager.getActiveInstances()).containsExactly(inside);
        verify(enemySpawner).releasePlacementOf(below);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** The screen as the camera clamps it at the level's start: x = 0 to 256. */
    private void givenTheCameraClampedAtTheLeftEdge() {
        when(gameEngine.getCameraState().getActiveObjectRegion(anyInt()))
            .thenReturn(new AxisAlignedBoundingBox(0, -1000, SCREEN_WIDTH_PIXELS, 1000));
    }

    /** The screen as the camera clamps it at the level's end. */
    private void givenTheCameraClampedAtTheRightEdge() {
        when(gameEngine.getCameraState().getActiveObjectRegion(anyInt())).thenReturn(
            new AxisAlignedBoundingBox(LEVEL_WIDTH_PIXELS - SCREEN_WIDTH_PIXELS, -1000, LEVEL_WIDTH_PIXELS, 1000)
        );
    }

    private StubEnemy enemyAt(final double pixelX) {
        return enemyAt(pixelX, 0);
    }

    private StubEnemy enemyAt(
        final double pixelX,
        final double pixelY
    ) {
        return new StubEnemy(gameEngine, pixelX, pixelY);
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
     * Koopa Troopa in. Carriability is per state everywhere, which for a stub means a plain flag. It moves
     * by a fixed {@code stepPixels} per tick (none by default), so it can be walked off the level.
     */
    @Getter
    private static final class StubEnemy implements EnemyLevelObject, CarriableLevelObject {
        private final GameEngine gameEngine;
        private final FixedPointVelocity velocity = STILL;
        private final EnemyMode mode = NORMAL;
        private final DimensionsPixels boundsPixels = new DimensionsPixels(16, 16);
        @Setter
        private boolean spawnedIntoScene = true;
        @Setter
        private boolean adoptedASlot;
        private int spawnIntoSceneCount;
        private PixelPosition position;
        @Setter
        private double stepPixels;
        @Setter
        private boolean expired;
        @Setter
        private boolean hittable = true;
        @Setter
        private boolean carried;
        @Setter
        private boolean defeated;
        @Setter
        private Reward pendingScoreReward;
        private int motionUpdates;
        private int detachCount;

        private StubEnemy(
            final GameEngine gameEngine,
            final double pixelX,
            final double pixelY
        ) {
            this.gameEngine = gameEngine;
            this.position = new PixelPosition(pixelX, pixelY);
        }

        @Override
        public void motionUpdate() {
            motionUpdates++;
            position = position.plusX(stepPixels);
        }

        @Override
        public boolean isHittable() {
            return hittable;
        }

        @Override
        public boolean isDefeated() {
            return defeated;
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
            // Counts the call rather than building a sprite: what is under test is when the manager makes it.
            spawnIntoSceneCount++;
            spawnedIntoScene = true;
        }

        @Override
        public boolean hasAdoptedASlot() {
            return adoptedASlot;
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
