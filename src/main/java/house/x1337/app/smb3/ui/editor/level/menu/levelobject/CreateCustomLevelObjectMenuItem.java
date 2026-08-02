package house.x1337.app.smb3.ui.editor.level.menu.levelobject;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.levelobject.create.CreateCustomLevelObjectDialog;
import house.x1337.app.smb3.ui.editor.level.menu.LevelSceneEditorWindowMenuItem;
import jakarta.annotation.PostConstruct;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

@Singleton
public class CreateCustomLevelObjectMenuItem extends LevelSceneEditorWindowMenuItem {

    @PostConstruct
    void init() {
        setText("Create Custom...");
        addActionListener(e -> openDialog());
    }

    private void openDialog() {
        final CreateCustomLevelObjectDialog dialog = getBean(
            CreateCustomLevelObjectDialog.class,
            getParentFrame()
        );
        dialog.setVisible(true);
    }
}
