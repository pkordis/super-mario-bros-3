package house.x1337.app.smb3.util.factory;

import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.jme3.core.CameraState;
import house.x1337.app.smb3.service.ConfigurationService;
import house.x1337.app.smb3.ui.editor.level.pane.MiddlePane;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTabSystem;
import house.x1337.app.smb3.ui.editor.level.tester.DebuggerPanel;
import house.x1337.app.smb3.ui.editor.level.tester.LevelSceneTesterSession;
import house.x1337.app.smb3.ui.editor.level.tester.input.automation.InputAutomationPanel;
import house.x1337.app.smb3.ui.editor.level.tester.physics.tuning.PhysicsTunerPanel;

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
