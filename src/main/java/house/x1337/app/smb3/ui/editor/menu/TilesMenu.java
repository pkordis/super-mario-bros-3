package house.x1337.app.smb3.ui.editor.menu;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.menu.tile.HandleUnresolvedTilesMenuItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenu;

@Singleton
@RequiredArgsConstructor
public class TilesMenu extends JMenu {
    private final HandleUnresolvedTilesMenuItem handleUnresolvedTilesMenuItem;

    @PostConstruct
    void init() {
        setText("Tiles");
        add(handleUnresolvedTilesMenuItem);
    }
}
