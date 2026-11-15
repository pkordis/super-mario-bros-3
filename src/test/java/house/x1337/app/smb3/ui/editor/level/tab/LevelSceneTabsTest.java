package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.service.LevelSceneService;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyLevelSceneGridStamper;
import house.x1337.app.smb3.ui.editor.level.service.PaletteSelectionService;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import house.x1337.app.smb3.ui.editor.tab.EditorTabSystem;
import house.x1337.app.smb3.util.factory.LevelSceneImportRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A stored scene has at most one tab: opening it again focuses that tab, until the tab is closed. Scenes that are
 * not stored yet are never tracked.
 */
class LevelSceneTabsTest {
    private final EditorTabSystem editorTabSystem = mock(EditorTabSystem.class);
    private final LevelSceneTabs levelSceneTabs = new LevelSceneTabs(editorTabSystem, mock(LevelSceneService.class));
    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(LevelSceneEditorGrid.class), any(), any()))
            .thenReturn(mock(LevelSceneEditorGrid.class));
        staticBeanFactory.when(() -> StaticBeanFactory.getBean(EditorTabSystem.class)).thenReturn(editorTabSystem);
        staticBeanFactory.when(() -> StaticBeanFactory.getBean(LevelSceneTabs.class)).thenReturn(levelSceneTabs);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Opening a scene whose tab is open focuses that tab instead of opening another")
    void openingAnOpenSceneFocusesItsTab() {
        // Prepare
        final LevelSceneEditorTab tab = tabHolding("scene-1");
        levelSceneTabs.track(tab);
        when(editorTabSystem.select(tab)).thenReturn(true);

        // Execute
        levelSceneTabs.open(scene("scene-1"));

        // Verify
        verify(editorTabSystem).select(tab);
        verify(editorTabSystem, never()).addTab(any());
    }

    @Test
    @DisplayName("Closing a level scene tab forgets its scene")
    void closingForgetsTheScene() {
        // Prepare
        final LevelSceneEditorTab tab = tabHolding("scene-1");
        levelSceneTabs.track(tab);

        // Execute
        tab.onClose();

        // Verify
        assertThat(levelSceneTabs.getTrackedTab(scene("scene-1"))).isEmpty();
    }

    @Test
    @DisplayName("A new scene opens in a new tab of the chosen size, and is not tracked")
    void newSceneOpensUntracked() {
        // Prepare
        final LevelSceneEditorTab tab = mock(LevelSceneEditorTab.class);
        staticBeanFactory.when(() -> StaticBeanFactory.getBean(LevelSceneEditorTab.class)).thenReturn(tab);

        // Execute
        levelSceneTabs.openNew(20, 30);

        // Verify
        verify(tab).newScene(30, 20);
        verify(editorTabSystem).addTab(tab);
        verify(tab, never()).updateTabTitle();
        assertThat(levelSceneTabs.getTrackedTab(scene(null))).isEmpty();
    }

    @Test
    @DisplayName("There is no active level scene tab while a tab of another kind is active")
    void otherTabsAreNotLevelSceneTabs() {
        // Prepare
        when(editorTabSystem.getActiveTab(LevelSceneEditorTab.class)).thenReturn(Optional.empty());

        // Execute & Verify
        assertThat(levelSceneTabs.getActiveTab()).isNull();
        assertThat(levelSceneTabs.hasActiveTab()).isFalse();
    }

    private LevelSceneEditorTab tabHolding(final String sceneId) {
        final LevelSceneEditorTab tab = new LevelSceneEditorTab(
            mock(LevelSceneImportRouter.class),
            mock(TilePalettePanel.class),
            new PaletteSelectionService(),
            mock(EnemyLevelSceneGridStamper.class)
        );
        tab.setSceneId(sceneId);
        return tab;
    }

    private LevelScene scene(final String id) {
        final LevelScene scene = mock(LevelScene.class);
        when(scene.getId()).thenReturn(id);
        return scene;
    }
}
