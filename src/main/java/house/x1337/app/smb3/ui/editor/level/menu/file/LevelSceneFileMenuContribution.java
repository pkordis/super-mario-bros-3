package house.x1337.app.smb3.ui.editor.level.menu.file;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.menu.FileMenuContribution;
import house.x1337.app.smb3.ui.editor.tab.core.BaseEditorTab;
import lombok.RequiredArgsConstructor;

import javax.swing.JMenuItem;
import java.util.List;

@Singleton
@RequiredArgsConstructor
public class LevelSceneFileMenuContribution implements FileMenuContribution {
    private final SaveLevelSceneMenuItem saveLevelSceneMenuItem;

    @Override
    public boolean appliesTo(final BaseEditorTab activeTab) {
        return activeTab instanceof LevelSceneEditorTab;
    }

    @Override
    public List<JMenuItem> getItems() {
        return List.of(saveLevelSceneMenuItem);
    }
}
