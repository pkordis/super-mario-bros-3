package house.x1337.app.smb3.model.ui.tile;

import javax.swing.JLabel;
import javax.swing.JPanel;

public record TileGroup(
    JLabel header,
    JPanel gridPanel
) {}
