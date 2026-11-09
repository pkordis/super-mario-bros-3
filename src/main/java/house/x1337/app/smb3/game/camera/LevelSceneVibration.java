package house.x1337.app.smb3.game.camera;

import house.x1337.app.smb3.annotation.Singleton;
import lombok.extern.slf4j.Slf4j;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

/**
 * The level-wide screen shake: while its counter is greater than zero the screen vibrates, as from the impact of
 * something heavy.
 *
 * <p>Three properties drive this class:
 *
 * <ul>
 *   <li><b>Vertical only, whole pixels, amplitude ≤ 3.</b> There is no horizontal component.</li>
 *   <li><b>The visible sequence is {@code 0, 1, 3, 2}</b>, not the offset table's declaration order:
 *       the table is indexed by the count as it stood <em>before</em> the decrement, {@code & 3}. With the
 *       count running {@code 16..1} the indices go {@code 0, 3, 2, 1}, so a {@link #powerShake()} plays
 *       {@code 0, 1, 3, 2} exactly four times.</li>
 *   <li><b>It bypasses the scroll limits.</b> The offset is applied after the vertical scroll has already applied
 *       its rules and clamps. That is why the shake is visible even in a horizontal level whose view is locked at
 *       the bottom, and why this offset must be added <em>outside</em> the camera's level-bounds clamp (see
 *       {@code CameraState.update}).</li>
 * </ul>
 *
 * <p>State is held for one whole simulation tick and deliberately <b>not</b> interpolated between
 * ticks the way {@link LevelSceneVerticalScroll#interpolate(double)} is: the effect <em>is</em> a
 * per-frame 1-to-3&nbsp;pixel jitter, and easing it across render frames would smear it away.
 *
 * <p>This is a shared primitive, not a P-Switch detail: besides the P-Switch stomp, the heavy Koopalings' body
 * slams and other impacts shake the screen too. New callers get their own named preset alongside
 * {@link #powerShake()}.
 */
@Slf4j
@Singleton
public final class LevelSceneVibration {
    /**
     * The shake offsets in their declared order, read with the descending {@code count & 3} index in
     * {@link #tick()}. It is that pairing — indices {@code 0, 3, 2, 1} into
     * {@code 0, 2, 3, 1} — that produces the visible {@code 0, 1, 3, 2} cycle; reordering the table
     * to playback order would break the index.
     */
    private static final int[] VIBRATION_OFFSETS = {0, 2, 3, 1};

    /** Shake length, in frames, set by the P-Switch stomp. */
    private static final int POWER_SHAKE_FRAMES = 16;

    private int framesRemaining;
    private int verticalOffsetPixels;

    /**
     * Starts the "something heavy just landed" shake: 16 frames of vertical jitter. Restarting an
     * in-flight shake reloads the counter.
     */
    public void powerShake() {
        vibrate(POWER_SHAKE_FRAMES);
    }

    /**
     * Advances the shake by one simulation tick: latches this tick's offset, then counts down, so the
     * offset for a given frame is derived from the count as it stood at the start of that frame.
     *
     * <p>Call once per 60&nbsp;Hz tick, after the players and objects have updated, so the special-tile
     * check that presses the switch runs immediately before the shake ticks. Consequently the press
     * frame itself always shows offset 0 — {@code 16 & 3 == 0}.
     */
    public void tick() {
        if (framesRemaining == 0) {
            verticalOffsetPixels = 0;
            return;
        }
        verticalOffsetPixels = VIBRATION_OFFSETS[framesRemaining & 3];
        framesRemaining--;
    }

    /**
     * @return this tick's shake displacement in pixels, {@code 0..3}
     */
    public int getVerticalOffsetPixels() {
        return verticalOffsetPixels;
    }

    /**
     * The same displacement expressed for the camera. The offset walks the view <em>down</em> the
     * level, whereas the camera lives in Y-up game units — hence the negation — and one pixel is
     * {@code 1 / TILE_SPRITE_SIZE} of a game unit.
     *
     * @return the camera's vertical offset for this tick, in game units
     */
    public double getCameraOffsetUnits() {
        return -verticalOffsetPixels / (double) TILE_SPRITE_SIZE;
    }

    /**
     * @return whether a shake is still running
     */
    public boolean isVibrating() {
        return framesRemaining > 0;
    }

    /**
     * Clears the shake. Called when a level is set up so a shake left running by a previous level
     * (or a previous run of the same level in the editor's tester) cannot leak into the new one —
     * this bean is a singleton, unlike the per-scene {@link LevelSceneVerticalScroll}.
     */
    public void reset() {
        framesRemaining = 0;
        verticalOffsetPixels = 0;
    }

    private void vibrate(final int frames) {
        framesRemaining = frames;
        verticalOffsetPixels = 0;
        log.debug("Level vibration started for {} frames", frames);
    }
}
