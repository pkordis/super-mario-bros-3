package house.x1337.app.smb3.ui.editor.level.menu.file;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneTabs;
import house.x1337.app.smb3.ui.editor.menu.GameEditorWindowMenuItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.KeyEvent.VK_S;
import static javax.swing.KeyStroke.getKeyStroke;

/**
 * Only in the File menu while a level scene tab is active (see {@link LevelSceneFileMenuContribution}).
 */
@Singleton
@RequiredArgsConstructor
public class SaveLevelSceneMenuItem extends GameEditorWindowMenuItem {
    private final LevelSceneTabs levelSceneTabs;

    @PostConstruct
    void init() {
        setText("Save Level Scene");
        setAccelerator(getKeyStroke(VK_S, CTRL_DOWN_MASK));
        addActionListener(e -> saveLevelScene());
    }

    private void saveLevelScene() {
        final SaveLevelSceneWindow saveWindow = SaveLevelSceneWindow.launch(
            getParentFrame(),
            levelSceneTabs.getActiveTab()
        );
        saveWindow.setVisible(true);
    }
}
