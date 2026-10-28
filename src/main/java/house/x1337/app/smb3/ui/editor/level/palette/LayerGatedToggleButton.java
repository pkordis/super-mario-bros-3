package house.x1337.app.smb3.ui.editor.level.palette;

import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.ui.editor.level.menu.scene.level.ActiveLayerMenu;
import house.x1337.app.smb3.ui.service.PaletteSelectionService;
import lombok.AccessLevel;
import lombok.Getter;

import javax.swing.GrayFilter;
import javax.swing.ImageIcon;
import javax.swing.JToggleButton;
import javax.swing.border.Border;
import java.awt.Color;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static java.awt.Color.GRAY;
import static javax.swing.BorderFactory.createDashedBorder;
import static javax.swing.BorderFactory.createLineBorder;

@Getter
public abstract class LayerGatedToggleButton extends JToggleButton {
    private static final Color BORDER_COLOR = GRAY;
    private static final Color SELECTED_BORDER_COLOR = GRAY.brighter();

    @Getter(AccessLevel.NONE)
    private final PaletteSelectionService paletteSelectionService = getBean(PaletteSelectionService.class);

    private ImageIcon thumbnail;
    private ImageIcon dimmedThumbnail;
    private String baseTooltip;
    private boolean outOfActiveLayer;

    protected LayerGatedToggleButton() {
        setBorder(resolveBorder());
        addActionListener(event -> onClicked());
    }

    public abstract LevelSceneLayerType getOwningLayer();

    /**
     * Reflects this button's selection state, both in the toggle and in its border. Called by
     * {@link PaletteSelectionService} so that selecting anything else — a tile, or an enemy — visibly
     * releases this one.
     */
    public void markSelected(final boolean selected) {
        setSelected(selected);
        setBorder(resolveBorder());
    }

    /**
     * Greys this entry out, or restores it, according to whether its layer is the active one.
     *
     * <p>A dashed border and a faded thumbnail read as unavailable while still inviting the click that
     * switches layers, which a plain disabled look would not.
     */
    public void markOutOfActiveLayer(final boolean out) {
        outOfActiveLayer = out;
        setIcon(currentIcon());
        setBorder(resolveBorder());
        setToolTipText(resolveTooltip());
        repaint();
    }

    /** Replaces the picture this entry shows, keeping its greyed twin in step. */
    public void setThumbnail(final ImageIcon icon) {
        thumbnail = icon;
        dimmedThumbnail = dim(icon);
        setIcon(currentIcon());
        repaint();
    }

    protected final void setBaseTooltip(final String tooltip) {
        baseTooltip = tooltip;
        setToolTipText(resolveTooltip());
    }

    private void onClicked() {
        if (!isSelected()) {
            paletteSelectionService.clearSelection();
            return;
        }
        // Activation first: the palette re-sync it triggers would release this entry if it were armed already.
        if (outOfActiveLayer && !activateOwningLayer()) {
            markSelected(false);
            return;
        }
        paletteSelectionService.select(this);
    }

    private boolean activateOwningLayer() {
        final LevelSceneLayerType owningLayer = getOwningLayer();
        return owningLayer != null && getBean(ActiveLayerMenu.class).activate(owningLayer);
    }

    /** The one border resolver, so that neither state overwrites the other's look. */
    private Border resolveBorder() {
        final Color color = isSelected() ? SELECTED_BORDER_COLOR : BORDER_COLOR;
        return outOfActiveLayer ? createDashedBorder(color, 1, 2, 2, false) : createLineBorder(color, 1);
    }

    private ImageIcon currentIcon() {
        return outOfActiveLayer && dimmedThumbnail != null ? dimmedThumbnail : thumbnail;
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
