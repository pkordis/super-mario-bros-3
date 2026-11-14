package house.x1337.app.smb3.ui.editor.menu;

import house.x1337.app.smb3.ui.editor.tab.core.BaseEditorTab;

import javax.swing.JMenuItem;
import java.util.List;

/**
 * File menu items an editor offers for its own tabs. The File menu shows them, above Settings, only while one of
 * those tabs is the active one.
 */
public interface FileMenuContribution {
    boolean appliesTo(BaseEditorTab activeTab);

    List<JMenuItem> getItems();
}
