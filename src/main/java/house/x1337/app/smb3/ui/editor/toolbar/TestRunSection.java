package house.x1337.app.smb3.ui.editor.toolbar;

import house.x1337.app.smb3.annotation.Singleton;
import lombok.RequiredArgsConstructor;

import javax.swing.JComponent;
import java.util.List;

@Singleton
@RequiredArgsConstructor
public class TestRunSection implements EditorToolBarSection {
    private final RunButton runButton;

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public List<JComponent> getTools() {
        return List.of(runButton);
    }
}
