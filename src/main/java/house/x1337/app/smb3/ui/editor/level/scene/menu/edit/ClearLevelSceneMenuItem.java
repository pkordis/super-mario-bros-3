package house.x1337.app.smb3.ui.editor.level.scene.menu.edit;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.scene.LevelSceneEditorWindow;
import house.x1337.app.smb3.ui.editor.level.scene.menu.LevelSceneEditorWindowMenuItem;
import house.x1337.app.smb3.ui.editor.level.scene.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.scene.tab.LevelSceneEditorTabSystem;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.swing.*;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
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
