package house.x1337.app.smb3.ui.editor.level.tile.edit;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.GameEditorWindow;

import java.awt.Component;
import java.util.List;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.util.factory.NewTilesReviewWindowFactory.fromTiles;
import static javax.swing.JOptionPane.INFORMATION_MESSAGE;
import static javax.swing.JOptionPane.OK_CANCEL_OPTION;
import static javax.swing.JOptionPane.OK_OPTION;
import static javax.swing.JOptionPane.WARNING_MESSAGE;
import static javax.swing.JOptionPane.showConfirmDialog;
import static javax.swing.JOptionPane.showMessageDialog;

/**
 * The windows and dialogs of tile editing, kept behind one bean so the code that opens them can be exercised
 * without opening windows.
 */
@Singleton
public class TileWindows {
    public void openEditor(final Tile tile) {
        getBean(EditTileWindow.class, getBean(GameEditorWindow.class), tile).setVisible(true);
    }

    public void openReview(final List<Tile> tiles) {
        fromTiles(tiles).setVisible(true);
    }

    /** @return whether the author chose to go ahead */
    public boolean confirmLayerMove(final Component parent, final String message) {
        return showConfirmDialog(parent, message, "Move Tile to Another Layer", OK_CANCEL_OPTION, WARNING_MESSAGE)
            == OK_OPTION;
    }

    /** @return whether the author chose to go ahead */
    public boolean confirmLevelObjectReplacement(final Component parent, final String message) {
        return showConfirmDialog(parent, message, "Replace Level Object", OK_CANCEL_OPTION, WARNING_MESSAGE)
            == OK_OPTION;
    }

    public void sayNoUnresolvedTiles(final Component parent) {
        showMessageDialog(parent, "Every tile has a type.", "No Unresolved Tiles", INFORMATION_MESSAGE);
    }
}
