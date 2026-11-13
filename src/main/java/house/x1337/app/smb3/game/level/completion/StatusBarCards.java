package house.x1337.app.smb3.game.level.completion;

import java.util.Arrays;

import static house.x1337.app.smb3.game.player.PlayerData.CARD_SLOTS;

/**
 * Which of the status bar's card slots the level completion shows empty. Like the status bar itself, a slot keeps
 * what was last drawn in it until it is drawn again.
 */
final class StatusBarCards {
    static final int CYCLE_FRAMES = 8;
    static final int FLASH_BIT = 16;
    static final int NEWEST_CARD_HOLD_TICKS = 48;

    private final boolean[] hidden = new boolean[CARD_SLOTS];
    private int newestSlot;
    private int hold;
    private int cycledSlot;

    void reset() {
        Arrays.fill(hidden, false);
        newestSlot = 0;
        hold = 0;
        cycledSlot = 0;
    }

    void hideTheNewest(final int slot) {
        newestSlot = slot;
        hidden[slot] = true;
    }

    void holdTheNewest() {
        hold = NEWEST_CARD_HOLD_TICKS;
    }

    void countDown() {
        if (hold > 0) {
            hold--;
        }
    }

    /** Shows the newest card while the hold lasts, then on every other {@value #FLASH_BIT} frames. */
    void drawTheNewestFlashing(final int frame) {
        hidden[newestSlot] = hold == 0 && (frame & FLASH_BIT) == 0;
    }

    /** Blinks the cards off in turn: one is empty for {@value #CYCLE_FRAMES} frames but the last, then the next. */
    void cycle(final int frame) {
        if (frame % CYCLE_FRAMES != 0) {
            hidden[cycledSlot] = true;
            return;
        }
        hidden[cycledSlot] = false;
        cycledSlot = (cycledSlot + 1) % CARD_SLOTS;
    }

    boolean isHidden(final int slot) {
        return hidden[slot];
    }
}
