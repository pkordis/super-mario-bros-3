package house.x1337.app.smb3.game.motion.pop;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.lang.Math.clamp;
import static java.lang.Math.floorDiv;

public final class PoppedOutCoinPopMotion implements PopMotion {
    public static final int GRAVITY = 2;
    public static final int LAUNCH_Y_VELOCITY = -40;
    public static final int REMOVAL_Y_VELOCITY = 32;

    private static final int DURATION_TICKS = (REMOVAL_Y_VELOCITY - LAUNCH_Y_VELOCITY + GRAVITY - 1) / GRAVITY;

    @Override
    public int durationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public int verticalOffsetAt(final int tick) {
        final int elapsed = clamp(tick, 0, DURATION_TICKS);
        final int travelled = LAUNCH_Y_VELOCITY * elapsed + GRAVITY * elapsed * (elapsed - 1) / 2;
        return -floorDiv(travelled, TILE_SPRITE_SIZE);
    }
}
