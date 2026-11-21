package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.model.game.MultiTileArea;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE;

/** Boxes drawn over the editor grid: the borders of placed objects, previews and refusals. */
public interface GridBoxDrawing {
    default Rectangle pixelBox(final MultiTileArea cells) {
        return new Rectangle(
            cells.column() * TILE_SIZE,
            cells.row() * TILE_SIZE,
            cells.columns() * TILE_SIZE,
            cells.rows() * TILE_SIZE
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
