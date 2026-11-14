package house.x1337.app.smb3.ui.editor.level.menu.scene;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.game.Coordinates;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import java.util.Map;

import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.NORTH;
import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;

/**
 * Tells the author a resize was refused because it would cut content, listing every cell in the way.
 */
@Singleton
public class LevelSceneResizeRefusal {
    public void show(final Component parent, final Map<String, List<Coordinates>> cutContent) {
        final JTextArea cells = new JTextArea(describe(cutContent));
        cells.setEditable(false);
        cells.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        final JScrollPane scrollPane = new JScrollPane(cells);
        scrollPane.setPreferredSize(new Dimension(380, 240));

        final JPanel message = new JPanel(new BorderLayout(0, 8));
        message.add(new JLabel(
            "<html>There are objects in the area the new size would remove.<br>"
                + "Clear these cells first, or choose a size that keeps them.</html>"
        ), NORTH);
        message.add(scrollPane, CENTER);
        showMessageDialog(parent, message, "Cannot Resize Scene", ERROR_MESSAGE);
    }

    /**
     * One heading per layer or marker, followed by its cells in the same form as the editor's status bar.
     */
    String describe(final Map<String, List<Coordinates>> cutContent) {
        final StringBuilder text = new StringBuilder();
        cutContent.forEach((group, cells) -> {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(group).append('\n');
            for (final Coordinates cell : cells) {
                text.append("  col=").append(cell.x()).append("  row=").append(cell.y()).append('\n');
            }
        });
        return text.toString();
    }
}
