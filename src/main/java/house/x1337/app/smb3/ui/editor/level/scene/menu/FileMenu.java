package house.x1337.app.smb3.ui.editor.level.scene.menu;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.scene.menu.file.BrowseLevelScenesMenuItem;
import house.x1337.app.smb3.ui.editor.level.scene.menu.file.ExitMenuItem;
import house.x1337.app.smb3.ui.editor.level.scene.menu.file.ImportFromPngLevelSceneMenuItem;
import house.x1337.app.smb3.ui.editor.level.scene.menu.file.NewLevelSceneMenuItem;
import house.x1337.app.smb3.ui.editor.level.scene.menu.file.SaveLevelSceneMenuItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenu;

@Singleton
@RequiredArgsConstructor
public class FileMenu extends JMenu {
    private final NewLevelSceneMenuItem newLevelSceneMenuItem;
    private final SaveLevelSceneMenuItem saveLevelSceneMenuItem;
    private final BrowseLevelScenesMenuItem browseLevelScenesMenuItem;
    private final ImportFromPngLevelSceneMenuItem importFromPngLevelSceneMenuItem;
    private final ExitMenuItem exitMenuItem;

    @PostConstruct
    void init() {
        setText("File");
        add(newLevelSceneMenuItem);
        add(saveLevelSceneMenuItem);
        add(browseLevelScenesMenuItem);
        addSeparator();
        add(importFromPngLevelSceneMenuItem);
        addSeparator();
        add(exitMenuItem);
    }
}
