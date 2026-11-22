package house.x1337.app.smb3.ui.editor.level.enemy.edit;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.ui.editor.GameEditorWindow;

import java.awt.Component;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static javax.swing.JOptionPane.OK_CANCEL_OPTION;
import static javax.swing.JOptionPane.OK_OPTION;
import static javax.swing.JOptionPane.WARNING_MESSAGE;
import static javax.swing.JOptionPane.showConfirmDialog;

/**
 * The windows and dialogs of enemy editing, kept behind one bean so the code that opens them can be exercised
 * without opening windows.
 */
@Singleton
public class EnemyWindows {
    public void openEditor(final EnemyStamp enemy) {
        getBean(EditEnemyWindow.class, getBean(GameEditorWindow.class), enemy).setVisible(true);
    }

    /** @return whether the author chose to go ahead */
    public boolean confirmSharedParts(final Component parent, final String message) {
        return showConfirmDialog(parent, message, "Edit Shared Parts", OK_CANCEL_OPTION, WARNING_MESSAGE) == OK_OPTION;
    }
}
