package house.x1337.app.smb3.ui.editor.level.menu;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.menu.tile.ConfigureVirtualTilesMenuItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenu;

@Singleton
@RequiredArgsConstructor
public class TilesMenu extends JMenu {
    private final ConfigureVirtualTilesMenuItem configureVirtualTilesMenuItem;

    @PostConstruct
    void init() {
        setText("Tiles");
        add(configureVirtualTilesMenuItem);
    }
}
