package house.x1337.app.smb3.game.player;

import house.x1337.app.smb3.event.GameEventBusAware;
import house.x1337.app.smb3.model.event.PlayerTimerCleared;
import house.x1337.app.smb3.model.event.PlayerTimerExpired;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.GameConstants.TARGET_FPS;

/**
 * Manages the level countdown timer, ticking once per second from an initial
 * value (default 300) down to zero.
 *
 * <p>Mirrors the NES status bar timer from prg026.asm
 * ({@code StatusBar_Fill_Time}): the timer decrements once per second while
 * active and triggers a time-out death when it reaches zero.
 *
 * <p>The timer operates in frame-based ticks — every {@link #TARGET_FPS}
 * calls to {@link #tick()} constitutes one second, at which point the
 * displayed timer value decrements by one.
 */
@Slf4j
public final class PlayerTimer implements GameEventBusAware {

    /** Default initial timer value (seconds) matching SMB3. */
    private static final int DEFAULT_INITIAL_TIME = 300;

    /** The initial time this timer was started with. */
    @Getter
    @Setter
    private int initialTime = DEFAULT_INITIAL_TIME;

    /** Current timer value (0–999). */
    @Getter
    private int time;

    /** Whether the timer is actively counting down. */
    @Getter
    private boolean active;

    /** Frame counter — counts up to TARGET_FPS to produce a 1-second tick. */
    private int frameTick;

    /**
     * Starts (or restarts) the countdown timer from the configured initial
     * time. If the timer was paused, it resumes from the current value
     * instead. To restart from the initial value after a pause, call
     * {@link #clear()} first, then {@code start()}.
     */
    public void start() {
        if (!active) {
            if (time == 0) {
                time = initialTime;
            }
            active = true;
            frameTick = 0;
        }
    }

    /**
     * Pauses the timer, freezing the current countdown value. The timer can
     * be resumed by calling {@link #start()}.
     */
    public void pause() {
        active = false;
    }

    /**
     * Clears the timer to zero and deactivates it. Publishes a
     * {@link PlayerTimerCleared} event to notify subscribers that the timer
     * was forcibly reset (as opposed to expiring naturally).
     */
    public void clear() {
        active = false;
        time = 0;
        frameTick = 0;
        publish(new PlayerTimerCleared());
    }

    /**
     * Advances the timer by one frame. Must be called once per game frame
     * (at {@link #TARGET_FPS} fps). When {@link #TARGET_FPS} frames have
     * elapsed, the displayed timer decrements by one. When the timer
     * reaches zero, a {@link PlayerTimerExpired} event is published and
     * the timer deactivates.
     */
    public boolean tick() {
        if (!active) {
            return false;
        }

        frameTick++;
        if (frameTick >= TARGET_FPS) {
            frameTick = 0;
            time--;

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
