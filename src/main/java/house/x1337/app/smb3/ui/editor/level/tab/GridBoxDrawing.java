package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.model.game.MultiTileArea;
import house.x1337.app.smb3.model.game.PixelArea;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;

import static house.x1337.app.smb3.GameConstants.TILE_SCALE;

/** Boxes drawn over the editor grid: the borders of placed objects, previews and refusals. */
public interface GridBoxDrawing {
    default Rectangle pixelBox(final MultiTileArea cells) {
        return pixelBox(cells.toPixelArea());
    }

    /** The editor pixels showing {@code area}, which is in level pixels. */
    default Rectangle pixelBox(final PixelArea area) {
        return new Rectangle(
            area.x() * TILE_SCALE,
            area.y() * TILE_SCALE,
            area.width() * TILE_SCALE,
            area.height() * TILE_SCALE
        );
    }

    /** A 4 px border drawn just inside {@code box}. */
    default void drawBorder(
        final Graphics2D graphics,
        final Paint paint,
        final Rectangle box
    ) {
        graphics.setPaint(paint);
        graphics.setStroke(new BasicStroke(4f));
        graphics.drawRect(box.x + 2, box.y + 2, box.width - 4, box.height - 4);
    }
}
