package house.x1337.app.smb3.ui.editor.level.scene.menu.file;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.scene.browse.BrowseScenesWindow;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenuItem;

@Singleton
@RequiredArgsConstructor
public class BrowseLevelScenesMenuItem extends JMenuItem {
    @PostConstruct
    void init() {
        setText("Browse Scenes...");
        addActionListener(e -> BrowseScenesWindow.launch());
    }
}
