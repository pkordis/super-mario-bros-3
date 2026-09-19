package house.x1337.app.smb3.ui.editor.level.tile.palette;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.service.SelectedTileService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.swing.JToggleButton;
import java.awt.Dimension;
import java.awt.Insets;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.model.ui.tile.Tile.THUMB_SIZE;
import static java.awt.Color.GRAY;
import static javax.swing.BorderFactory.createLineBorder;

@Getter
@Prototype
@RequiredArgsConstructor
public class TileButton extends JToggleButton {
    private final Tile tile;

    public static TileButton fromTile(final Tile tile) {
        final TileButton button = getBean(TileButton.class, tile);
        final SelectedTileService selectedTileService = getBean(SelectedTileService.class);
        button.setIcon(tile.toThumbnail());
        final int size = THUMB_SIZE + 2;
        button.setPreferredSize(new Dimension(size, size));
        button.setMinimumSize(new Dimension(size, size));
        button.setMaximumSize(new Dimension(size, size));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setBorder(createLineBorder(GRAY, 1));
        button.addActionListener(e -> {
            if (button.isSelected()) {
                selectedTileService.select(button);
            } else {
                selectedTileService.clearSelection();
            }
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

    public TileButton withTooltip(final String tooltip) {
        setToolTipText(tooltip);
        return this;
    }
}
