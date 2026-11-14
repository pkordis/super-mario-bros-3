package house.x1337.app.smb3.ui.editor.level.menu.scene;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneTabs;
import house.x1337.app.smb3.ui.editor.menu.GameEditorWindowMenuItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Resizes the active scene, keeping its bottom-left corner in place. A shrink that would cut content is refused.
 */
@Singleton
@RequiredArgsConstructor
public class SetLevelSceneSizeMenuItem extends GameEditorWindowMenuItem {
    private final LevelSceneTabs levelSceneTabs;
    private final LevelSceneSizeRules sizeRules;
    private final LevelSceneResizeRefusal resizeRefusal;

    @PostConstruct
    public void init() {
        setText("Set Size...");
        levelSceneTabs.enableOnlyWhileActive(this);
        addActionListener(e -> promptSize());
    }

    private void promptSize() {
        final LevelSceneEditorTab tab = levelSceneTabs.getActiveTab();
        LevelSceneSizeDialog
            .fromContext(
                getParentFrame(),
                "Set Level Scene Size"
            )
            .configure(
                new LevelSceneDimensions(tab.getColumns(), tab.getRows()),
                sizeRules
            )
            .ask()
            .ifPresent(size -> resize(tab, size));
    }

    void resize(final LevelSceneEditorTab tab, final LevelSceneDimensions size) {
        if (size.columns() == tab.getColumns() && size.rows() == tab.getRows()) {
            return;
        }
        final Map<String, List<Coordinates>> cutContent = tab.findContentCutBy(size.columns(), size.rows());
        if (!cutContent.isEmpty()) {
            resizeRefusal.show(getParentFrame(), cutContent);
            return;
        }
        tab.resize(size.columns(), size.rows());
    }
}
