package house.x1337.app.smb3.ui.editor.level.menu.scene;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneTabs;
import house.x1337.app.smb3.ui.editor.menu.GameEditorWindowMenuItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.InputEvent.SHIFT_DOWN_MASK;
import static java.awt.event.KeyEvent.VK_C;
import static javax.swing.JOptionPane.YES_NO_OPTION;
import static javax.swing.JOptionPane.YES_OPTION;
import static javax.swing.JOptionPane.showConfirmDialog;
import static javax.swing.KeyStroke.getKeyStroke;

/**
 * Empties the active scene but keeps its identity, so saving afterwards overwrites the stored scene.
 */
@Singleton
@RequiredArgsConstructor
public class ClearLevelSceneMenuItem extends GameEditorWindowMenuItem {
    private final LevelSceneTabs levelSceneTabs;

    @PostConstruct
    void init() {
        setText("Clear Level Scene");
        setAccelerator(getKeyStroke(VK_C, CTRL_DOWN_MASK | SHIFT_DOWN_MASK));
        levelSceneTabs.enableOnlyWhileActive(this);
        addActionListener(e -> {
            final int result = showConfirmDialog(getParentFrame(), "Clear all tiles?", "Clear Scene", YES_NO_OPTION);
            if (result == YES_OPTION) {
                levelSceneTabs.getActiveTab().clearScene();
            }
        });
    }
}
