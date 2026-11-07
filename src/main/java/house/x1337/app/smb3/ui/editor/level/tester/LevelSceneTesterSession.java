package house.x1337.app.smb3.ui.editor.level.tester;

import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.event.TestSessionEventListener;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.engine.LevelRunStart;
import house.x1337.app.smb3.game.level.pipe.LevelSceneHost;
import house.x1337.app.smb3.game.level.pipe.PipePortalTransit;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.model.event.PipePortalTransitRequested;
import house.x1337.app.smb3.service.LevelSceneService;
import house.x1337.app.smb3.service.PipePortalService;
import house.x1337.app.smb3.ui.editor.level.pane.MiddlePane;
import house.x1337.app.smb3.ui.editor.level.tab.GameEngineTesterTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTabSystem;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.util.concurrent.atomic.AtomicBoolean;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

@Prototype
@RequiredArgsConstructor
public class LevelSceneTesterSession implements TestSessionEventListener, LevelSceneHost {
    private final AtomicBoolean restartPending = new AtomicBoolean(false);

    @Getter
    private final GameEngine gameEngine;
    private final DebuggerPanel debuggerPanel;
    private final LevelSceneEditorTabSystem editorTabs;
    private final MiddlePane middlePane;

    private LevelScene originalLevelScene;
    private PipePortalTransit pipePortalTransit;
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
    public void requestLevelSceneRestart() {
        if (restartPending.compareAndSet(false, true)) {
            gameEngine.enqueue(this::runPendingRestart);
        }
    }

    private void runPendingRestart() {
        try {
            onLevelSceneRestart();
        } finally {
            restartPending.set(false);
        }
    }

    @Override
    public void onLevelSceneRestart() {
        if (pipePortalTransit != null) {
            pipePortalTransit.cancel();
        }
        detachLevelRun();
        if (originalLevelScene != null) {
            gameEngine.setLevelScene(originalLevelScene);
        }
        gameEngine.buildLevel();
    }

    @Override
    public void onPipePortalTransitRequested(final PipePortalTransitRequested event) {
        pipePortalTransit().onPipePortalTransitRequested(event);
    }

    @Override
    public void swapLevelScene(final LevelScene levelScene, final LevelRunStart start) {
        if (originalLevelScene == null) {
            originalLevelScene = gameEngine.getLevelScene();
        }
        detachLevelRun();
        gameEngine.setLevelScene(levelScene);
        gameEngine.buildLevel(start);
    }

    private PipePortalTransit pipePortalTransit() {
        if (pipePortalTransit == null) {
            pipePortalTransit = getBean(
                PipePortalTransit.class,
                gameEngine,
                getBean(PipePortalService.class),
                getBean(LevelSceneService.class),
                gameEngine.getFadingEngine(),
                this
            );
        }
        return pipePortalTransit;
    }

    private void detachLevelRun() {
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
    }
}
