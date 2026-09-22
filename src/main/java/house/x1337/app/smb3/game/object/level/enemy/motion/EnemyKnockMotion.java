package house.x1337.app.smb3.game.object.level.enemy.motion;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.lang.Math.min;

@Getter
@RequiredArgsConstructor
public final class EnemyKnockMotion {
    private static final int LAUNCH_Y_VELOCITY_FIXED_POINT = -80;
    private static final int KNOCK_X_VELOCITY_FIXED_POINT = 8;
    private static final int GRAVITY_FIXED_POINT = 3;
    private static final int MAX_FALL_FIXED_POINT = 64;
    private static final double FIXED_POINT_VELOCITY_TO_PIXELS = 1.0 / TILE_SPRITE_SIZE;

    private final Direction knockDirection;
    private int launchDelayTicks = 1;
    private boolean upsideDown;
    private int xVelocityFixedPoint;
    private int yVelocityFixedPoint;
    private double stepX;
    private double stepY;

    public static EnemyKnockMotion strikeFrom(final Direction knockDirection) {
        return new EnemyKnockMotion(knockDirection);
    }

    public void advance() {
        if (launchDelayTicks > 0) {
            // Still on the spot it was struck, right way up: the flash has only just appeared.
            launchDelayTicks--;
            stepX = 0;
            stepY = 0;
            return;
        }
        if (!upsideDown) {
            upsideDown = true;
            yVelocityFixedPoint = LAUNCH_Y_VELOCITY_FIXED_POINT;
            xVelocityFixedPoint = knockDirection.getValue() * KNOCK_X_VELOCITY_FIXED_POINT;
        }
        stepX = xVelocityFixedPoint * FIXED_POINT_VELOCITY_TO_PIXELS;
        stepY = yVelocityFixedPoint * FIXED_POINT_VELOCITY_TO_PIXELS;
        // Object_Move applies gravity after the move, exactly as the live walk does.
        yVelocityFixedPoint = min(yVelocityFixedPoint + GRAVITY_FIXED_POINT, MAX_FALL_FIXED_POINT);
    }

    @Getter
    @RequiredArgsConstructor
    public enum Direction {
        LEFT(1),
        RIGHT(-1);

        private final int value;
    }
}
