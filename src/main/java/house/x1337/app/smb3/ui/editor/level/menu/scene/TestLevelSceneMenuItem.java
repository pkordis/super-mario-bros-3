package house.x1337.app.smb3.ui.editor.level.menu.scene;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneTabs;
import house.x1337.app.smb3.ui.editor.level.toolbar.LevelSceneTestRunTarget;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenuItem;

import static house.x1337.app.smb3.ui.editor.toolbar.RunButton.SHORTCUT;

@Singleton
@RequiredArgsConstructor
public class TestLevelSceneMenuItem extends JMenuItem {
    private final LevelSceneTabs levelSceneTabs;
    private final LevelSceneTestRunTarget levelSceneTestRunTarget;

    @PostConstruct
    public void init() {
        setText("Test Level Scene");
        // The toolbar's Run button binds the same key and takes it first; both run the same target.
        setAccelerator(SHORTCUT);
        levelSceneTabs.enableOnlyWhileActive(this);
        addActionListener(e -> levelSceneTestRunTarget.run(levelSceneTabs.getActiveTab()));
    }
}
