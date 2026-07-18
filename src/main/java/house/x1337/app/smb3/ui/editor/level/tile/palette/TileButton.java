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
import static javax.swing.BorderFactory.createLineBorder;
import static java.awt.Color.GRAY;

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
            final JToggleButton selectedButton = selectedTileService.getSelectedTileButton();
            if (button.isSelected()) {
                if (selectedButton != null && selectedButton != button) {
                    selectedButton.setSelected(false);
                    selectedButton.setBorder(createLineBorder(GRAY, 1));
                }
                selectedTileService.setSelectedTileButton(button);
                button.setBorder(createLineBorder(GRAY.brighter(), 1));
            } else {
                button.setBorder(createLineBorder(GRAY, 1));
            }
        });
        return button;
    }

    public TileButton withTooltip(final String tooltip) {
        setToolTipText(tooltip);
        return this;
    }
}
