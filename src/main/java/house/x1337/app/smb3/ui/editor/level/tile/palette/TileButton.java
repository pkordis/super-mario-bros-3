package house.x1337.app.smb3.ui.editor.level.tile.palette;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.service.SelectedTileService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.swing.GrayFilter;
import javax.swing.ImageIcon;
import javax.swing.JToggleButton;
import java.awt.Dimension;
import java.awt.Insets;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.model.ui.tile.Tile.THUMB_SIZE;
import static java.awt.Color.GRAY;
import static javax.swing.BorderFactory.createDashedBorder;
import static javax.swing.BorderFactory.createLineBorder;

@Getter
@Prototype
@RequiredArgsConstructor
public class TileButton extends JToggleButton {
    private final Tile tile;

    private ImageIcon thumbnail;
    private ImageIcon dimmedThumbnail;
    private String baseTooltip;

    /**
     * Whether this tile belongs to a layer other than the active one, and so cannot be painted yet.
     *
     * <p>Deliberately <em>not</em> modelled with {@link #setEnabled}: a disabled Swing button receives no
     * mouse events, and clicking one of these has to be possible — it is how the user switches the active
     * layer to the one the tile belongs to. So the button stays enabled and only looks unavailable.
     */
    private boolean outOfActiveLayer;

    public static TileButton fromTile(final Tile tile) {
        final TileButton button = getBean(TileButton.class, tile);
        final SelectedTileService selectedTileService = getBean(SelectedTileService.class);
        button.thumbnail = tile.toThumbnail();
        button.dimmedThumbnail = dim(button.thumbnail);
        button.setIcon(button.thumbnail);
        final int size = THUMB_SIZE + 2;
        button.setPreferredSize(new Dimension(size, size));
        button.setMinimumSize(new Dimension(size, size));
        button.setMaximumSize(new Dimension(size, size));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setBorder(createLineBorder(GRAY, 1));
        button.addActionListener(e -> {
            if (!button.isSelected()) {
                selectedTileService.clearSelection();
                return;
            }
            // Picking a tile that belongs elsewhere is read as "I want to work on that layer" rather than
            // as a mistake, so the palette follows the tile instead of refusing the click.
            if (button.isOutOfActiveLayer()) {
                getBean(TilePalettePanel.class).activateLayerOf(tile);
            }
            selectedTileService.select(button);
        });
        return button;
    }

    /**
     * Reflects this button's selection state, both in the toggle and in its border. Called by
     * {@link SelectedTileService} so that selecting anything else — another tile, or an enemy — visibly
     * releases this one.
     */
    public void markSelected(final boolean selected) {
        setSelected(selected);
        setBorder(createLineBorder(selected ? GRAY.brighter() : GRAY, 1));
    }

    /**
     * Greys this tile out, or restores it, according to whether its layer is the active one.
     *
     * <p>A dashed border and a faded thumbnail read as unavailable while still inviting the click that
     * switches layers, which a plain disabled look would not.
     */
    public void markOutOfActiveLayer(final boolean out) {
        outOfActiveLayer = out;
        setIcon(out ? dimmedThumbnail : thumbnail);
        setBorder(out ? createDashedBorder(GRAY, 1, 2, 2, false) : createLineBorder(GRAY, 1));
        setToolTipText(resolveTooltip());
        repaint();
    }

    /** @return the layer this tile belongs on, or {@code null} for a virtual tile that belongs to none */
    public LevelSceneLayerType getOwningLayer() {
        return tile.getType() == null ? null : tile.getType().getLevelSceneLayerOwningType();
    }

    public TileButton withTooltip(final String tooltip) {
        baseTooltip = tooltip;
        setToolTipText(resolveTooltip());
        return this;
    }

    private String resolveTooltip() {
        final LevelSceneLayerType owningLayer = getOwningLayer();
        if (!outOfActiveLayer || owningLayer == null) {
            return baseTooltip;
        }
        final String prefix = baseTooltip == null ? "" : baseTooltip + "<br>";
        return "<html>" + prefix + "Belongs on the <b>" + owningLayer.getLabel()
            + "</b> layer. Click to switch to it.</html>";
    }

    private static ImageIcon dim(final ImageIcon icon) {
        if (icon == null) {
            return null;
        }
        return new ImageIcon(GrayFilter.createDisabledImage(icon.getImage()));
    }
}
