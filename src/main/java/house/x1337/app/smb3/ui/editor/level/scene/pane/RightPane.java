package house.x1337.app.smb3.ui.editor.level.scene.pane;

import house.x1337.app.smb3.annotation.Singleton;
import jakarta.annotation.PostConstruct;

import javax.swing.JSplitPane;

@Singleton
public class RightPane extends JSplitPane {
    public RightPane(
        final LeftPane leftPane,
        final MiddlePane middlePane
    ) {
        super(HORIZONTAL_SPLIT, leftPane, middlePane);
    }

    @PostConstruct
    void init() {
        setDividerSize(6);
        setResizeWeight(0.0); // extra space goes to the editor side
    }
}
