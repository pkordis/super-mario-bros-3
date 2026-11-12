package house.x1337.app.smb3.ui.editor.level.menu.file;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.MusicTrack;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

import static house.x1337.app.smb3.enumeration.GameContext.LEVEL_SCENE;

/**
 * The choice of a scene's background music: "None" first, then every looping level-scene track in declaration order.
 * The jingles are left out, as they play once and are not background music, and so are the world map's songs.
 */
@Prototype
@RequiredArgsConstructor
final class BackgroundMusicComboBox extends JComboBox<MusicTrack> {
    private final MusicTrackCellRenderer renderer;

    @PostConstruct
    void init() {
        final DefaultComboBoxModel<MusicTrack> model = new DefaultComboBoxModel<>();
        model.addElement(null);
        for (final MusicTrack track : MusicTrack.values()) {
            if (track.isLooping() && track.getContext() == LEVEL_SCENE) {
                model.addElement(track);
            }
        }
        setModel(model);
        setRenderer(renderer);
    }

    /** @return the chosen track, or {@code null} when "None" is chosen */
    MusicTrack getSelectedTrack() {
        return (MusicTrack) getSelectedItem();
    }
}
