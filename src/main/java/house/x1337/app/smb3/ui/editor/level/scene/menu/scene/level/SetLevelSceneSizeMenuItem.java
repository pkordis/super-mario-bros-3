package house.x1337.app.smb3.ui.editor.level.scene.menu.scene.level;

import house.x1337.app.smb3.annotation.Singleton;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenuItem;

@Singleton
@RequiredArgsConstructor
public class SetLevelSceneSizeMenuItem extends JMenuItem {
    @PostConstruct
    public void init() {
        setText("Set Size...");
    }
}
