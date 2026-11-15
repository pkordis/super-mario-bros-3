package house.x1337.app.smb3.ui.editor.level.menu.scene;

import house.x1337.app.smb3.file.importer.PngFileImporter;
import house.x1337.app.smb3.service.LevelSceneService;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemiesPalettePanel;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneTabs;
import house.x1337.app.smb3.ui.editor.level.toolbar.LevelSceneTestRunTarget;
import house.x1337.app.smb3.ui.editor.tab.EditorTabSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.swing.JMenuItem;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Level &gt; Scene keeps what works on the active scene disabled while no level scene tab is active, and keeps
 * creating and opening scenes available all the time.
 */
class LevelSceneMenuEnablementTest {
    private final EditorTabSystem editorTabSystem = mock(EditorTabSystem.class);
    private final LevelSceneTabs levelSceneTabs = new LevelSceneTabs(editorTabSystem, mock(LevelSceneService.class));
    private List<JMenuItem> needingAScene;
    private List<JMenuItem> alwaysAvailable;

    @BeforeEach
    void prepare() {
        when(editorTabSystem.getActiveTab(LevelSceneEditorTab.class)).thenReturn(Optional.empty());
        final LevelSceneSizeRules sizeRules = new LevelSceneSizeRules();

        final ClearLevelSceneMenuItem clear = new ClearLevelSceneMenuItem(levelSceneTabs);
        clear.init();
        final SetLevelSceneSizeMenuItem setSize = new SetLevelSceneSizeMenuItem(
            levelSceneTabs,
            sizeRules,
            new LevelSceneResizeRefusal()
        );
        setSize.init();
        final ActiveLayerMenu activeLayer = new ActiveLayerMenu(levelSceneTabs, mock(EnemiesPalettePanel.class));
        activeLayer.init();
        final LayerVisibilityMenu layerVisibility = new LayerVisibilityMenu(levelSceneTabs);
        layerVisibility.init();
        final TestLevelSceneMenuItem test = new TestLevelSceneMenuItem(
            levelSceneTabs,
            mock(LevelSceneTestRunTarget.class)
        );
        test.init();
        needingAScene = List.of(clear, setSize, activeLayer, layerVisibility, test);

        final NewLevelSceneMenuItem newScene = new NewLevelSceneMenuItem(levelSceneTabs, sizeRules);
        newScene.init();
        final BrowseLevelScenesMenuItem browse = new BrowseLevelScenesMenuItem();
        browse.init();
        final ImportFromPngLevelSceneMenuItem importFromPng = new ImportFromPngLevelSceneMenuItem(
            levelSceneTabs,
            mock(PngFileImporter.class),
            mock(TileService.class)
        );
        importFromPng.init();
        alwaysAvailable = List.of(newScene, browse, importFromPng);
    }

    @Test
    @DisplayName("Without a level scene tab, only New, Browse and Import are enabled")
    void withoutALevelSceneTab() {
        // Execute
        activate(null);

        // Verify
        assertThat(needingAScene).allSatisfy(item -> assertThat(item.isEnabled()).as(item.getText()).isFalse());
        assertThat(alwaysAvailable).allSatisfy(item -> assertThat(item.isEnabled()).as(item.getText()).isTrue());
    }

    @Test
    @DisplayName("While a level scene tab is active, every Scene item is enabled, and they disable again after")
    void followsTheActiveTab() {
        // Execute & Verify
        activate(mock(LevelSceneEditorTab.class));
        assertThat(needingAScene).allSatisfy(item -> assertThat(item.isEnabled()).as(item.getText()).isTrue());
        assertThat(alwaysAvailable).allSatisfy(item -> assertThat(item.isEnabled()).as(item.getText()).isTrue());

        activate(null);
        assertThat(needingAScene).allSatisfy(item -> assertThat(item.isEnabled()).as(item.getText()).isFalse());
    }

    private void activate(final LevelSceneEditorTab tab) {
        when(editorTabSystem.getActiveTab(LevelSceneEditorTab.class)).thenReturn(Optional.ofNullable(tab));
        final ArgumentCaptor<ChangeListener> listeners = ArgumentCaptor.forClass(ChangeListener.class);
        verify(editorTabSystem, atLeastOnce()).addChangeListener(listeners.capture());
        listeners.getAllValues().forEach(listener -> listener.stateChanged(new ChangeEvent(editorTabSystem)));
    }
}
