package house.x1337.app.smb3.ui.editor.level.tab;

import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.Rectangle;

public interface LevelSceneEditorFontCapabilities {
    default Point centredBaseline(
        final FontMetrics metrics,
        final String text,
        final Rectangle area
    ) {
        final int x = area.x + (area.width - metrics.stringWidth(text)) / 2;
        final int y = area.y + (area.height - (metrics.getAscent() + metrics.getDescent())) / 2 + metrics.getAscent();
        return new Point(x, y);
    }
}
