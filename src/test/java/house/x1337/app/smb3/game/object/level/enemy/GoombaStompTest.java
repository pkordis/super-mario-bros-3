package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.enemy.animator.GoombaAnimator;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.PLAYER_STOMP_BOUNCE_YVEL;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The Goomba's stomp response, as the ROM defines it: flatten for 16 frames while intangible, hand the
 * player a {@code -$40} bounce, and leave 100 points behind.
 *
 * <p>Chain in the dasm: {@code Player_HitEnemy} @ PRG000_D2B4 sets the bounce and awards
 * {@code Score_Get100PlusPts}; {@code OA3_SQUASH} routes the Goomba through {@code ObjState_Shelled}
 * ({@code Objects_Timer3 = $10}) into {@code ObjState_Squashed}, which runs the timer down and then marks
 * it dead/empty.
 */
class GoombaStompTest {
    private static final double TOLERANCE = 1e-9;
    private static final int COLUMNS = 12;
    private static final int ROWS = 8;
    private static final int WALK_FRAME_COUNT = 2;

    /** {@code Objects_Timer3 = $10}, decremented every tick because it is below the {@code $60} throttle. */
    private static final int SQUISH_FRAMES = 16;

    private static final int FLOOR_ROW = 6;
    private static final int WALK_ROW = FLOOR_ROW - 1;
    private static final double WALK_ROW_PIXEL_Y = WALK_ROW * (double) TILE_SPRITE_SIZE;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private GameEngine gameEngine;
    private boolean[][] solids;

