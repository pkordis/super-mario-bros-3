package house.x1337.app.smb3.game.level.completion;

import house.x1337.app.smb3.game.player.PlayerData;
import house.x1337.app.smb3.game.sound.SoundPlayer;
import lombok.Getter;

import static house.x1337.app.smb3.enumeration.SoundEffect.ONE_UP;

/** The lives three cards pay out one 1-up sound apart, and the big "nUP" that rises and bounces to show how many. */
@Getter
final class LivesBonus {
    static final int ONE_UP_SOUND_TICKS = 48;
    static final int RISEN_Y = 128;
    static final int RISE_PER_TICK = 4;
    static final int START_Y = 255;

    private final int lives;
    private final int x;
    private int livesLeft;
    private int y = START_Y;
    private int shownY = START_Y;
    private int oneUpSoundTicks;

    LivesBonus(
        final int lives,
        final int x
    ) {
        this.lives = lives;
        this.x = x;
        this.livesLeft = lives;
    }

    void countDown() {
        if (oneUpSoundTicks > 0) {
            oneUpSoundTicks--;
        }
    }

    /** Gives the next life once the last one's sound is over, and shows the "nUP" where it is as the tick begins. */
    void payOut(
        final PlayerData playerData,
        final SoundPlayer soundPlayer
    ) {
        shownY = y;
        if (livesLeft == 0 || oneUpSoundTicks > 0) {
            return;
        }
        soundPlayer.play(ONE_UP);
        oneUpSoundTicks = ONE_UP_SOUND_TICKS;
        playerData.addLives(1);
        livesLeft--;
    }

    boolean isRising() {
        return y >= RISEN_Y;
    }

    void rise() {
        y -= RISE_PER_TICK;
    }

    void bounce() {
        y ^= 1;
    }

    boolean isPaidOut() {
        return livesLeft == 0;
    }

    LevelCompletionScreen.LivesBonusPicture picture() {
        // A sprite is drawn one line below its line
        return new LevelCompletionScreen.LivesBonusPicture(lives, x, shownY + 1);
    }
}
