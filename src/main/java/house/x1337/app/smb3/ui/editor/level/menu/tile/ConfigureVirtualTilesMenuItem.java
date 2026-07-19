package house.x1337.app.smb3.ui.editor.level.menu.tile;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.menu.LevelSceneEditorWindowMenuItem;
import house.x1337.app.smb3.ui.editor.level.tile.configuration.virtual.ConfigureVirtualTilesWindow;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

@Singleton
@RequiredArgsConstructor
public class ConfigureVirtualTilesMenuItem extends LevelSceneEditorWindowMenuItem {
    private final TileService tileService;

    @PostConstruct
    void init() {
        setText("Configure Virtual Tiles...");
        addActionListener(e -> openWindow());
    }

    private void openWindow() {
        final ConfigureVirtualTilesWindow window = getBean(
            ConfigureVirtualTilesWindow.class,
            getParentFrame(),
            tileService
        );
        window.setVisible(true);
    }
}
