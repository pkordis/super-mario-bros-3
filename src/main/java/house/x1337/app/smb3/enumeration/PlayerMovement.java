package house.x1337.app.smb3.enumeration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Logical movement / action mode of the player.
 *
 * <p>This enum captures <em>what the player is doing</em>, independently of
 * which power-up appearance they carry ({@link PlayerMode}).
 * The renderer and animation system use it to decide which
 * {@link PlayerMode} frame to display on each tick.
 *
 * <ul>
 *   <li>{@link #STILL} - on the ground, not moving horizontally.</li>
 *   <li>{@link #WALKING} - on the ground, moving, walk button only (no run).</li>
 *   <li>{@link #RUNNING} - on the ground, B held, speed ≥ TOPRUNSPEED but below
 *       power threshold. Still uses walk animation frames (accelerating).</li>
 *   <li>{@link #POWER_RUNNING} - on the ground, at or near TOPPOWERSPEED
 *       (abs(XVel) ≥ $37). Uses the spread-eagle running sprites.</li>
 *   <li>{@link #SKIDDING} - on the ground, pressing the opposite direction from
 *       current movement while abs(XVel) ≥ 2 raw ($02). Plays the skid sound and
 *       shows the braking sprite (prg008.asm: Player_SkidFrame). The player's
 *       facing orientation is retained (does not flip to input direction).</li>
 *   <li>{@link #JUMPING} - airborne with upward (negative) vertical velocity.</li>
 *   <li>{@link #FALLING} - airborne with downward (positive) vertical velocity.</li>
 *   <li>{@link #FLYING} - airborne via a power-up (e.g. Tanooki / P-Wing).</li>
 *   <li>{@link #SWIMMING} - inside a water zone.</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum PlayerMovement {
    DUCKING("Ducking"),
    FALLING("Falling"),
    FLYING("Flying"),
    JUMPING("Jumping"),
    POWER_RUNNING("Power Running"),
    RUNNING("Running"),
    SKIDDING("Skidding"),
    STILL("Still"),
    SWIMMING("Swimming"),
    WALKING("Walking");

    private final String label;
}
