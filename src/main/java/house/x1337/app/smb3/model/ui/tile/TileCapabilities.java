package house.x1337.app.smb3.model.ui.tile;

import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.repository.TileRecord;

import javax.swing.ImageIcon;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.TileType.Category.VIRTUAL;
import static java.awt.RenderingHints.KEY_INTERPOLATION;
import static java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;

public sealed interface TileCapabilities permits Tile {
    int THUMB_SIZE = 48;

    default TileRecord toRecord() {
        final Tile tile = (Tile) this;
        return TileRecord.builder()
            .id(tile.getId())
            .sha256(tile.getSha256())
            .type(tile.getType())
            .description(tile.getDescription())
            .originalArgbData(tile.getOriginalArgbData())
            .argbData(tile.getArgbData())
            .build();
    }

    default ImageIcon toThumbnail() {
        final BufferedImage image = toImage();
        if (image == null) {
            return null;
        }
        final BufferedImage scaled = new BufferedImage(THUMB_SIZE, THUMB_SIZE, TYPE_INT_ARGB);
        final Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(
            KEY_INTERPOLATION,
            VALUE_INTERPOLATION_NEAREST_NEIGHBOR
        );
        graphics.drawImage(image, 0, 0, THUMB_SIZE, THUMB_SIZE, null);
        graphics.dispose();
        return new ImageIcon(scaled);
    }

    default BufferedImage toImage() {
        final int[] pixels = getDisplayArgbData();
        if (pixels == null) {
            return null;
        }
        final BufferedImage image = new BufferedImage(TILE_SPRITE_SIZE, TILE_SPRITE_SIZE, TYPE_INT_ARGB);
        image.setRGB(0, 0, TILE_SPRITE_SIZE, TILE_SPRITE_SIZE, pixels, 0, TILE_SPRITE_SIZE);
        return image;
    }

    /**
     * Pixels to display: the classified {@code argbData} when present, otherwise the
     * imported {@code originalArgbData}.
     */
    default int[] getDisplayArgbData() {
        final Tile tile = (Tile) this;
        return tile.getArgbData() != null ? tile.getArgbData() : tile.getOriginalArgbData();
    }

    default boolean isOfType(final TileType type) {
        final Tile tile = (Tile) this;
        return tile.getType() == type;
    }

    default boolean isVirtual() {
        final Tile tile = (Tile) this;
        return tile.getType() != null && tile.getType().getCategory() == VIRTUAL;
    }

    default boolean isRenderable() {
        final Tile tile = (Tile) this;
        return tile.getType() != null && !isVirtual() && tile.getArgbData() != null;
    }
}
