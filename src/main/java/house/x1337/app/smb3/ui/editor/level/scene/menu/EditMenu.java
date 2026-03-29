package house.x1337.app.smb3.ui.editor.level.scene.menu;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.scene.menu.edit.ClearLevelSceneMenuItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenu;

@Singleton
@RequiredArgsConstructor
public class EditMenu extends JMenu {
    private final ClearLevelSceneMenuItem clearLevelSceneMenuItem;
    @PostConstruct
    void init() {
        setText("Edit");
        add(clearLevelSceneMenuItem);
    }
}
