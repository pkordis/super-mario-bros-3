package house.x1337.app.smb3.ui.editor.level.tile.palette;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.palette.LayerGatedToggleButton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.awt.Dimension;
import java.awt.Insets;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.model.ui.tile.Tile.THUMB_SIZE;

@Getter
@Prototype
@RequiredArgsConstructor
public class TileButton extends LayerGatedToggleButton {
    private final Tile tile;

    public static TileButton fromTile(final Tile tile) {
        final TileButton button = getBean(TileButton.class, tile);
        button.setThumbnail(tile.toThumbnail());
        final int size = THUMB_SIZE + 2;
        button.setPreferredSize(new Dimension(size, size));
        button.setMinimumSize(new Dimension(size, size));
        button.setMaximumSize(new Dimension(size, size));
        button.setMargin(new Insets(0, 0, 0, 0));
        return button;
    }

    /** @return the layer this tile belongs on, or {@code null} for a virtual tile that belongs to none */
    @Override
    public LevelSceneLayerType getOwningLayer() {
        return tile.getType() == null ? null : tile.getType().getLevelSceneLayerOwningType();
    }

    public TileButton withTooltip(final String tooltip) {
        setBaseTooltip(tooltip);
        return this;
    }
}
