package house.x1337.app.smb3.game.object.level.enemy.motion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.game.object.level.enemy.motion.EnemyKnockMotion.Direction.LEFT;
import static house.x1337.app.smb3.game.object.level.enemy.motion.EnemyKnockMotion.Direction.RIGHT;
import static java.lang.Math.min;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The shared killed-enemy death throw: {@code Enemy_Kill} (dasm prg000 @ PRG000_DBCF) launching the
 * enemy, then {@code ObjState_Killed} letting it fall clean off the level.
 */
class EnemyKnockMotionTest {
    private static final double TOLERANCE = 1e-9;

    /** {@code -$50} in 4.4 fixed point. */
    private static final double LAUNCH_PIXELS_PER_TICK = -0x50 / 16.0;

    /** {@code EnemyKill_XVels} is {@code ±$08}. */
    private static final double KNOCK_PIXELS_PER_TICK = 0x08 / 16.0;

    /** {@code OBJECT_FALLRATE} per tick. */
    private static final double GRAVITY_PIXELS_PER_TICK = 3 / 16.0;

    @Test
    @DisplayName("The first tick holds the living pose on the spot, so the flip lands on wham frame _1")
    void firstTickHoldsThePoseOnTheSpot() {
        // Prepare
        final EnemyKnockMotion motion = EnemyKnockMotion.strikeFrom(LEFT);
        assertThat(motion.isUpsideDown()).as("upright at the moment of impact").isFalse();

        // Execute
        motion.advance();

        // Verify - the flash is showing its first frame and the enemy has not budged
        assertThat(motion.getStepX()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(motion.getStepY()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(motion.isUpsideDown()).as("still the right way up").isFalse();
    }

    @Test
    @DisplayName("The second tick flips the enemy over and launches it up and away")
    void secondTickFlipsAndLaunches() {
        // Prepare - struck from the left, so it is knocked to the right
        final EnemyKnockMotion motion = EnemyKnockMotion.strikeFrom(LEFT);
        motion.advance();

        // Execute
        motion.advance();

        // Verify
        assertThat(motion.isUpsideDown()).as("Objects_FlipBits |= SPR_VFLIP").isTrue();
        assertThat(motion.getStepY())
            .as("launched at -$50/16 px per tick")
            .isCloseTo(LAUNCH_PIXELS_PER_TICK, within(TOLERANCE));
        assertThat(motion.getStepX())
            .as("knocked away from the attacker on its left")
            .isCloseTo(KNOCK_PIXELS_PER_TICK, within(TOLERANCE));
    }

    @Test
    @DisplayName("The knock is always away from the attacker")
    void knockIsAwayFromTheAttacker() {
        // Prepare & Execute - struck from the right this time
        final EnemyKnockMotion motion = EnemyKnockMotion.strikeFrom(RIGHT);
        motion.advance();
        motion.advance();

        // Verify
        assertThat(motion.getStepX()).isCloseTo(-KNOCK_PIXELS_PER_TICK, within(TOLERANCE));
    }

    @Test
    @DisplayName("Horizontal drift never changes: there is no friction on a dead enemy")
    void horizontalDriftIsConstant() {
        // Prepare
        final EnemyKnockMotion motion = EnemyKnockMotion.strikeFrom(LEFT);
        motion.advance();

        // Execute & Verify - Object_Move only ever touches Y for a killed object
        for (int tick = 0; tick < 40; tick++) {
            motion.advance();
            assertThat(motion.getStepX())
                .as("tick %d", tick)
                .isCloseTo(KNOCK_PIXELS_PER_TICK, within(TOLERANCE));
        }
    }

    @Test
    @DisplayName("Gravity decays the launch tick by tick until the enemy is falling")
    void gravityTurnsTheLaunchIntoAFall() {
        // Prepare
        final EnemyKnockMotion motion = EnemyKnockMotion.strikeFrom(LEFT);
        motion.advance();
        motion.advance();

        // Execute & Verify - each tick is exactly OBJECT_FALLRATE slower than the last
        double previousStepY = motion.getStepY();
        boolean everDescended = false;
        for (int tick = 0; tick < 40; tick++) {
            motion.advance();
            assertThat(motion.getStepY() - previousStepY)
                .as("gravity applied on tick %d", tick)
                .isCloseTo(GRAVITY_PIXELS_PER_TICK, within(TOLERANCE));
            previousStepY = motion.getStepY();
            everDescended |= motion.getStepY() > 0;
        }
        assertThat(everDescended).as("the arc turns over and the enemy falls").isTrue();
    }

    @Test
    @DisplayName("The fall is capped at terminal velocity")
    void fallSpeedCapsAtTerminalVelocity() {
        // Prepare
        final EnemyKnockMotion motion = EnemyKnockMotion.strikeFrom(LEFT);

        // Execute - long enough to be well past the cap
        for (int tick = 0; tick < 120; tick++) {
            motion.advance();
        }

        // Verify - OBJECT_MAXFALL = $40, i.e. 4 px per tick
        assertThat(motion.getStepY()).isCloseTo(0x40 / 16.0, within(TOLERANCE));
        assertThat(motion.getYVelocityFixedPoint()).isEqualTo(0x40);
    }

    @Test
    @DisplayName("The arc rises before it falls, and ends up well below where it started")
    void arcRisesThenFallsPastTheStart() {
        // Prepare - integrate the motion the way an enemy applies it
        final EnemyKnockMotion motion = EnemyKnockMotion.strikeFrom(LEFT);
        double y = 0;
        double highest = 0;

        // Execute
        for (int tick = 0; tick < 80; tick++) {
            motion.advance();
            y += motion.getStepY();
            highest = min(highest, y);
        }

        // Verify - Y grows downward, so a negative peak is upward travel
        assertThat(highest).as("thrown upward first").isLessThan(-8.0);
        assertThat(y).as("and ends far below the impact point, heading off the level").isGreaterThan(64.0);
    }

    @Test
    @DisplayName("Staying upside down is permanent once launched")
    void staysUpsideDownForTheWholeFall() {
        final EnemyKnockMotion motion = EnemyKnockMotion.strikeFrom(LEFT);
        motion.advance();

        for (int tick = 0; tick < 80; tick++) {
            motion.advance();
            assertThat(motion.isUpsideDown()).as("tick %d", tick).isTrue();
        }
    }
}
