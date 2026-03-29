package house.x1337.app.smb3.ui.editor.level.tile.palette;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.service.SelectedTileService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.swing.JToggleButton;
import java.awt.Dimension;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.model.ui.tile.Tile.THUMB_SIZE;
import static javax.swing.BorderFactory.createBevelBorder;
import static javax.swing.border.BevelBorder.LOWERED;
import static javax.swing.border.BevelBorder.RAISED;

@Getter
@Prototype
@RequiredArgsConstructor
public class TileButton extends JToggleButton {
    private final Tile tile;

    public static TileButton fromTile(final Tile tile) {
        final TileButton button = getBean(TileButton.class, tile);
        final SelectedTileService selectedTileService = getBean(SelectedTileService.class);
        button.setIcon(tile.toThumbnail());
        button.setPreferredSize(new Dimension(THUMB_SIZE + 12, THUMB_SIZE + 12));
        button.setBorder(createBevelBorder(RAISED));
        button.addActionListener(e -> {
            final JToggleButton selectedButton = selectedTileService.getSelectedTileButton();
            if (button.isSelected()) {
                if (selectedButton != null && selectedButton != button) {
                    selectedButton.setSelected(false);
                    selectedButton.setBorder(createBevelBorder(RAISED));
                }
                selectedTileService.setSelectedTileButton(button);
                button.setBorder(createBevelBorder(LOWERED));
            } else {
                button.setBorder(createBevelBorder(RAISED));
            }
        });
        return button;
    }

    public TileButton withTooltip(final String tooltip) {
        setToolTipText(tooltip);
        return this;
    }
}
