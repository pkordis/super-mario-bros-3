package house.x1337.app.smb3.util.factory;

import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.factory.GameEngineFactory;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.service.ConfigurationService;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneTabs;
import house.x1337.app.smb3.ui.editor.level.tester.DebuggerPanel;
import house.x1337.app.smb3.ui.editor.level.tester.LevelSceneTesterSession;
import house.x1337.app.smb3.ui.editor.level.tester.input.automation.InputAutomationPanel;
import house.x1337.app.smb3.ui.editor.level.tester.physics.tuning.PhysicsTunerPanel;
import house.x1337.app.smb3.ui.editor.pane.MiddlePane;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface LevelSceneTesterSessionFactory {
    static LevelSceneTesterSession fromLevelScene(final LevelScene levelScene) {
        final GameEngine gameEngine = GameEngineFactory.start();
        final LevelSceneTesterSession testerSession = getBean(
            LevelSceneTesterSession.class,
            gameEngine,
            getBean(
                DebuggerPanel.class,
                getBean(PhysicsTunerPanel.class, getBean(ConfigurationService.class)),
                getBean(InputAutomationPanel.class)
            ),
            getBean(LevelSceneTabs.class),
            getBean(MiddlePane.class)
        );
        gameEngine.setupLevel(levelScene);
        return testerSession;
    }
}
