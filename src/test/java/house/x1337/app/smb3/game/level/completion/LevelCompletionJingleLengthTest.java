package house.x1337.app.smb3.game.level.completion;

import house.x1337.app.smb3.enumeration.MusicTrack;
import house.x1337.app.smb3.game.sound.OggTrackLength;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static house.x1337.app.smb3.GameConstants.SIMULATION_FPS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The time bonus waits for the level completion jingle to end: the course-clear jingle's tune lasts 192 ticks and the
 * fanfare's 240, so each file must end just after its tune, with no silence left to wait through. A few ticks over
 * cover the recording's short lead-in and the last note's release.
 */
class LevelCompletionJingleLengthTest {
    private static final int TICKS_OVER_THE_TUNE = 4;

    private final OggTrackLength oggTrackLength = new OggTrackLength();

    @ParameterizedTest(name = "{0}: {1} ticks")
    @CsvSource({
        "COURSE_CLEAR, 192",
        "VICTORY_FANFARE, 240"
    })
    @DisplayName("A level completion jingle ends within a few ticks of its tune, so the wait after it is not stretched")
    void endsWithItsTune(final MusicTrack track, final int tuneTicks) {
        // Execute
        final double ticks = oggTrackLength.secondsOf(track.getPath()) * SIMULATION_FPS;

        // Verify
        assertThat(ticks)
            .as("%s length in ticks", track)
            .isBetween((double) tuneTicks, (double) tuneTicks + TICKS_OVER_THE_TUNE);
    }
}
