package house.x1337.app.smb3.game.engine;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import lombok.Data;

/**
 * Mutable model holding the current player state values displayed on the HUD.
 *
 * <p>All numeric values are kept as integers matching the NES representation.
 * The HUD renderer converts these to digit arrays at display time.
 *
 * <p>The P-meter is stored as a bitmask (bits 0–6 for arrows 1–7) matching
 * the {@code Player_Power} variable in the dasm (prg008/prg026).
 */
@Data
@Prototype
public final class PlayerData {

    /** The player identity (Mario or Luigi). */
    private PlayerIdentity identity;

    /** Current world number (1–9). */
    private int world = 1;

    /**
     * Internal P-meter charge level used by the physics engine (0–7).
     * Mirrors {@code Player_Power} in the dasm (prg008).
     */
    private int playerPower;

    /**
     * Throttle counter for P-meter charge/drain rate limiting.
     * Decrements each frame; when zero the meter advances one step.
     */
    private int playerPowerThrottle;

    /** P-meter display level (0 = empty, 7 = full). Bits 0–6 represent arrows. */
    private int pMeter;

    /** Whether the P-meter is at max (triggers [P] flash). */
    private boolean pMeterFull;

    /** Player score (0–9999999). */
    private int score;

    /** Coins held (0–99). */
    private int coins;

    /** Lives remaining (0–99). */
    private int lives = 4;

    /** Timer value (0–999). Timer of 0 means no timer / map screen. */
    private int timer;

    /** Whether the timer is active (false on world map). */
    private boolean timerActive;

    /** True if current player is Mario, false if Luigi. */
    private boolean mario = true;
}
