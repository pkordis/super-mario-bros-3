package house.x1337.app.smb3.game.engine.fade;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * How long a fade takes and whether the game waits for it.
 *
 * <p>Every ROM fade darkens or brightens the whole palette one brightness row ({@code $10}) per step, so only the
 * timing changes from one context to another. Four steps cover the four rows from full colour to black; a profile with
 * more steps holds at black (or at full colour) for the rest.
 */
@Getter
@RequiredArgsConstructor
public enum FadeProfile {
    STANDARD(true, 4, 4),
    TOAD_AND_KING(false, 4, 11),
    TITLE(false, 5, 4);

    static final int BRIGHTNESS_ROWS = 4;

    private final boolean blocking;
    private final int framesPerStep;
    private final int steps;

    int totalFrames() {
        return steps * framesPerStep;
    }
}
