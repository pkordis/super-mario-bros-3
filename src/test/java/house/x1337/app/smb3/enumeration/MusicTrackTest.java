package house.x1337.app.smb3.enumeration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static house.x1337.app.smb3.enumeration.MusicTrack.BATTLE_LOST;
import static house.x1337.app.smb3.enumeration.MusicTrack.COURSE_CLEAR;
import static house.x1337.app.smb3.enumeration.MusicTrack.GAME_OVER;
import static house.x1337.app.smb3.enumeration.MusicTrack.HURRY_UP;
import static house.x1337.app.smb3.enumeration.MusicTrack.KING_RESTORED;
import static house.x1337.app.smb3.enumeration.MusicTrack.PLAYER_DOWN;
import static house.x1337.app.smb3.enumeration.MusicTrack.VICTORY_FANFARE;
import static org.assertj.core.api.Assertions.assertThat;

class MusicTrackTest {
    @Test
    @DisplayName("Only the jingles play once; every other track loops")
    void onlyTheJinglesPlayOnce() {
        // Execute
        final List<MusicTrack> once = Arrays.stream(MusicTrack.values()).filter(track -> !track.isLooping()).toList();

        // Verify
        assertThat(once).containsExactlyInAnyOrder(
            HURRY_UP,
            COURSE_CLEAR,
            BATTLE_LOST,
            KING_RESTORED,
            VICTORY_FANFARE,
            GAME_OVER,
            PLAYER_DOWN
        );
    }

    @Test
    @DisplayName("Exactly the world map songs belong to the map, and every other track to the level scene")
    void theWorldMapSongsBelongToTheMap() {
        // Execute & Verify
        for (final MusicTrack track : MusicTrack.values()) {
            final boolean worldMapSong = track.name().startsWith("WORLD_");
            assertThat(track.getContext())
                .as("%s", track)
                .isEqualTo(worldMapSong ? GameContext.MAP : GameContext.LEVEL_SCENE);
            assertThat(track.getLabel().startsWith("World "))
                .as("%s is labelled as a world map song exactly when it is one", track)
                .isEqualTo(worldMapSong);
        }
    }

    @Test
    @DisplayName("Every track has a label, and no two share one")
    void everyTrackHasADistinctLabel() {
        // Execute
        final List<String> labels = Arrays.stream(MusicTrack.values()).map(MusicTrack::getLabel).toList();

        // Verify
        assertThat(labels).doesNotContainNull().doesNotHaveDuplicates();
    }
}
