package house.x1337.app.smb3.ui.editor.menu;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.menu.LevelMenu;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenuBar;

@Singleton
@RequiredArgsConstructor
public class EditorMenuBar extends JMenuBar {
    private final FileMenu fileMenu;
    private final TilesMenu tilesMenu;
    private final LevelMenu levelMenu;

    @PostConstruct
    public void init() {
        add(fileMenu);
        add(tilesMenu);
        add(levelMenu);
    }
}
