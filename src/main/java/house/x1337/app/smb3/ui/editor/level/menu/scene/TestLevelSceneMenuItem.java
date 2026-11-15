package house.x1337.app.smb3.ui.editor.level.menu.scene;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneTabs;
import house.x1337.app.smb3.ui.editor.level.tester.LevelSceneTesterSession;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenuItem;

import static house.x1337.app.smb3.util.factory.LevelSceneEditorTabFactory.fromLevelSceneEditor;
import static house.x1337.app.smb3.util.factory.LevelSceneTesterSessionFactory.fromLevelScene;
import static javax.swing.JOptionPane.WARNING_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;

@Singleton
@RequiredArgsConstructor
public class TestLevelSceneMenuItem extends JMenuItem {
    private final LevelSceneTabs levelSceneTabs;

    @PostConstruct
    public void init() {
        setText("Test Level Scene");
        levelSceneTabs.enableOnlyWhileActive(this);
        addActionListener(e -> startTestingSession());
    }

    private void startTestingSession() {
        final LevelSceneEditorTab active = levelSceneTabs.getActiveTab();
        if (!active.hasRenderingStarter()) {
            showMessageDialog(
                this,
                "A Rendering Starter must be placed before testing the scene.",
                "Rendering Starter Required",
                WARNING_MESSAGE
            );
            return;
        }
        if (!active.hasSpawnPoint()) {
            showMessageDialog(
                this,
                "A Spawn Point must be placed before testing the scene.",
                "Spawn Point Required",
                WARNING_MESSAGE
            );
            return;
        }

        final LevelScene levelScene = fromLevelSceneEditor(active);
        final LevelSceneTesterSession testerSession = fromLevelScene(levelScene);
        testerSession.start();
    }
}
