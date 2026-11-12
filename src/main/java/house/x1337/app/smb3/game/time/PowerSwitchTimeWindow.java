package house.x1337.app.smb3.game.time;

import house.x1337.app.smb3.annotation.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Singleton
public final class PowerSwitchTimeWindow {
    public static final int INITIAL_COUNT = 128;
    public static final int TICKS_PER_COUNT = 4;
    public static final int TOTAL_TICKS = INITIAL_COUNT * TICKS_PER_COUNT;

    private int count;
    private int ticksWithinCount;

    public void activate() {
        count = INITIAL_COUNT;
        ticksWithinCount = 0;
        log.debug("P-Switch active for {} ticks", TOTAL_TICKS);
    }

    /** @return whether the window ran out on this tick */
    public boolean tick() {
        if (count == 0) {
            return false;
        }
        ticksWithinCount++;
        if (ticksWithinCount < TICKS_PER_COUNT) {
            return false;
        }
        ticksWithinCount = 0;
        count--;
        if (count == 0) {
            log.debug("P-Switch expired");
            return true;
        }
        return false;
    }

    public boolean isActive() {
        return count > 0;
    }

    public int getTicksRemaining() {
        return count == 0
            ? 0
            : count * TICKS_PER_COUNT - ticksWithinCount;
    }

    public void reset() {
        count = 0;
        ticksWithinCount = 0;
    }
}
