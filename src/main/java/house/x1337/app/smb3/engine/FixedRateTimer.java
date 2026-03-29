package house.x1337.app.smb3.engine;

import com.jme3.system.Timer;
import house.x1337.app.smb3.GameConstants;
import house.x1337.app.smb3.engine.core.GameEngine;

import java.util.concurrent.locks.LockSupport;

/**
 * A jMonkeyEngine {@link Timer} that drives the game loop at a precise,
 * fixed rate of {@link GameConstants#TARGET_FPS} ticks per second.
 *
 * <p>Unlike the default {@code NanoTimer}, which measures actual wall-clock
 * time between frames and lets jitter bleed into physics and movement, this
 * timer always reports {@code tpf = 1 / TARGET_FPS} regardless of how long
 * the preceding frame actually took. The rendering thread is parked using
 * {@link LockSupport#parkNanos} for the bulk of each interval and busy-spins
 * only for the final {@value #SPIN_THRESHOLD_NANOS} ns, giving sub-millisecond
 * scheduling accuracy without burning a full CPU core.
 *
 * <p>jMonkeyEngine's built-in {@code Sync} throttler must be disabled
 * ({@code AppSettings.setFrameRate(-1)}) to prevent double-sleeping;
 * {@link GameEngine#start()} handles this automatically.
 */
public final class FixedRateTimer extends Timer {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    /** Nanoseconds per tick at {@link GameConstants#TARGET_FPS}. */
    private static final long TICK_NANOS = NANOS_PER_SECOND / GameConstants.TARGET_FPS;

    /** Fixed time-per-frame always returned to callers. */
    private static final float FIXED_TPF = (float) GameConstants.FRAME_TIME_SECONDS;

    /**
     * When fewer than this many nanoseconds remain until the next tick, stop
     * parking and busy-spin for the final stretch to maximise precision.
     */
    private static final long SPIN_THRESHOLD_NANOS = 200_000L; // 0.2 ms

    /** Monotonic tick counter — drives {@link #getTime()} / {@link #getTimeInSeconds()}. */
    private long tickCount = 0L;

    /**
     * The {@link System#nanoTime()} value at which the <em>current</em> tick
     * should fire. Advanced by exactly {@link #TICK_NANOS} at the end of each
     * {@link #update()}.
     */
    private long nextTickNanos = 0L;

    /** {@code true} once {@link #reset()} has been called for the first time. */
    private boolean started = false;

    // -------------------------------------------------------------------------
    // Timer contract
    // -------------------------------------------------------------------------

    @Override
    public long getResolution() {
        return NANOS_PER_SECOND;
    }

    /**
     * Returns the logical elapsed time expressed in nanoseconds — i.e. the
     * tick count multiplied by {@link #TICK_NANOS}. Does <em>not</em> query
     * {@link System#nanoTime()}; the value only advances on {@link #update()}.
     */
    @Override
    public long getTime() {
        return tickCount * TICK_NANOS;
    }

    /** Returns the logical elapsed time in seconds ({@code tickCount × FIXED_TPF}). */
    @Override
    public float getTimeInSeconds() {
        return tickCount * FIXED_TPF;
    }

    /** Always returns {@link GameConstants#TARGET_FPS}. */
    @Override
    public float getFrameRate() {
        return GameConstants.TARGET_FPS;
    }

    /** Always returns {@code 1 / TARGET_FPS} — the fixed step size. */
    @Override
    public float getTimePerFrame() {
        return FIXED_TPF;
    }

    /**
     * Blocks the calling (render) thread until the next scheduled tick, then
     * advances the tick counter.
     *
     * <ol>
     *   <li>Parks via {@link LockSupport#parkNanos} until {@link #SPIN_THRESHOLD_NANOS}
     *       remain.</li>
     *   <li>Busy-spins for the final nanoseconds to achieve precise wakeup.</li>
     *   <li>If the system fell more than one full tick behind (e.g. during startup
     *       or a GC pause), the next deadline is reset to {@code now + TICK_NANOS}
     *       to avoid a catch-up spiral.</li>
     * </ol>
     */
    @Override
    public void update() {
        if (!started) {
            reset();
        }

        final long target = nextTickNanos;
        long remaining = target - System.nanoTime();

        // Park in coarse increments until close to the deadline
        while (remaining > SPIN_THRESHOLD_NANOS) {
            LockSupport.parkNanos(remaining - SPIN_THRESHOLD_NANOS);
            remaining = target - System.nanoTime();
        }

        // Busy-spin for the final nanoseconds
        //noinspection StatementWithEmptyBody
        while (System.nanoTime() < target) {
            // intentional busy-wait for high-resolution accuracy
        }

        // If we are more than one tick behind (e.g. GC pause), reschedule from
        // now instead of trying to catch up — prevents spiral-of-death.
        final long now = System.nanoTime();
        nextTickNanos = (now - nextTickNanos > TICK_NANOS)
                ? now + TICK_NANOS
                : nextTickNanos + TICK_NANOS;
        tickCount++;
    }

    /** Resets the tick counter to zero and schedules the first tick one interval from now. */
    @Override
    public void reset() {
        tickCount = 0L;
        nextTickNanos = System.nanoTime() + TICK_NANOS;
        started = true;
    }
}

