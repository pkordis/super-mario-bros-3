package house.x1337.app.smb3.ui.editor.level.menu;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.menu.scene.ActiveLayerMenu;
import house.x1337.app.smb3.ui.editor.level.menu.scene.BrowseLevelScenesMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.scene.ClearLevelSceneMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.scene.ImportFromPngLevelSceneMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.scene.LayerVisibilityMenu;
import house.x1337.app.smb3.ui.editor.level.menu.scene.NewLevelSceneMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.scene.SetLevelSceneSizeMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.scene.TestLevelSceneMenuItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenu;

@Singleton
@RequiredArgsConstructor
public class LevelSceneMenu extends JMenu {
    private final NewLevelSceneMenuItem newLevelSceneMenuItem;
    private final BrowseLevelScenesMenuItem browseLevelScenesMenuItem;
    private final ImportFromPngLevelSceneMenuItem importFromPngLevelSceneMenuItem;
    private final ClearLevelSceneMenuItem clearLevelSceneMenuItem;
    private final SetLevelSceneSizeMenuItem setLevelSceneSizeMenuItem;
    private final ActiveLayerMenu activeLayerMenu;
    private final LayerVisibilityMenu layerVisibilityMenu;
    private final TestLevelSceneMenuItem testLevelSceneMenuItem;

    @PostConstruct
    void init() {
        setText("Scene");
        add(newLevelSceneMenuItem);
        add(browseLevelScenesMenuItem);
        add(importFromPngLevelSceneMenuItem);
        addSeparator();
        add(clearLevelSceneMenuItem);
        add(setLevelSceneSizeMenuItem);
        addSeparator();
        add(activeLayerMenu);
        add(layerVisibilityMenu);
        addSeparator();
        add(testLevelSceneMenuItem);
    }
}
