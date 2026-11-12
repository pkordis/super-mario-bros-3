package house.x1337.app.smb3.ui.editor.level.menu.file;

import house.x1337.app.smb3.enumeration.MusicTrack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static house.x1337.app.smb3.enumeration.GameContext.LEVEL_SCENE;
import static house.x1337.app.smb3.enumeration.GameContext.MAP;
import static house.x1337.app.smb3.enumeration.MusicTrack.ATHLETIC;
import static house.x1337.app.smb3.enumeration.MusicTrack.COURSE_CLEAR;
import static house.x1337.app.smb3.enumeration.MusicTrack.FORTRESS;
import static house.x1337.app.smb3.enumeration.MusicTrack.WORLD_1_GRASS_LAND;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Save dialog's background music choice: what it offers, what it preselects and how each entry reads.
 */
class BackgroundMusicComboBoxTest {
    private final MusicTrackCellRenderer renderer = new MusicTrackCellRenderer();

    @Test
    @DisplayName("None comes first, then every looping level-scene track in declaration order")
    void offersNoneAndTheLoopingLevelSceneTracks() {
        // Execute
        final BackgroundMusicComboBox combo = new BackgroundMusicComboBox(renderer);
        combo.init();
        final List<MusicTrack> items = itemsOf(combo);

        // Verify
        final List<MusicTrack> expected = new ArrayList<>();
        expected.add(null);
        expected.addAll(Arrays.stream(MusicTrack.values())
            .filter(track -> track.isLooping() && track.getContext() == LEVEL_SCENE)
            .toList());
        assertThat(items).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("No jingle and no world map song is offered")
    void offersNoJingleAndNoMapSong() {
        // Execute
        final List<MusicTrack> items = itemsOf(new BackgroundMusicComboBox(renderer));

        // Verify
        assertThat(items).doesNotContain(COURSE_CLEAR);
        assertThat(items)
            .doesNotContainAnyElementsOf(Arrays.stream(MusicTrack.values())
                .filter(track -> track.getContext() == MAP)
                .toList());
    }

    @Test
    @DisplayName("A scene without music preselects None")
    void noMusicPreselectsNone() {
        // Execute
        final BackgroundMusicComboBox combo = new BackgroundMusicComboBox(renderer);
        combo.init();

        // Verify
        assertThat(combo.getSelectedTrack()).isNull();
    }

    @Test
    @DisplayName("A scene's track is preselected, and a new choice is what the combo hands back")
    void theScenesTrackIsPreselected() {
        // Prepare
        final BackgroundMusicComboBox combo = new BackgroundMusicComboBox(renderer);
        combo.init();
        combo.setSelectedItem(ATHLETIC);
        final MusicTrack preselected = combo.getSelectedTrack();

        // Execute
        combo.setSelectedItem(FORTRESS);

        // Verify
        assertThat(preselected).isEqualTo(ATHLETIC);
        assertThat(combo.getSelectedTrack()).isEqualTo(FORTRESS);
    }

    @Test
    @DisplayName("A track reads as its label, and the empty choice as None")
    void entriesReadAsLabels() {
        // Prepare
        final JList<MusicTrack> list = new JList<>();

        // Execute
        final JLabel track = (JLabel) renderer.getListCellRendererComponent(list, WORLD_1_GRASS_LAND, 1, false, false);
        final String trackText = track.getText();
        final JLabel none = (JLabel) renderer.getListCellRendererComponent(list, null, 0, false, false);

        // Verify
        assertThat(trackText).isEqualTo("World 1 Map: Grass Land");
        assertThat(none.getText()).isEqualTo("None");
    }

    private List<MusicTrack> itemsOf(final BackgroundMusicComboBox combo) {
        final List<MusicTrack> items = new ArrayList<>();
        for (int index = 0; index < combo.getItemCount(); index++) {
            items.add(combo.getItemAt(index));
        }
        return items;
    }
}
