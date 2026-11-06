package house.x1337.app.smb3.ui.editor.level.virtualobject;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.VirtualObjectType;

import java.awt.Component;
import java.util.Optional;

import static javax.swing.JOptionPane.WARNING_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;
import static javax.swing.SwingUtilities.getWindowAncestor;

/**
 * The dialogs a pipe portal placement can raise, kept behind one bean so the placement logic in
 * {@code LevelSceneEditorTab} can be exercised without opening windows.
 */
@Singleton
public class PipePortalPrompt {
    /**
     * Opens the Place Pipe Portal window and waits for it to close.
     *
     * @param parent the component the window is centred on
     * @param type   the kind of portal being placed
     * @param row    the anchor's row
     * @param column the anchor's column
     * @return what the author entered, or empty when they cancelled
     */
    public Optional<PipePortalDetails> ask(
        final Component parent,
        final VirtualObjectType type,
        final int row,
        final int column
    ) {
        final PlacePipePortalWindow window = PlacePipePortalWindow.launch(getWindowAncestor(parent), type, row, column);
        window.setVisible(true);
        return window.getResult();
    }

    /** Says why a portal cannot go where the author clicked. */
    public void warn(final Component parent, final String reason) {
        showMessageDialog(parent, reason, "Cannot Place Pipe Portal", WARNING_MESSAGE);
    }
}
