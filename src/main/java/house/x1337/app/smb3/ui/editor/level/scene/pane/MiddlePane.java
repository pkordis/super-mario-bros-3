package house.x1337.app.smb3.ui.editor.level.scene.pane;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.scene.tab.LevelSceneEditorTabSystem;
import jakarta.annotation.PostConstruct;

import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.Dimension;

import static java.awt.BorderLayout.CENTER;

@Singleton
public class MiddlePane extends JSplitPane {
    public MiddlePane(final LevelSceneEditorTabSystem editorTabs) {
        super(HORIZONTAL_SPLIT, buildEditorPane(editorTabs), new JPanel());
    }

    private static JPanel buildEditorPane(final LevelSceneEditorTabSystem editorTabs) {
        final JPanel editorPane = new JPanel(new BorderLayout());
        editorPane.add(editorTabs, CENTER);
        return editorPane;
    }

    @PostConstruct
    void init() {
        setDividerSize(0);
        setResizeWeight(1.0);
        // Hide the empty right side initially
        hideRightPane();
    }

    public void hideRightPane() {
        getRightComponent().setMinimumSize(new Dimension(0, 0));
        getRightComponent().setPreferredSize(new Dimension(0, 0));
    }
}
