package house.x1337.app.smb3.game.motion.pop;

import static java.lang.Math.clamp;

public final class BlockCoinPopMotion implements PopMotion {
    public static final int LAUNCH_Y_VELOCITY = -5;
    public static final int REMOVAL_Y_VELOCITY = 5;
    public static final int VELOCITY_STEP_UPDATES = 4;
    public static final int LAUNCH_COUNTER = 1;
    public static final int SPIN_PHASE_TICKS = LAUNCH_COUNTER;

    public static final int REMOVAL_UPDATE =
        (REMOVAL_Y_VELOCITY - LAUNCH_Y_VELOCITY) * VELOCITY_STEP_UPDATES - LAUNCH_COUNTER;

    @Override
    public int durationTicks() {
        return REMOVAL_UPDATE;
    }

    @Override
    public int verticalOffsetAt(final int tick) {
        final int updates = clamp(tick, 0, REMOVAL_UPDATE);
        int counter = LAUNCH_COUNTER;
        int yVelocity = LAUNCH_Y_VELOCITY;
        int risen = 0;
        for (int update = 0; update < updates; update++) {
            counter++;
            risen -= yVelocity;
            if (counter % VELOCITY_STEP_UPDATES == 0) {
                yVelocity++;
            }
        }
        return risen;
    }
}
