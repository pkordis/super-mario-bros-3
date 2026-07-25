package house.x1337.app.smb3.ui.editor.level.menu;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.menu.tile.ConfigureVirtualTilesMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.tile.EditInteractiveSingleTiledMenuItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

@Singleton
@RequiredArgsConstructor
public class TilesMenu extends JMenu {
    private final ConfigureVirtualTilesMenuItem configureVirtualTilesMenuItem;
    private final EditInteractiveSingleTiledMenuItem editInteractiveSingleTiledMenuItem;

    @PostConstruct
    void init() {
        setText("Tiles");
        add(configureVirtualTilesMenuItem);
        addSeparator();
        add(buildEditInteractiveObjectsMenu());
    }

    private JMenu buildEditInteractiveObjectsMenu() {
        final JMenu submenu = new JMenu("Edit Interactive Objects");
        submenu.add(editInteractiveSingleTiledMenuItem);
        return submenu;
    }
}
