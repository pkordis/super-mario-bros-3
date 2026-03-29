package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Logical movement / action state of the player.
 *
 * <p>This enum captures <em>what the player is doing</em>, independently of
 * which power-up appearance they carry ({@link PlayerAppearance}).
 * The renderer and animation system use it to decide which
 * {@link PlayerAppearance} frame to display on each tick.
 *
 * <ul>
 *   <li>{@link #STILL} — on the ground, not moving horizontally.</li>
 *   <li>{@link #WALKING} — on the ground, moving, walk button only (no run).</li>
 *   <li>{@link #RUNNING} — on the ground, moving, run button held.</li>
 *   <li>{@link #JUMPING} — airborne with upward (negative) vertical velocity.</li>
 *   <li>{@link #FALLING} — airborne with downward (positive) vertical velocity.</li>
 *   <li>{@link #FLYING} — airborne via a power-up (e.g. Tanooki / P-Wing).</li>
 *   <li>{@link #SWIMMING} — inside a water zone.</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum PlayerState {
    STILL("Still"),
    WALKING("Walking"),
    RUNNING("Running"),
    JUMPING("Jumping"),
    FALLING("Falling"),
    FLYING("Flying"),
    SWIMMING("Swimming");

    private final String label;
}

