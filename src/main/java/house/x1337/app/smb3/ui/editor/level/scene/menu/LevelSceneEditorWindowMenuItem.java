package house.x1337.app.smb3.ui.editor.level.scene.menu;

import house.x1337.app.smb3.ui.editor.level.scene.LevelSceneEditorWindow;
import lombok.Getter;

import javax.swing.JMenuItem;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static lombok.AccessLevel.PROTECTED;

public abstract class LevelSceneEditorWindowMenuItem extends JMenuItem {
    @Getter(lazy = true, value = PROTECTED)
    private final LevelSceneEditorWindow parentFrame = getBean(LevelSceneEditorWindow.class);
}
