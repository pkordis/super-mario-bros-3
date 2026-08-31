package house.x1337.app.smb3.game.player;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import lombok.AllArgsConstructor;
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

    /**
     * Internal P-meter charge level used by the physics engine (0–7).
     * Mirrors {@code Player_Power} in the dasm (prg008).
     */
    private int playerPower;

    /**
     * Throttle counter for P-meter charge/drain rate limiting.
     * Decrements each frame; when zero the meter advances one step.
     */
    private int powerThrottle;

    /** P-meter display level (0 = empty, 7 = full). Bits 0–6 represent arrows. */
    private int powerMeter = 0;

    /** Whether the P-meter is at max (triggers [P] flash). */
    private boolean powerMeterFull = false;

    /** Player score (0–9999999). */
    private int score = 0;

    /** Coins held (0–99). */
    private int coins = 0;

    /** Lives remaining (0–99). */
    private int lives = 4;

    /** Current world number (1–9). */
    private int world = 1;

    /** Level countdown timer (manages start/pause/clear and event publishing). */
    private final PlayerTimer playerTimer = new PlayerTimer();

    /**
     * Returns the current timer display value (0–999).
     * Delegates to {@link PlayerTimer#getTime()}.
     */
    public int getTimer() {
        return playerTimer.getTime();
    }

    /**
     * Returns whether the timer is actively counting down.
     * Delegates to {@link PlayerTimer#isActive()}.
     */
    public boolean haveTimerActive() {
        return playerTimer.isActive();
    }

    public void addCoin() {
        ++coins;
    }

    public void addToScore(final Integer score) {
        this.score += score;
    }

    @Data
    @AllArgsConstructor
    public static class State {
        private int time;
        private int score;
        private int coins;
        private int lives;
        private int world;

        public boolean equals(final PlayerData playerData) {
            return playerData.score == this.score &&
                playerData.coins == this.coins &&
                playerData.lives == this.lives &&
                playerData.world == this.world &&
                playerData.getPlayerTimer().getTime() == this.time;
        }

        public void updateFrom(final PlayerData snapshot) {
            score = snapshot.score;
            coins = snapshot.coins;
            lives = snapshot.lives;
            world = snapshot.world;
            time = snapshot.getPlayerTimer().getTime();
        }
    }
}
