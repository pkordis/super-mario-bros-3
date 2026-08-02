package house.x1337.app.smb3.ui.editor.level.menu;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.menu.levelobject.CreateCustomLevelObjectMenuItem;
import house.x1337.app.smb3.ui.editor.level.menu.levelobject.EditInteractiveSingleTiledMenuItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenu;

@Singleton
@RequiredArgsConstructor
public class LevelObjectsMenu extends JMenu {
    private final EditInteractiveSingleTiledMenuItem editInteractiveSingleTiledMenuItem;
    private final CreateCustomLevelObjectMenuItem createCustomLevelObjectMenuItem;

    @PostConstruct
    void init() {
        setText("Level Objects");
        add(buildEditInteractiveMenu());
        addSeparator();
        add(createCustomLevelObjectMenuItem);
    }

    private JMenu buildEditInteractiveMenu() {
        final JMenu submenu = new JMenu("Edit Interactive");
        submenu.add(editInteractiveSingleTiledMenuItem);
        return submenu;
    }
}
