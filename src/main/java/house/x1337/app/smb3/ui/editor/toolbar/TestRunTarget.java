package house.x1337.app.smb3.ui.editor.toolbar;

import house.x1337.app.smb3.ui.editor.tab.core.BaseEditorTab;

/**
 * What the toolbar's Run button runs for one kind of tab, such as testing a level scene. Each editor whose tabs can
 * be run supplies one.
 */
public interface TestRunTarget {
    boolean handles(BaseEditorTab activeTab);

    String getLabel();

    void run(BaseEditorTab activeTab);
}
