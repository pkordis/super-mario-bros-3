package house.x1337.app.smb3.ui.editor.level.menu.levelobject;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.tile.configuration.interactive.single.EditInteractiveSingleTiledWindow;
import house.x1337.app.smb3.ui.editor.menu.GameEditorWindowMenuItem;
import jakarta.annotation.PostConstruct;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

@Singleton
public class EditInteractiveSingleTiledMenuItem extends GameEditorWindowMenuItem {
    @PostConstruct
    void init() {
        setText("Single Tiled...");
        addActionListener(e -> openWindow());
    }

    private void openWindow() {
        final EditInteractiveSingleTiledWindow window = getBean(
            EditInteractiveSingleTiledWindow.class,
            getParentFrame()
        );
        window.setVisible(true);
    }
}
