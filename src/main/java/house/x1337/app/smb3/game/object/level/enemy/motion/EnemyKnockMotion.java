package house.x1337.app.smb3.game.object.level.enemy.motion;

import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.model.game.motion.FixedPointVelocity.STILL;

@Getter
@RequiredArgsConstructor
public final class EnemyKnockMotion {
    private static final int LAUNCH_Y_VELOCITY_FIXED_POINT = -80;
    private static final int KNOCK_X_VELOCITY_FIXED_POINT = 8;
    private static final int GRAVITY_FIXED_POINT = 3;
    private static final int MAX_FALL_FIXED_POINT = 64;

    private final Direction knockDirection;
    private int launchDelayTicks = 1;
    private boolean upsideDown;
    private FixedPointVelocity velocity = STILL;

    /**
     * The velocity the owner should move by this frame. Distinct from {@link #getVelocity()} because
     * {@code Object_Move} applies gravity <em>after</em> the move, so the step is last frame's
     * pre-gravity snapshot rather than the live value.
     */
    private FixedPointVelocity stepVelocity = STILL;

    public static EnemyKnockMotion strikeFrom(final Direction knockDirection) {
        return new EnemyKnockMotion(knockDirection);
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
            velocity = new FixedPointVelocity(
                knockDirection.getValue() * KNOCK_X_VELOCITY_FIXED_POINT,
                LAUNCH_Y_VELOCITY_FIXED_POINT
            );
        }
        stepVelocity = velocity;
        // Object_Move applies gravity after the move, exactly as the live walk does.
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
