package house.x1337.app.smb3.ui.editor.menu;

import house.x1337.app.smb3.annotation.Singleton;
import jakarta.annotation.PostConstruct;

import javax.swing.JMenu;

@Singleton
public class TilesMenu extends JMenu {
    @PostConstruct
    void init() {
        setText("Tiles");
    }
}
