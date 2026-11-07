package house.x1337.app.smb3.game.engine.fade;

import house.x1337.app.smb3.annotation.Singleton;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.game.engine.fade.FadeDirection.IN;
import static house.x1337.app.smb3.game.engine.fade.FadeDirection.OUT;
import static house.x1337.app.smb3.game.engine.fade.FadeProfile.BRIGHTNESS_ROWS;

@Slf4j
@Singleton
public final class FadingEngine {
    private FadeProfile profile;
    private FadeDirection direction;
    private Runnable onComplete;
    private int elapsedTicks;
    private boolean running;
    private boolean heldBlack;

    /**
     * Starts fading to black, replacing any fade under way.
     *
     * @param profile    the fade's timing
     * @param onComplete run once on the engine thread, inside {@link #tick()}, when the screen is black; may be
     *                   {@code null}
     */
    public void fadeOut(final FadeProfile profile, final Runnable onComplete) {
        start(OUT, profile, onComplete);
    }

    /**
     * Starts fading in from (nearly) black, replacing any fade under way.
     *
     * @param profile    the fade's timing
     * @param onComplete run once on the engine thread, inside {@link #tick()}, when the colours are back; may be
     *                   {@code null}
     */
    public void fadeIn(final FadeProfile profile, final Runnable onComplete) {
        start(IN, profile, onComplete);
    }

    /**
     * Advances the fade by one simulation tick and, on the tick it completes, runs its callback. The state is settled
     * before the callback runs, so the callback may start the next fade.
     */
    public void tick() {
        if (!running) {
            return;
        }
        elapsedTicks++;
        final int completionTick = direction == OUT ? profile.totalFrames() + 1 : profile.totalFrames();
        if (elapsedTicks < completionTick) {
            return;
        }
        running = false;
        heldBlack = direction == OUT;
        final Runnable callback = onComplete;
        onComplete = null;
        log.debug("Fade {} complete after {} tick(s)", direction, elapsedTicks);
        if (callback != null) {
            callback.run();
        }
    }

    /**
     * @return how dark the screen is, from 0 (full colour) to 1 (black), in quarters
     */
    public float getDarkness() {
        if (heldBlack) {
            return 1f;
        }
        if (!running) {
            return 0f;
        }
        final int stepsDone = Math.min(profile.getSteps(), elapsedTicks / profile.getFramesPerStep());
        final int rowsDown = direction == OUT
            ? Math.min(BRIGHTNESS_ROWS, stepsDone)
            : Math.max(0, BRIGHTNESS_ROWS - 1 - stepsDone);
        return rowsDown / (float) BRIGHTNESS_ROWS;
    }

    /**
     * @return {@code true} while a fade is under way
     */
    public boolean isFading() {
        return running;
    }

    /**
     * @return {@code true} while a blocking fade runs, or while the screen is held black after one
     */
    public boolean isHaltingGameplay() {
        return profile != null && profile.isBlocking() && (running || heldBlack);
    }

    /**
     * Drops any fade and shows full colour. Called when an engine starts, since this singleton outlives each engine.
     */
    public void reset() {
        profile = null;
        direction = null;
        onComplete = null;
        elapsedTicks = 0;
        running = false;
        heldBlack = false;
    }

    private void start(final FadeDirection direction, final FadeProfile profile, final Runnable onComplete) {
        this.profile = profile;
        this.direction = direction;
        this.onComplete = onComplete;
        this.elapsedTicks = 0;
        this.running = true;
        this.heldBlack = false;
        log.debug(
            "Fade {} started: {} step(s) of {} tick(s)",
            direction,
            profile.getSteps(),
            profile.getFramesPerStep()
        );
    }
}
