package house.x1337.app.smb3.ui.editor.level.tester;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
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

    @Override
    public void onLevelSceneRestart() {
        final Node rootNode = gameEngine.getRootNode();
        // Detach the current player's node by name (there is exactly one "PlayerNode" in a level run).
        // Done by name rather than via the Player handle because the Player interface exposes no node —
        // only the level player does — and the restart only ever runs in a level scene.
        final Spatial playerNode = rootNode.getChild("PlayerNode");
        if (playerNode != null) {
            playerNode.removeFromParent();
        }
        final Spatial cameraTarget = rootNode.getChild("CameraTarget");
        if (cameraTarget != null) {
            cameraTarget.removeFromParent();
        }
        for (final LevelSceneLayerType layer : LevelSceneLayerType.values()) {
            final Spatial layerGeometry = rootNode.getChild(layer.getLayerName());
            if (layerGeometry != null) {
                layerGeometry.removeFromParent();
            }
        }
        gameEngine.buildLevel();
    }
}
