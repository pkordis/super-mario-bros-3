package house.x1337.app.smb3.ui.editor.level.virtualobject;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PipePortalType;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import java.awt.Component;

@Prototype
final class PipePortalTypeCellRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(
        final JList<?> list,
        final Object value,
        final int index,
        final boolean isSelected,
        final boolean cellHasFocus
    ) {
        final Object label = value instanceof final PipePortalType type ? type.getLabel() : value;
        return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
    }
}
