package house.x1337.app.smb3.util.factory;

import house.x1337.app.smb3.engine.core.GameEngine;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.service.ConfigurationService;
import house.x1337.app.smb3.ui.editor.level.scene.pane.MiddlePane;
import house.x1337.app.smb3.ui.editor.level.scene.tab.LevelSceneEditorTabSystem;
import house.x1337.app.smb3.ui.editor.level.scene.tester.DebuggerPanel;
import house.x1337.app.smb3.ui.editor.level.scene.tester.LevelSceneTesterSession;
import house.x1337.app.smb3.ui.editor.level.scene.tester.input.automation.InputAutomationPanel;
import house.x1337.app.smb3.ui.editor.level.scene.tester.physics.tuning.PhysicsTunerPanel;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface LevelSceneTesterSessionFactory {
    static LevelSceneTesterSession fromLevelScene(final LevelScene levelScene) {
        final LevelSceneEditorTabSystem editorTabSystem = getBean(LevelSceneEditorTabSystem.class);
        final GameEngine gameEngine = getBean(GameEngine.class, getBean(CameraState.class));
        final LevelSceneTesterSession testerSession = getBean(
            LevelSceneTesterSession.class,
            editorTabSystem,
            getBean(
                DebuggerPanel.class,
                getBean(PhysicsTunerPanel.class, getBean(ConfigurationService.class)),
                getBean(InputAutomationPanel.class)
            ),
            getBean(MiddlePane.class, editorTabSystem),
            gameEngine
        );
        gameEngine.setLevelScene(levelScene);
        return testerSession;
    }
}
