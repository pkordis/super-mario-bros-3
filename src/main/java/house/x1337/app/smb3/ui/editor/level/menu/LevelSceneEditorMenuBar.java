package house.x1337.app.smb3.ui.editor.level.menu;

import house.x1337.app.smb3.annotation.Singleton;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenuBar;

@Singleton
@RequiredArgsConstructor
public class LevelSceneEditorMenuBar extends JMenuBar {
    private final FileMenu fileMenu;
    private final EditMenu editMenu;
    private final TilesMenu tilesMenu;
    private final LevelSceneMenu levelSceneMenu;

    @PostConstruct
    public void init() {
        add(fileMenu);
        add(editMenu);
        add(tilesMenu);
        add(levelSceneMenu);
    }
}
