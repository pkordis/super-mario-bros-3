package house.x1337.app.smb3.game.object.level.enemy.motion;

import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.model.game.motion.FixedPointVelocity.STILL;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class EnemyKnockMotion {
    public static final int BUMP_LAUNCH_Y_VELOCITY_FIXED_POINT = -48;
    public static final int GRAVITY_FIXED_POINT = 3;
    public static final int KNOCK_X_VELOCITY_FIXED_POINT = 8;
    public static final int LAUNCH_Y_VELOCITY_FIXED_POINT = -80;
    public static final int MAX_FALL_FIXED_POINT = 64;
    public static final int SHELL_KILL_LAUNCH_Y_VELOCITY_FIXED_POINT = -48;

    private final FixedPointVelocity knockLaunchVelocity;
    private FixedPointVelocity velocity = STILL;
    private FixedPointVelocity stepVelocity = STILL;
    private int launchDelayTicks = 1;
    private boolean upsideDown;

    /**
     * The death a tail strike or a bouncer deals.
     *
     * @param knockDirection the side the strike came from
     * @return the motion to tick
     */
    public static EnemyKnockMotion strikeFrom(final Direction knockDirection) {
        final FixedPointVelocity strikeVelocity = new FixedPointVelocity(
            knockDirection.getValue() * KNOCK_X_VELOCITY_FIXED_POINT,
            LAUNCH_Y_VELOCITY_FIXED_POINT
        );
        return new EnemyKnockMotion(strikeVelocity);
    }

    /**
     * The death a kicked or carried object deals.
     *
     * @param knockXVelocityFixedPoint the signed X velocity to die with, in 4.4 fixed point
     * @return the motion to tick
     */
    public static EnemyKnockMotion shellKill(final int knockXVelocityFixedPoint) {
        final FixedPointVelocity shellKillVelocity = new FixedPointVelocity(
            knockXVelocityFixedPoint,
            SHELL_KILL_LAUNCH_Y_VELOCITY_FIXED_POINT
        );
        return new EnemyKnockMotion(shellKillVelocity);
    }

    public void advance() {
        if (launchDelayTicks > 0) {
            // Still on the spot it was struck, right way up: the flash has only just appeared.
            launchDelayTicks--;
            stepVelocity = STILL;
            return;
        }
        if (!upsideDown) {
            upsideDown = true;
            velocity = knockLaunchVelocity;
        }
        stepVelocity = velocity;
        velocity = velocity.falling(GRAVITY_FIXED_POINT, MAX_FALL_FIXED_POINT);
    }

    @Getter
    @RequiredArgsConstructor
    public enum Direction {
        LEFT(1),
        RIGHT(-1);

        private final int value;
    }
}
