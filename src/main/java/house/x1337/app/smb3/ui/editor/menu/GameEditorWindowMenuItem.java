package house.x1337.app.smb3.ui.editor.menu;

import house.x1337.app.smb3.ui.editor.GameEditorWindow;
import lombok.Getter;

import javax.swing.JMenuItem;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static lombok.AccessLevel.PROTECTED;

public abstract class GameEditorWindowMenuItem extends JMenuItem {
    @Getter(lazy = true, value = PROTECTED)
    private final GameEditorWindow parentFrame = getBean(GameEditorWindow.class);
}
