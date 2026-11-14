package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.service.LevelSceneService;
import house.x1337.app.smb3.ui.editor.EditorStartup;
import house.x1337.app.smb3.ui.editor.tab.EditorTabSystem;
import house.x1337.app.smb3.util.factory.LevelSceneEditorTabFactory;
import lombok.RequiredArgsConstructor;

import javax.swing.JComponent;
import javax.swing.event.ChangeListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

/**
 * The level scene tabs among the editor's tabs, and the level scene tester's tab.
 *
 * <p>A tab is tracked by its scene id once it holds a stored scene, so opening that scene again focuses the tab
 * instead of opening a second one. New and imported scenes stay untracked until they are saved.
 */
@Singleton
@RequiredArgsConstructor
public class LevelSceneTabs implements EditorStartup {
    private final EditorTabSystem editorTabSystem;
    private final LevelSceneService levelSceneService;
    private final Map<String, LevelSceneEditorTab> trackedTabs = new HashMap<>();
    private GameEngineTesterTab testerTab;

    @Override
    public void onEditorStart() {
        levelSceneService.findLastEdited().ifPresent(this::open);
    }

    /**
     * @return the active tab when it holds a level scene, otherwise {@code null}
     */
    public LevelSceneEditorTab getActiveTab() {
        return editorTabSystem.getActiveTab(LevelSceneEditorTab.class).orElse(null);
    }

    public boolean hasActiveTab() {
        return getActiveTab() != null;
    }

    public List<LevelSceneEditorTab> getLevelSceneTabs() {
        return editorTabSystem.getTabs(LevelSceneEditorTab.class);
    }

    /**
     * Listens for every change of the editor's active tab, whatever kind of tab becomes active.
     */
    public void addActiveTabListener(final ChangeListener listener) {
        editorTabSystem.addChangeListener(listener);
    }

    /**
     * Keeps {@code component} enabled only while a level scene tab is the active one.
     */
    public void enableOnlyWhileActive(final JComponent component) {
        component.setEnabled(hasActiveTab());
        addActiveTabListener(event -> component.setEnabled(hasActiveTab()));
    }

    /**
     * Focuses the tab already holding {@code levelScene}, or opens one for it.
     */
    public void open(final LevelScene levelScene) {
        final Optional<LevelSceneEditorTab> trackedTab = getTrackedTab(levelScene);
        if (trackedTab.isPresent()) {
            if (editorTabSystem.select(trackedTab.get())) {
                return;
            }
            forget(trackedTab.get());
        }
        final LevelSceneEditorTab newTab = LevelSceneEditorTabFactory.fromScene(levelScene);
        editorTabSystem.addTab(newTab);
        track(newTab);
    }

    /**
     * Opens a tab for a scene that is not stored yet. It is tracked once it is saved.
     */
    public void openUnsaved(final LevelSceneEditorTab tab) {
        editorTabSystem.addTab(tab);
    }

    /**
     * Opens a tab holding a blank scene of the given size, with every layer.
     */
    public void openNew(final int columns, final int rows) {
        final LevelSceneEditorTab tab = getBean(LevelSceneEditorTab.class);
        tab.newScene(rows, columns);
        openUnsaved(tab);
    }

    public void track(final LevelSceneEditorTab tab) {
        trackedTabs.put(tab.getSceneId(), tab);
        tab.updateTabTitle();
    }

    public void forget(final LevelSceneEditorTab tab) {
        trackedTabs.values().removeIf(tracked -> tracked == tab);
    }

    public Optional<LevelSceneEditorTab> getTrackedTab(final LevelScene levelScene) {
        return Optional.ofNullable(trackedTabs.get(levelScene.getId()));
    }

    public void addTesterTab(final GameEngineTesterTab tab) {
        testerTab = tab;
        editorTabSystem.addTab(tab);
    }

    public void removeTesterTab() {
        if (testerTab != null) {
            editorTabSystem.removeTab(testerTab);
            testerTab = null;
        }
    }
}
