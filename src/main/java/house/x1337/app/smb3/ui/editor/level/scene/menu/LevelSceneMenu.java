package house.x1337.app.smb3.ui.editor.level.scene.menu;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.scene.menu.scene.level.ActiveLayerMenu;
import house.x1337.app.smb3.ui.editor.level.scene.menu.scene.level.LayerVisibilityMenu;
import house.x1337.app.smb3.ui.editor.level.scene.menu.scene.level.SetLevelSceneSizeMenuItem;
import house.x1337.app.smb3.ui.editor.level.scene.menu.scene.level.TestLevelSceneMenuItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenu;

@Singleton
@RequiredArgsConstructor
public class LevelSceneMenu extends JMenu {
    private final SetLevelSceneSizeMenuItem setLevelSceneSize;
    private final TestLevelSceneMenuItem testLevelSceneMenuItem;
    private final LayerVisibilityMenu layerVisibilityMenu;
    private final ActiveLayerMenu activeLayerMenu;

    @PostConstruct
    void init() {
        setText("Level Scene");
        add(setLevelSceneSize);
        addSeparator();
        add(activeLayerMenu);
        add(layerVisibilityMenu);
        addSeparator();
        add(testLevelSceneMenuItem);
    }
}
