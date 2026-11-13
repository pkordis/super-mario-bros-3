package house.x1337.app.smb3.game.level.completion;

import house.x1337.app.smb3.enumeration.LevelCompletionCardType;
import house.x1337.app.smb3.game.level.completion.LevelCompletion.Message;

/**
 * What the level completion shows over the level view on this tick; equal when they show the same.
 *
 * @param viewTop    the view's top line in level pixels, which places what the fanfare draws on the sky
 * @param fireworks  {@code null} without a fanfare
 * @param livesBonus {@code null} until lives are paid out
 */
public record LevelCompletionScreen(
    Message message,
    LevelCompletionCardType card,
    int viewTop,
    FireworksPicture fireworks,
    LivesBonusPicture livesBonus
) {
    public static final LevelCompletionScreen NOTHING = new LevelCompletionScreen(Message.NONE, null, 0, null, null);

    public boolean isEmpty() {
        return message == Message.NONE && fireworks == null && livesBonus == null;
    }

    /** @param sparkle the picture the dots show on this tick, 0 to 3 */
    public record FireworksPicture(
        LevelCompletionCardType shape,
        FireworksFade fade,
        int sparkle
    ) {
    }

    /** @param y the top line on the view */
    public record LivesBonusPicture(
        int lives,
        int x,
        int y
    ) {
    }
}
