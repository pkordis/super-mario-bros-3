package house.x1337.app.smb3.ui.editor.level.menu.file;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.menu.LevelSceneEditorWindowMenuItem;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTabSystem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.KeyEvent.VK_S;
import static javax.swing.JOptionPane.WARNING_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;
import static javax.swing.KeyStroke.getKeyStroke;

@Singleton
@RequiredArgsConstructor
public class SaveLevelSceneMenuItem extends LevelSceneEditorWindowMenuItem {
    private final LevelSceneEditorTabSystem levelSceneEditorTabSystem;

    @PostConstruct
    void init() {
        setText("Save Level Scene");
        setAccelerator(getKeyStroke(VK_S, CTRL_DOWN_MASK));
        addActionListener(e -> saveLevelScene());
    }

    private void saveLevelScene() {
        final LevelSceneEditorTab activeTab = levelSceneEditorTabSystem.getActiveTab();
        if (activeTab == null) {
            showMessageDialog(
                getParentFrame(),
                "No active scene tab to save.",
                "Save Level Scene",
                WARNING_MESSAGE
            );
            return;
        }

        final SaveLevelSceneWindow saveWindow = SaveLevelSceneWindow.launch(
            getParentFrame(),
            activeTab
        );
        saveWindow.setVisible(true);
    }
}
