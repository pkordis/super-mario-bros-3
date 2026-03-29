package house.x1337.app.smb3.ui.editor.level.scene.tab.core;

import house.x1337.app.smb3.ui.editor.level.scene.tab.LevelSceneEditorTabSystem;
import lombok.Getter;

import javax.swing.JPanel;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static lombok.AccessLevel.PROTECTED;

@Getter(PROTECTED)
public abstract class BaseLevelSceneTab extends JPanel {
    private final LevelSceneEditorTabSystem editorTabSystem = getBean(LevelSceneEditorTabSystem.class);
}
