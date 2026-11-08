package house.x1337.app.smb3.game.motion.pop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

import static java.lang.Math.clamp;
import static lombok.AccessLevel.PACKAGE;

@Data
@Accessors(fluent = true)
@AllArgsConstructor(access = PACKAGE)
public final class ScoreRisePopMotion implements PopMotion {
    public static final int SCORE_COUNTER = 48;
    public static final int DIGITS_BELOW_SPRITE_TOP = 8;
    public static final int UPDATES_PER_RISE_STAGE = 16;
    private static final int[] RISE_COUNTER_MASKS = {3, 1, 0, 0};

    private final int firstUpdateFrame;
    private final int maxRises;

    @Override
    public int durationTicks() {
        return SCORE_COUNTER + 1;
    }

    @Override
    public int verticalOffsetAt(final int tick) {
        return risenAfter(clamp(tick, 0, SCORE_COUNTER)) - DIGITS_BELOW_SPRITE_TOP;
    }

    private int risenAfter(final int updates) {
        int risen = 0;
        for (int update = 1; update <= updates && risen < maxRises; update++) {
            final int counter = SCORE_COUNTER - update;
            final int frame = firstUpdateFrame + update - 1;
            if ((frame & RISE_COUNTER_MASKS[counter / UPDATES_PER_RISE_STAGE]) == 0) {
                risen++;
            }
        }
        return risen;
    }
}
