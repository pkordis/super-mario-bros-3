package house.x1337.app.smb3.ui.editor.level.toolbar;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tester.LevelSceneTesterSession;
import house.x1337.app.smb3.ui.editor.tab.core.BaseEditorTab;
import house.x1337.app.smb3.ui.editor.toolbar.TestRunTarget;

import static house.x1337.app.smb3.util.factory.LevelSceneEditorTabFactory.fromLevelSceneEditor;
import static house.x1337.app.smb3.util.factory.LevelSceneTesterSessionFactory.fromLevelScene;
import static javax.swing.JOptionPane.WARNING_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;

/**
 * Tests the active level scene, for the toolbar's Run Level and for Level &gt; Scene &gt; Test Level Scene alike. A
 * scene without a rendering starter or a spawn point is refused with a warning.
 */
@Singleton
public class LevelSceneTestRunTarget implements TestRunTarget {
    @Override
    public boolean handles(final BaseEditorTab activeTab) {
        return activeTab instanceof LevelSceneEditorTab;
    }

    @Override
    public String getLabel() {
        return "Run Level";
    }

    @Override
    public void run(final BaseEditorTab activeTab) {
        if (!(activeTab instanceof final LevelSceneEditorTab tab)) {
            return;
        }
        if (!tab.hasRenderingStarter()) {
            showMessageDialog(
                tab,
                "A Rendering Starter must be placed before testing the scene.",
                "Rendering Starter Required",
                WARNING_MESSAGE
            );
            return;
        }
        if (!tab.hasSpawnPoint()) {
            showMessageDialog(
                tab,
                "A Spawn Point must be placed before testing the scene.",
                "Spawn Point Required",
                WARNING_MESSAGE
            );
            return;
        }
        final LevelScene levelScene = fromLevelSceneEditor(tab);
        final LevelSceneTesterSession testerSession = fromLevelScene(levelScene);
        testerSession.start();
    }
}
