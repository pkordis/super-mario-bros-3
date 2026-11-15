package house.x1337.app.smb3.ui.editor.toolbar;

import javax.swing.JComponent;
import java.util.List;

/**
 * One group of tools on the editor toolbar. The toolbar lays the sections out by {@link #getOrder()}, lowest first,
 * with a separator between each.
 */
public interface EditorToolBarSection {
    int getOrder();
    List<JComponent> getTools();
}
