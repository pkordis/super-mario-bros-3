package house.x1337.app.smb3.ui.editor.pane;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.pane.LevelScenePalettesPane;
import jakarta.annotation.PostConstruct;

import javax.swing.JSplitPane;

@Singleton
public class EditorContentPane extends JSplitPane {
    public EditorContentPane(
        final LevelScenePalettesPane palettesPane,
        final MiddlePane middlePane
    ) {
        super(HORIZONTAL_SPLIT, palettesPane, middlePane);
    }

    @PostConstruct
    void init() {
        setDividerSize(6);
        setResizeWeight(0.0); // extra space goes to the editor side
    }
}
