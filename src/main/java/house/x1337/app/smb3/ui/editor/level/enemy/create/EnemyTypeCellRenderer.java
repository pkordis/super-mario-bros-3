package house.x1337.app.smb3.ui.editor.level.enemy.create;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.enemy.EnemyType;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import java.awt.Component;

@Prototype
final class EnemyTypeCellRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(
        final JList<?> list,
        final Object value,
        final int index,
        final boolean isSelected,
        final boolean cellHasFocus
    ) {
        final Object label = value instanceof final EnemyType enemyType ? enemyType.getLabel() : value;
        return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
    }
}
