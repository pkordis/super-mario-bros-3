package house.x1337.app.smb3.game.level.completion;

/**
 * A placeholder for the course-clear music, until the game has a sound engine.
 *
 * <p>The card touch starts the course-clear music, and the time bonus does nothing while any music plays, so the
 * time bonus waits for the track to end. This class stands in for the track with a wait of
 * {@link #COURSE_CLEAR_MUSIC_TICKS}. No music is supplied yet, so the wait is 0 and the bonus starts as soon as the
 * card has flown off.
 *
 * <p>Replace this class with the sound engine's "music playing" state once there is one.
 */
public final class CourseClearMusicStandIn {
    /** Ticks the stand-in plays for. 0 while the game has no music to wait for. */
    public static final int COURSE_CLEAR_MUSIC_TICKS = 0;

    private final int lengthTicks;
    private int remainingTicks;

    public CourseClearMusicStandIn() {
        this(COURSE_CLEAR_MUSIC_TICKS);
    }

    /** @param lengthTicks how long the stand-in plays for */
    public CourseClearMusicStandIn(final int lengthTicks) {
        this.lengthTicks = lengthTicks;
    }

    /** Starts the track from its beginning, on the tick the card is touched. */
    public void start() {
        remainingTicks = lengthTicks;
    }

    public void stop() {
        remainingTicks = 0;
    }

    /** @return whether the track is still playing */
    public boolean isPlaying() {
        return remainingTicks > 0;
    }

    public void tick() {
        if (remainingTicks > 0) {
            remainingTicks--;
        }
    }
}