    @BeforeEach
    void prepare() {
        solids = new boolean[ROWS][COLUMNS];
        for (int column = 0; column < COLUMNS; column++) {
            solids[FLOOR_ROW][column] = true;
        }

        final StaticEnvironmentCollisionGrid collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
        when(collisionGrid.isSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isSolid(invocation.getArgument(0), invocation.getArgument(1)));
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.empty());

        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);

        final GoombaAnimator animator = mock(GoombaAnimator.class);
        when(animator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(GoombaAnimator.class))
            .thenReturn(animator);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A stomp flattens the Goomba for exactly 16 frames, then it vanishes")
    void squishLastsSixteenFrames() {
        // Prepare
        final Goomba goomba = goombaAt(5, WALK_ROW);
        goomba.faceClosestPlayer();
        assertThat(goomba.isSquished()).isFalse();

        // Execute
        goomba.onCollisionFromAbove(descendingPlayer());

        // Verify - flattened at once, and still present right up to the last frame
        assertThat(goomba.isSquished()).isTrue();
        for (int frame = 0; frame < SQUISH_FRAMES - 1; frame++) {
            goomba.motionUpdate();
            assertThat(goomba.isExpired())
                .as("still on screen after %d of %d squish frames", frame + 1, SQUISH_FRAMES)
                .isFalse();
        }

        // Execute - the 16th frame runs the timer out
        goomba.motionUpdate();

        // Verify - expired, so its manager retires it
        assertThat(goomba.isExpired()).as("gone after the 16th frame").isTrue();
    }

    @Test
    @DisplayName("A flattened Goomba stops walking but still obeys gravity")
    void squishedGoombaStopsWalkingAndFalls() {
        // Prepare - stomped in mid-air, one row above the floor
        final Goomba goomba = goombaAt(5, WALK_ROW - 1);
        goomba.faceClosestPlayer();
        final double startX = goomba.getPixelX();
        final double startY = goomba.getPixelY();

        // Execute
        goomba.onCollisionFromAbove(descendingPlayer());
        for (int frame = 0; frame < SQUISH_FRAMES - 1; frame++) {
            goomba.motionUpdate();
        }

        // Verify - ObjState_Squashed clears XVel but still calls Object_Move, so it drops straight down
        assertThat(goomba.getPixelX()).as("horizontal movement halted").isCloseTo(startX, within(TOLERANCE));
        assertThat(goomba.getPixelY()).as("still falling under gravity").isGreaterThan(startY);
        assertThat(goomba.getPixelY())
            .as("never sinks past the floor it is heading for")
            .isLessThanOrEqualTo(WALK_ROW_PIXEL_Y);
    }

    @Test
    @DisplayName("A Goomba stomped on the ground stays flat on it, without drifting")
    void squishedGoombaOnGroundStaysPut() {
        // Prepare - the ordinary case: stomped mid-walk, already resting on the floor
        final Goomba goomba = goombaAt(5, WALK_ROW);
        goomba.faceClosestPlayer();
        goomba.motionUpdate();
        final double walkedToX = goomba.getPixelX();

        // Execute
        goomba.onCollisionFromAbove(descendingPlayer());
        for (int frame = 0; frame < SQUISH_FRAMES - 1; frame++) {
            goomba.motionUpdate();
        }

        // Verify - Object_HitGround keeps re-aligning the feet, and it never takes another step
        assertThat(goomba.getPixelX()).as("frozen where it was stomped").isCloseTo(walkedToX, within(TOLERANCE));
        assertThat(goomba.getPixelY()).isCloseTo(WALK_ROW_PIXEL_Y, within(TOLERANCE));
        assertThat(goomba.isGrounded()).isTrue();
    }

    @Test
    @DisplayName("The walk cycle stops advancing while flattened")
    void squishedGoombaStopsAnimatingItsWalk() {
        // Prepare - the flattened frame replaces the walk frames outright (ObjState_Squashed draws frame 3)
        final Goomba goomba = goombaAt(5, WALK_ROW);
        goomba.faceClosestPlayer();
        goomba.onCollisionFromAbove(descendingPlayer());
        final int frameIndexAtStomp = goomba.getWalkFrameIndex();

        // Execute - long enough to have flipped the walk frame twice had it kept cycling
        for (int frame = 0; frame < SQUISH_FRAMES - 1; frame++) {
            goomba.motionUpdate();
        }

        // Verify
        assertThat(goomba.getWalkFrameIndex()).isEqualTo(frameIndexAtStomp);
    }

    @Test
    @DisplayName("A flattened Goomba is intangible, so nothing can reach it again")
    void squishedGoombaIsNotHittable() {
        // Prepare
        final Goomba goomba = goombaAt(5, WALK_ROW);
        assertThat(goomba.isHittable()).as("a live Goomba takes part in collision").isTrue();

        // Execute
        goomba.onCollisionFromAbove(descendingPlayer());

        // Verify - its manager will keep it out of the broadphase for the rest of the squish
        assertThat(goomba.isHittable()).isFalse();
    }

    @Test
    @DisplayName("A stomp bounces the player upward at the ROM's -$40")
    void stompBouncesThePlayer() {
        // Prepare - a player descending onto the Goomba
        final Goomba goomba = goombaAt(5, WALK_ROW);
        final LevelScenePlayer player = descendingPlayer();

        // Execute
        goomba.onCollisionFromAbove(player);

        // Verify - -$40/16 px/frame, whatever the player was doing. There is no A-specific value in the
        // ROM: holding A only swaps the gravity that follows (GRAVITY_SLOW while DY < -2), which this
        // bounce starts well beyond, so it carries much further than without.
        assertThat(player.getPosition().getDY())
            .isCloseTo(PLAYER_STOMP_BOUNCE_YVEL, within(TOLERANCE));
        assertThat(PLAYER_STOMP_BOUNCE_YVEL).as("-$40 in 4.4 fixed point").isCloseTo(-4.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("A stomp queues a 100-point caption, claimable exactly once")
    void stompQueuesOneHundredPoints() {
        // Prepare
        final Goomba goomba = goombaAt(5, WALK_ROW);
        assertThat(goomba.getPendingScoreReward()).as("nothing owed before the stomp").isNull();

        // Execute
        goomba.onCollisionFromAbove(descendingPlayer());

        // Verify - Score_Get100PlusPts with a zero kill tally is the base 100
        assertThat(goomba.getPendingScoreReward()).isEqualTo(SCORE_100);

        // Execute - the manager takes it in postCollision
        goomba.setPendingScoreReward(null);
        goomba.motionUpdate();

        // Verify - ticking never re-queues it
        assertThat(goomba.getPendingScoreReward()).as("awarded once, not once per frame").isNull();
    }

    @Test
    @DisplayName("Stomping an already-flattened Goomba does not restart it or pay out again")
    void secondStompIsIgnored() {
        // Prepare - the broadphase should make this unreachable, but the guard must hold regardless
        final Goomba goomba = goombaAt(5, WALK_ROW);
        goomba.onCollisionFromAbove(descendingPlayer());
        goomba.setPendingScoreReward(null);
        for (int frame = 0; frame < 10; frame++) {
            goomba.motionUpdate();
        }

        // Execute
        final LevelScenePlayer secondPlayer = descendingPlayer();
        goomba.onCollisionFromAbove(secondPlayer);

        // Verify - no second score, no second bounce, and the timer was not reset
        assertThat(goomba.getPendingScoreReward()).as("paid once").isNull();
        assertThat(secondPlayer.getPosition().getDY()).as("no second bounce").isCloseTo(2.0, within(TOLERANCE));
        for (int frame = 0; frame < 6; frame++) {
            goomba.motionUpdate();
        }
        assertThat(goomba.isExpired()).as("the original 16 frames still governed the lifetime").isTrue();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private Goomba goombaAt(final int column, final int row) {
        final Goomba goomba = new Goomba(gameEngine, Offset.of(column, row));
        goomba.init();
        return goomba;
    }

    /** A player falling onto the Goomba; only its position is read by the stomp response. */
    private static LevelScenePlayer descendingPlayer() {
        final PlayerPosition position = new PlayerPosition();
        position.setDY(2.0);
        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        return player;
    }

    /** Mirrors {@code StaticEnvironmentCollisionGrid#isSolidTile}: outside the level is solid, sky is not. */
    private boolean isSolid(final int column, final int row) {
        if (row < 0) {
            return false;
        }
        if (row >= ROWS || column < 0 || column >= COLUMNS) {
            return true;
        }
        return solids[row][column];
    }
}
