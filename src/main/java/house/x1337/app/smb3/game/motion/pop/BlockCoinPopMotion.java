package house.x1337.app.smb3.game.motion.pop;

import static java.lang.Math.clamp;

public final class BlockCoinPopMotion implements PopMotion {
    static final int HEAD_PROBE_DEPTH = 14;
    static final int SPRITE_LINE_DELAY = 1;
    public static final int LAUNCH_COUNTER = 1;
    public static final int LAUNCH_HEIGHT_ABOVE_HEAD = 24;
    public static final int LAUNCH_Y_VELOCITY = -5;
    public static final int REMOVAL_Y_VELOCITY = 5;
    public static final int SPIN_PHASE_TICKS = LAUNCH_COUNTER + 1;
    public static final int VELOCITY_STEP_UPDATES = 4;
    public static final int REMOVAL_UPDATE = (REMOVAL_Y_VELOCITY - LAUNCH_Y_VELOCITY) *
        VELOCITY_STEP_UPDATES - LAUNCH_COUNTER;
    public static final int LAUNCH_HEIGHT = LAUNCH_HEIGHT_ABOVE_HEAD - HEAD_PROBE_DEPTH - SPRITE_LINE_DELAY;
    private static final int DURATION_TICKS = REMOVAL_UPDATE - 1;

    @Override
    public int durationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public int verticalOffsetAt(final int tick) {
        return LAUNCH_HEIGHT + risenAfter(clamp(tick, 0, DURATION_TICKS) + 1);
    }

    private static int risenAfter(final int updates) {
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
