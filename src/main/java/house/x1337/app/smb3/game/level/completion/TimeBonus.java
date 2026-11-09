package house.x1337.app.smb3.game.level.completion;

import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.player.PlayerTimer;

/**
 * One step of the level-end time bonus, made once per frame.
 *
 * <p>While the hundreds or tens digit is non-zero, a step takes 10 off the timer and scores 500. Once only the units
 * are left it takes 1 off and scores 50.
 * Either way the clock turns into 50 points a second.
 */
final class TimeBonus {
    static final int TENS_STEP = 10;
    static final int TENS_POINTS = 500;
    static final int UNITS_STEP = 1;
    static final int UNITS_POINTS = 50;

    private TimeBonus() {
    }

    /**
     * @param playerData whose time is converted into score
     * @return whether a step was made; {@code false} once the clock reads 000
     */
    static boolean convertStep(final PlayerData playerData) {
        final PlayerTimer timer = playerData.getPlayerTimer();
        final int time = timer.getTime();
        if (time == 0) {
            return false;
        }
        final boolean tens = time >= TENS_STEP;
        timer.takeOff(tens ? TENS_STEP : UNITS_STEP);
        playerData.addPoints(tens ? TENS_POINTS : UNITS_POINTS);
        return true;
    }
}
