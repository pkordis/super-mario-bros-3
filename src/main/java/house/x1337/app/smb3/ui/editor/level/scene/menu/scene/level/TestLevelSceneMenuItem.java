package house.x1337.app.smb3.ui.editor.level.scene.menu.scene.level;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.ui.editor.level.scene.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.scene.tab.LevelSceneEditorTabSystem;
import house.x1337.app.smb3.ui.editor.level.scene.tester.LevelSceneTesterSession;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenuItem;
import javax.swing.JPanel;

import java.util.concurrent.atomic.AtomicReference;

import static house.x1337.app.smb3.util.factory.LevelSceneEditorTabFactory.fromLevelSceneEditor;
import static house.x1337.app.smb3.util.factory.LevelSceneTesterSessionFactory.fromLevelScene;
import static javax.swing.JOptionPane.WARNING_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;

@Singleton
@RequiredArgsConstructor
public class TestLevelSceneMenuItem extends JMenuItem {
    private final LevelSceneEditorTabSystem editorTabs;
    private final AtomicReference<JPanel> gameEngineTesterPanelWrapper = new AtomicReference<>(null);

    @PostConstruct
    public void init() {
        setText("Test Level Scene");
        addActionListener(e -> startTestingSession());
    }

    private void startTestingSession() {
        final LevelSceneEditorTab active = editorTabs.getActiveTab();
        if (active == null) {
            return;
        }
        if (!active.hasRenderingStarter()) {
            showMessageDialog(
                this,
                "A Rendering Starter tile must be placed before testing the scene.",
                "Rendering Starter Required",
                WARNING_MESSAGE
            );
            return;
        }
        if (!active.hasSpawnPoint()) {
            showMessageDialog(
                this,
                "A Spawn Point tile must be placed before testing the scene.",
                "Spawn Point Required",
                WARNING_MESSAGE
            );
            return;
        }

        // If a test is already running, focus the existing tab
        if (gameEngineTesterPanelWrapper.get() != null) {
            final JPanel activeGameEngineTesterPanelWrapper = gameEngineTesterPanelWrapper.get();
            final int idx = editorTabs.indexOfComponent(activeGameEngineTesterPanelWrapper);
            if (idx >= 0) {
                editorTabs.setSelectedIndex(idx);
            }
            return;
        }

        final LevelScene levelScene = fromLevelSceneEditor(active);
        final LevelSceneTesterSession testerSession = fromLevelScene(levelScene);
        testerSession.start();
    }
}
