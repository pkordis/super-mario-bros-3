package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.ui.editor.level.tab.core.BaseLevelSceneTabSystem;
import house.x1337.app.smb3.ui.editor.level.tester.LevelSceneTesterSession;
import house.x1337.app.smb3.util.factory.LevelSceneEditorTabFactory;
import lombok.RequiredArgsConstructor;

import java.awt.Component;
import java.util.Optional;

@Singleton
@RequiredArgsConstructor
public class LevelSceneEditorTabSystem extends BaseLevelSceneTabSystem {
    private GameEngineTesterTab gameEngineTesterTab;

    public LevelSceneEditorTab getActiveTab() {
        final Component selected = getSelectedComponent();
        return (selected instanceof LevelSceneEditorTab panel) ? panel : null;
    }

    public void addTab(final LevelSceneEditorTab tab) {
        final String title = tab.resolveTitle();
        addTab(title, tab);

        final int newIndex = indexOfComponent(tab);
        installCloseButton(newIndex);
        setSelectedIndex(newIndex);
    }

    public void open(final LevelScene levelScene) {
        // If this scene is already open, just focus its tab
        final Optional<LevelSceneEditorTab> trackedTab = getTrackedTab(levelScene);
        if (trackedTab.isPresent()) {
            final LevelSceneEditorTab alreadyOpen = trackedTab.get();
            final int index = indexOfComponent(alreadyOpen);
            if (index >= 0) {
                setSelectedIndex(index);
                return;
            }
            // Tab was somehow removed but still tracked - clean up and fall through
            untrack(alreadyOpen);
        }

        // Create a new tab for this scene
        final LevelSceneEditorTab newTab = LevelSceneEditorTabFactory.fromScene(levelScene);
        addTab(newTab);
        track(newTab);
    }

    public void addGameEngineTesterTab(
        final GameEngineTesterTab tab,
        final LevelSceneTesterSession session
    ) {
        this.gameEngineTesterTab = tab;
        addTab("Level Scene Tester", gameEngineTesterTab);

        final int tabIndex = indexOfComponent(gameEngineTesterTab);
        installCloseButton(tabIndex).addActionListener(e -> session.stop());
        setSelectedIndex(tabIndex);
    }

    public void removeGameEngineTesterTab() {
        if (gameEngineTesterTab != null) {
            final int index = indexOfComponent(gameEngineTesterTab);
            if (index >= 0) {
                removeTabAt(index);
            }
            gameEngineTesterTab = null;
        }
    }
}
