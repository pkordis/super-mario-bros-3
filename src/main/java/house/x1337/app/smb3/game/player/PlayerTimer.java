package house.x1337.app.smb3.game.player;

import house.x1337.app.smb3.enumeration.TimerPace;
import house.x1337.app.smb3.event.GameEventBusAware;
import house.x1337.app.smb3.model.event.PlayerTimerCleared;
import house.x1337.app.smb3.model.event.PlayerTimerExpired;
import house.x1337.app.smb3.model.event.PlayerTimerHurryUp;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.enumeration.TimerPace.REAL_TIME;

@Data
@Slf4j
public final class PlayerTimer implements GameEventBusAware {
    private static final int DEFAULT_INITIAL_TIME = 300;
    private static final int HURRY_UP_TIME = 100;

    private boolean active;
    private boolean paused;
    private int frameTick;
    private int initialTime = DEFAULT_INITIAL_TIME;
    private int time;
    private TimerPace pace = REAL_TIME;

    public void start() {
        if (!active) {
            if (time == 0) {
                time = initialTime;
            }
            active = true;
            paused = false;
            frameTick = 0;
        }
    }

    public void pause() {
        if (active) {
            active = false;
            paused = true;
        }
    }

    public boolean isShown() {
        return active || paused;
    }

    public void clear() {
        active = false;
        paused = false;
        time = 0;
        frameTick = 0;
        publish(new PlayerTimerCleared());
    }

    public void takeOff(final int seconds) {
        if (seconds < 0 || seconds > time) {
            throw new IllegalArgumentException("Cannot take " + seconds + " off " + time);
        }
        time -= seconds;
    }

    /**
     * Counts one simulation tick, and one timer step every {@link TimerPace#getTicksPerStep()} ticks of its pace: once
     * a second at {@link TimerPace#REAL_TIME}. The step that brings the time down to 100 publishes
     * {@link PlayerTimerHurryUp}, and the one that brings it to 0 publishes {@link PlayerTimerExpired} and stops the
     * timer. A run that starts at 100 or less, or time taken off by the time bonus, never publishes the hurry-up.
     *
     * @return whether a step went by on this tick
     */
    public boolean tick() {
        if (!active) {
            return false;
        }

        frameTick++;
        if (frameTick >= pace.getTicksPerStep()) {
            frameTick = 0;
            time--;

            if (time == HURRY_UP_TIME) {
                publish(new PlayerTimerHurryUp());
            }
            if (time <= 0) {
                time = 0;
                active = false;
                publish(new PlayerTimerExpired());
            }
            log.debug("Player time left: {}", time);
            return true;
        }
        return false;
    }
}
