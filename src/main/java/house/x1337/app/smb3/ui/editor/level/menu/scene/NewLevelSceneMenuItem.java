package house.x1337.app.smb3.ui.editor.level.menu.scene;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneTabs;
import house.x1337.app.smb3.ui.editor.menu.GameEditorWindowMenuItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.ui.editor.level.menu.scene.LevelSceneSizeRules.MIN_COLUMNS;
import static house.x1337.app.smb3.ui.editor.level.menu.scene.LevelSceneSizeRules.MIN_ROWS;
import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.KeyEvent.VK_N;
import static javax.swing.KeyStroke.getKeyStroke;

/**
 * Asks for a size, starting from the smallest one allowed, and opens a blank scene of that size in a new tab.
 */
@Singleton
@RequiredArgsConstructor
public class NewLevelSceneMenuItem extends GameEditorWindowMenuItem {
    private final LevelSceneTabs levelSceneTabs;
    private final LevelSceneSizeRules sizeRules;

    @PostConstruct
    void init() {
        setText("New Level Scene...");
        setAccelerator(getKeyStroke(VK_N, CTRL_DOWN_MASK));
        addActionListener(e -> promptNewScene());
    }

    private void promptNewScene() {
        LevelSceneSizeDialog
            .fromContext(
                getParentFrame(),
                "New Level Scene"
            )
            .configure(
                new LevelSceneDimensions(MIN_COLUMNS, MIN_ROWS),
                sizeRules
            )
            .ask()
            .ifPresent(size -> levelSceneTabs.openNew(size.columns(), size.rows()));
    }
}
