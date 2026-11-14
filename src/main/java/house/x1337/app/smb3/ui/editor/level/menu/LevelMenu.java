package house.x1337.app.smb3.ui.editor.level.menu;

import house.x1337.app.smb3.annotation.Singleton;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenu;

@Singleton
@RequiredArgsConstructor
public class LevelMenu extends JMenu {
    private final LevelSceneMenu levelSceneMenu;
    private final LevelObjectsMenu levelObjectsMenu;

    @PostConstruct
    void init() {
        setText("Level");
        add(levelSceneMenu);
        add(levelObjectsMenu);
    }
}
