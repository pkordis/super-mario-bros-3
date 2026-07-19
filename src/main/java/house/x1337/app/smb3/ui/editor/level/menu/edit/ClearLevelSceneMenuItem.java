package house.x1337.app.smb3.ui.editor.level.menu.edit;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.menu.LevelSceneEditorWindowMenuItem;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTabSystem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.InputEvent.SHIFT_DOWN_MASK;
import static java.awt.event.KeyEvent.VK_C;
import static javax.swing.JOptionPane.YES_NO_OPTION;
import static javax.swing.JOptionPane.YES_OPTION;
import static javax.swing.JOptionPane.showConfirmDialog;
import static javax.swing.KeyStroke.getKeyStroke;

@Singleton
@RequiredArgsConstructor
public class ClearLevelSceneMenuItem extends LevelSceneEditorWindowMenuItem {
    private final LevelSceneEditorTabSystem editorTabs;

    @PostConstruct
    void init() {
        setText("Clear Level Scene");
        setAccelerator(getKeyStroke(VK_C, CTRL_DOWN_MASK | SHIFT_DOWN_MASK));
        addActionListener(e -> {
            final LevelSceneEditorTab active = editorTabs.getActiveTab();
            if (active == null) {
                return;
            }
            final int result = showConfirmDialog(getParentFrame(), "Clear all tiles?", "Clear Scene", YES_NO_OPTION);
            if (result == YES_OPTION) {
                editorTabs.untrack(active);
            }
        });
    }
}
