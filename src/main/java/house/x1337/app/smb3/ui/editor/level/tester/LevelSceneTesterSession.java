package house.x1337.app.smb3.ui.editor.level.tester;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.event.TestSessionEventListener;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.ui.editor.level.pane.MiddlePane;
import house.x1337.app.smb3.ui.editor.level.tab.GameEngineTesterTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTabSystem;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.swing.JPanel;
import java.awt.Dimension;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

@Prototype
@RequiredArgsConstructor
public class LevelSceneTesterSession implements TestSessionEventListener {
    private final LevelSceneEditorTabSystem editorTabs;
    private final DebuggerPanel debuggerPanel;
    private final MiddlePane middlePane;
    @Getter
    private final GameEngine gameEngine;

    private boolean stopping;

    public void start() {
        middlePane.setRightComponent(debuggerPanel);
        middlePane.setDividerSize(6);
        middlePane.setResizeWeight(0.7);
        middlePane.revalidate();
        middlePane.repaint();

        gameEngine.start();

        editorTabs.addGameEngineTesterTab(getBean(GameEngineTesterTab.class, gameEngine), this);
    }

    public void stop() {
        if (stopping) {
            return;
        }
        stopping = true;

        gameEngine.stop();

        // Remove the test tab
        editorTabs.removeGameEngineTesterTab();

        // Dispose of the debugger panel and its resources
        debuggerPanel.dispose();

        // Hide the right-side debugger area
        final JPanel emptyRight = new JPanel();
        emptyRight.setMinimumSize(new Dimension(0, 0));
        emptyRight.setPreferredSize(new Dimension(0, 0));
        middlePane.setRightComponent(emptyRight);
        middlePane.setDividerSize(0);
        middlePane.setResizeWeight(1.0);
        middlePane.revalidate();
        middlePane.repaint();
    }

    @Override
    public void onGameEngineStopped() {
        stop();
    }
}
