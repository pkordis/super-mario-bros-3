package house.x1337.app.smb3.ui.editor.level.menu;

import house.x1337.app.smb3.file.importer.PngFileImporter;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemiesPalettePanel;
import house.x1337.app.smb3.ui.editor.level.menu.levelobject.CreateCustomLevelObjectMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.levelobject.CreateEnemyFromImageMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.levelobject.EditInteractiveSingleTiledMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.levelobject.PipePortalLinkageMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.scene.ActiveLayerMenu;
import house.x1337.app.smb3.ui.editor.level.menu.scene.BrowseLevelScenesMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.scene.ClearLevelSceneMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.scene.ImportFromPngLevelSceneMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.scene.LayerVisibilityMenu;
import house.x1337.app.smb3.ui.editor.level.menu.scene.LevelSceneResizeRefusal;
import house.x1337.app.smb3.ui.editor.level.menu.scene.LevelSceneSizeRules;
import house.x1337.app.smb3.ui.editor.level.menu.scene.NewLevelSceneMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.scene.SetLevelSceneSizeMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.scene.TestLevelSceneMenuItem;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneTabs;
import house.x1337.app.smb3.ui.editor.level.toolbar.LevelSceneTestRunTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Level holds Scene, then Objects. Scene groups creating and opening scenes, changing the active one, its layers,
 * and testing it.
 */
class LevelMenuTest {
    private final LevelSceneTabs levelSceneTabs = mock(LevelSceneTabs.class);
    private final LevelSceneSizeRules sizeRules = new LevelSceneSizeRules();

    @Test
    @DisplayName("Level holds Scene, then Objects")
    void sceneThenObjects() {
        // Prepare
        final LevelSceneMenu sceneMenu = sceneMenu(new ArrayList<>());
        final LevelObjectsMenu objectsMenu = objectsMenu(new ArrayList<>());
        final LevelMenu levelMenu = new LevelMenu(sceneMenu, objectsMenu);

        // Execute
        levelMenu.init();
        sceneMenu.init();
        objectsMenu.init();

        // Verify
        assertThat(levelMenu.getText()).isEqualTo("Level");
        assertThat(levelMenu.getItemCount()).isEqualTo(2);
        assertThat(levelMenu.getItem(0)).isSameAs(sceneMenu);
        assertThat(sceneMenu.getText()).isEqualTo("Scene");
        assertThat(levelMenu.getItem(1)).isSameAs(objectsMenu);
        assertThat(objectsMenu.getText()).isEqualTo("Objects");
    }

    @Test
    @DisplayName("Scene lists New, Browse and Import, then Clear and Set Size, then the layers, then Test")
    void sceneItemsInOrder() {
        // Prepare
        final List<JMenuItem> expected = new ArrayList<>();
        final LevelSceneMenu sceneMenu = sceneMenu(expected);

        // Execute
        sceneMenu.init();

        // Verify
        final List<JMenuItem> items = new ArrayList<>();
        for (int index = 0; index < sceneMenu.getItemCount(); index++) {
            items.add(sceneMenu.getItem(index));
        }
        assertThat(items).as("A null entry is a separator").containsExactly(
            expected.get(0), expected.get(1), expected.get(2), null,
            expected.get(3), expected.get(4), null,
            expected.get(5), expected.get(6), null,
            expected.get(7)
        );
    }

    private LevelSceneMenu sceneMenu(final List<JMenuItem> items) {
        final NewLevelSceneMenuItem newScene = new NewLevelSceneMenuItem(levelSceneTabs, sizeRules);
        final BrowseLevelScenesMenuItem browse = new BrowseLevelScenesMenuItem();
        final ImportFromPngLevelSceneMenuItem importFromPng = new ImportFromPngLevelSceneMenuItem(
            levelSceneTabs,
            mock(PngFileImporter.class),
            mock(TileService.class)
        );
        final ClearLevelSceneMenuItem clear = new ClearLevelSceneMenuItem(levelSceneTabs);
        final SetLevelSceneSizeMenuItem setSize = new SetLevelSceneSizeMenuItem(
            levelSceneTabs,
            sizeRules,
            new LevelSceneResizeRefusal()
        );
        final ActiveLayerMenu activeLayer = new ActiveLayerMenu(levelSceneTabs, mock(EnemiesPalettePanel.class));
        final LayerVisibilityMenu layerVisibility = new LayerVisibilityMenu(levelSceneTabs);
        final TestLevelSceneMenuItem test = new TestLevelSceneMenuItem(
            levelSceneTabs,
            mock(LevelSceneTestRunTarget.class)
        );
        items.addAll(List.of(newScene, browse, importFromPng, clear, setSize, activeLayer, layerVisibility, test));
        return new LevelSceneMenu(newScene, browse, importFromPng, clear, setSize, activeLayer, layerVisibility, test);
    }

    private LevelObjectsMenu objectsMenu(final List<JMenuItem> items) {
        final EditInteractiveSingleTiledMenuItem editInteractive = new EditInteractiveSingleTiledMenuItem();
        final CreateCustomLevelObjectMenuItem createCustom = new CreateCustomLevelObjectMenuItem();
        final CreateEnemyFromImageMenuItem createEnemy = new CreateEnemyFromImageMenuItem(
            mock(PngFileImporter.class),
            mock(TileService.class)
        );
        final PipePortalLinkageMenuItem linkage = new PipePortalLinkageMenuItem();
        items.addAll(List.of(editInteractive, createCustom, createEnemy, linkage));
        return new LevelObjectsMenu(editInteractive, createCustom, createEnemy, linkage);
    }
}
