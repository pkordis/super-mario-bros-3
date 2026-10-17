package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.enemy.animator.ParaGoombaAnimator;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The wing flap runs off {@code Objects_Var3}, which is deliberately <b>not</b> the body clock: the body
 * frame is {@code (Counter_1 >> 3) & 1} (an unconditional 8-tick toggle) while {@code Var3} advances
 * {@code +1} a frame, {@code +3} while rising out of the big hop, and is pinned to {@code 0} on the
 * ground between hop cycles (dasm prg004.asm:3043-3058 and :3084).
 */
class ParaGoombaWingClockTest {
    private static final int COLUMNS = 24;
    private static final int ROWS = 12;
    private static final int WALK_FRAME_COUNT = 2;

    /** {@code Objects_Var3 & $08} - 8 ticks raised, 8 ticks lowered. */
    private static final int WING_FLAP_HALF_PERIOD = 8;

    /** {@code Goomba.TICKS_PER_WALK_FRAME}, the body's own cadence. */
    private static final int TICKS_PER_BODY_FRAME = 8;

    private static final int BIG_HOP_YVEL = -0x30;

    private static final int FLOOR_ROW = 10;
    private static final int REST_ROW = FLOOR_ROW - 1;
    private static final double REST_ROW_PIXEL_Y = REST_ROW * (double) TILE_SPRITE_SIZE;
    private static final int START_COLUMN = 10;

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
        when(collisionGrid.isGroundSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isSolid(invocation.getArgument(0), invocation.getArgument(1)));
        when(collisionGrid.isWallOrCeilingSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isSolid(invocation.getArgument(0), invocation.getArgument(1)));
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.empty());

        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);

        final ParaGoombaAnimator animator = mock(ParaGoombaAnimator.class);
        when(animator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(ParaGoombaAnimator.class))
            .thenReturn(animator);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("The wings beat 8 ticks up and 8 ticks down off their own clock")
    void wingsBeatEveryEightTicks() {
        // Prepare - the first little hop is airborne for 17 ticks, so Var3 runs unpinned throughout
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();
        tick(paraGoomba, 1);
        assertThat(paraGoomba.getWingClock()).as("Pinned to 0 on the launching tick").isZero();
        assertThat(paraGoomba.isWingUp()).isFalse();

        // Execute & Verify - Var3 climbs 1 a tick through the hop, flipping the $08 bit every 8
        tick(paraGoomba, WING_FLAP_HALF_PERIOD - 1);
        assertThat(paraGoomba.getWingClock()).isEqualTo(7);
        assertThat(paraGoomba.isWingUp()).as("Still lowered on the 7th tick").isFalse();

        tick(paraGoomba, 1);
        assertThat(paraGoomba.getWingClock()).isEqualTo(WING_FLAP_HALF_PERIOD);
        assertThat(paraGoomba.isWingUp()).as("Raised as the $08 bit comes in").isTrue();

        tick(paraGoomba, WING_FLAP_HALF_PERIOD);
        assertThat(paraGoomba.getWingClock()).isEqualTo(WING_FLAP_HALF_PERIOD * 2);
        assertThat(paraGoomba.isWingUp()).as("Lowered again, for a 16-tick cycle").isFalse();
    }

    @Test
    @DisplayName("The wings beat three times faster than normal while rising out of the big hop")
    void wingsBeatFasterWhileRisingOutOfTheBigHop() {
        // Prepare - Var4 is back to 0 for the big hop, which is the condition the extra pair of INCs
        // hangs off; during the little hops Var4 is 1-3 and the clock runs at the plain rate.
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();
        tickUntilBigHopLaunch(paraGoomba, 300);
        assertThat(paraGoomba.getHopCounter()).as("Var4 reset by the big hop").isZero();

        // Execute - one tick into the climb
        final int clockAtLaunch = paraGoomba.getWingClock();
        tick(paraGoomba, 1);
        final int risingStep = paraGoomba.getWingClock() - clockAtLaunch;

        // Verify - +2 extra on top of the unconditional +1
        assertThat(paraGoomba.getYVelocityFixedPoint()).as("Still rising").isNegative();
        assertThat(risingStep).as("Var3 += 3 a tick on the way up").isEqualTo(3);

        // Execute - carry on to the top of the climb and past it
        tickUntilFalling(paraGoomba, 60);
        final int clockWhileFalling = paraGoomba.getWingClock();
        tick(paraGoomba, 1);

        // Verify - the bonus is gone the moment it stops rising
        assertThat(paraGoomba.getWingClock() - clockWhileFalling)
            .as("Back to a plain +1 on the way down")
            .isEqualTo(1);
    }

    @Test
    @DisplayName("The wing clock is independent of the body walk frame, which keeps its flat 8-tick beat")
    void wingClockIsIndependentOfTheBodyFrame() {
        // Prepare - count both over the big hop's climb, where the two rates genuinely diverge. Baking
        // body+wings into composed frames would make this impossible.
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();
        tickUntilBigHopLaunch(paraGoomba, 300);

        int wingFlips = 0;
        int bodyFlips = 0;
        boolean previousWingUp = paraGoomba.isWingUp();
        int previousBodyFrame = paraGoomba.getWalkFrameIndex();

        // Execute - the whole climb
        final int climbTicks = 24;
        for (int tick = 0; tick < climbTicks; tick++) {
            paraGoomba.motionUpdate();
            if (paraGoomba.isWingUp() != previousWingUp) {
                wingFlips++;
                previousWingUp = paraGoomba.isWingUp();
            }
            if (paraGoomba.getWalkFrameIndex() != previousBodyFrame) {
                bodyFlips++;
                previousBodyFrame = paraGoomba.getWalkFrameIndex();
            }
        }

        // Verify - the body keeps its one flip per 8 ticks while the wings run at triple that
        assertThat(bodyFlips)
            .as("The body frame is a flat 8-tick toggle throughout")
            .isEqualTo(climbTicks / TICKS_PER_BODY_FRAME);
        assertThat(wingFlips)
            .as("The wings beat far more often over the same ticks")
            .isGreaterThan(bodyFlips * 2);
    }

    @Test
    @DisplayName("The wings are held still and lowered while the big hop's cooldown runs down")
    void wingsAreStillWhileWaitingOutTheCooldown() {
        // Prepare - land the big hop, so Var4 is 0 and every grounded tick re-zeroes Var3
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();
        tickUntilBigHopLaunch(paraGoomba, 300);
        tickUntilGrounded(paraGoomba, 100);

        // Execute & Verify
        for (int tick = 0; tick < 20; tick++) {
            paraGoomba.motionUpdate();
            assertThat(paraGoomba.getHopCooldownTicks()).as("Still in the cooldown").isPositive();
            assertThat(paraGoomba.getPixelY()).as("Sitting on the floor").isEqualTo(REST_ROW_PIXEL_Y);
            assertThat(paraGoomba.getWingClock()).as("Var3 = 0 on grounded tick %d", tick + 1).isZero();
            assertThat(paraGoomba.isWingUp()).as("So the wings stay folded").isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private void tickUntilBigHopLaunch(final ParaGoomba paraGoomba, final int limit) {
        for (int tick = 0; tick < limit; tick++) {
            paraGoomba.motionUpdate();
            if (paraGoomba.isGroundContact() && paraGoomba.getYVelocityFixedPoint() == BIG_HOP_YVEL) {
                return;
            }
        }
        throw new AssertionError("No big hop within " + limit + " ticks");
    }

    private void tickUntilFalling(final ParaGoomba paraGoomba, final int limit) {
        for (int tick = 0; tick < limit; tick++) {
            paraGoomba.motionUpdate();
            if (paraGoomba.getYVelocityFixedPoint() >= 0) {
                return;
            }
        }
        throw new AssertionError("Never stopped rising within " + limit + " ticks");
    }

    private void tickUntilGrounded(final ParaGoomba paraGoomba, final int limit) {
        for (int tick = 0; tick < limit; tick++) {
            paraGoomba.motionUpdate();
            if (paraGoomba.isGrounded()) {
                return;
            }
        }
        throw new AssertionError("Never landed within " + limit + " ticks");
    }

    private ParaGoomba paraGoombaAt(final int column, final int row) {
        final ParaGoomba paraGoomba = new ParaGoomba(gameEngine, Offset.of(column, row));
        paraGoomba.init();
        return paraGoomba;
    }

    private void tick(final ParaGoomba paraGoomba, final int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            paraGoomba.motionUpdate();
        }
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
