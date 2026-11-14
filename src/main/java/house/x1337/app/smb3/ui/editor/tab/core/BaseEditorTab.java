package house.x1337.app.smb3.ui.editor.tab.core;

import house.x1337.app.smb3.ui.editor.tab.EditorTabSystem;

import javax.swing.JPanel;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

/**
 * A tab of the editor. Every kind of editor (level scenes, the level scene tester, ...) shows its work in one.
 */
public abstract class BaseEditorTab extends JPanel {
    private final EditorTabSystem editorTabSystem = getBean(EditorTabSystem.class);

    /**
     * The title shown on the tab.
     */
    public abstract String resolveTitle();

    /**
     * Called once the tab has been closed and removed from the tab system.
     */
    public void onClose() {
    }

    /**
     * Shows the current {@link #resolveTitle()} on the tab.
     */
    public void updateTabTitle() {
        editorTabSystem.updateTitle(this);
    }
}
