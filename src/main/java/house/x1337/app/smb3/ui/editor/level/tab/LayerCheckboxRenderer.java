package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.ui.layer.Layer;
import jakarta.annotation.PostConstruct;

import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.*;

import static javax.swing.BorderFactory.createEmptyBorder;

@Singleton
public class LayerCheckboxRenderer extends JCheckBox implements ListCellRenderer<Layer> {
    @PostConstruct
    void init() {
        setOpaque(true);
        setBorder(createEmptyBorder(1, 4, 1, 4));
    }

    @Override
    public Component getListCellRendererComponent(
        final JList<? extends Layer> list,
        final Layer value,
        final int index,
        final boolean isSelected,
        final boolean cellHasFocus
    ) {
        setText(value.getType().getLabel());
        setSelected(value.isVisible());
        setEnabled(value.isEnabled());
        if (isSelected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
        }
        return this;
    }
}
