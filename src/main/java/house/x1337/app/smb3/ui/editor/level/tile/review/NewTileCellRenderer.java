package house.x1337.app.smb3.ui.editor.level.tile.review;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.model.ui.tile.Tile;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.HashMap;
import java.util.Map;

import static house.x1337.app.smb3.model.ui.tile.TileCapabilities.THUMB_SIZE;
import static java.awt.FlowLayout.LEFT;
import static javax.swing.SwingConstants.CENTER;

@Prototype
public final class NewTileCellRenderer extends JPanel implements ListCellRenderer<Tile> {
    private final JLabel thumbnailLabel = new JLabel();
    private final JLabel infoLabel = new JLabel();

    // Thumbnail cache (tile id → scaled ImageIcon)
    private final Map<Integer, ImageIcon> thumbnailCache = new HashMap<>();

    @PostConstruct
    void init() {
        setLayout(new FlowLayout(LEFT, 6, 4));
        thumbnailLabel.setPreferredSize(new Dimension(THUMB_SIZE, THUMB_SIZE));
        infoLabel.setVerticalAlignment(CENTER);
        add(thumbnailLabel);
        add(infoLabel);
    }

    @Override
    public Component getListCellRendererComponent(
        final JList<? extends Tile> list,
        final Tile tile,
        final int index,
        final boolean isSelected,
        final boolean cellHasFocus
    ) {
        setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
        setOpaque(true);
        infoLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());

        // Thumbnail
        thumbnailLabel.setIcon(getThumbnail(tile));

        // Info text
        final String sha256 = tile.getSha256();
        final String id = sha256 != null ? sha256 : String.valueOf(tile.getId());
        final String shortId = id.length() > 10 ? id.substring(0, 8) + "…" : id;
        final String typeInfo = tile.getType() != null ? tile.getType().getLabel() : "no type";
        infoLabel.setText("<html><b>" + shortId + "</b><br><small><i>" + typeInfo + "</i></small></html>");

        return this;
    }

    private ImageIcon getThumbnail(final Tile tile) {
        return thumbnailCache.computeIfAbsent(
            tile.getId(),
            id -> tile.toThumbnail()
        );
    }
}
