package house.x1337.app.smb3.ui.editor.level.menu.file;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.MusicTrack;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import java.awt.Component;

/** Shows a {@link MusicTrack} by its label, and the empty choice as "None". */
@Prototype
final class MusicTrackCellRenderer extends DefaultListCellRenderer {
    static final String NONE = "None";

    @Override
    public Component getListCellRendererComponent(
        final JList<?> list,
        final Object value,
        final int index,
        final boolean isSelected,
        final boolean cellHasFocus
    ) {
        final Object label = value instanceof final MusicTrack track ? track.getLabel() : NONE;
        return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
    }
}
