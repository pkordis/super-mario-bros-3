package house.x1337.app.smb3.ui.editor.level.tile.palette.core;

import house.x1337.app.smb3.annotation.Singleton;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * A {@link JPanel} that implements {@link Scrollable} and tracks the viewport
 * width. This prevents horizontal scrolling and ensures child panels using
 * {@link WrapLayout} reflow correctly when the viewport is resized in either
 * direction.
 */
@Singleton
public final class ScrollableFlowPanel extends JPanel implements Scrollable {
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(
        final Rectangle visibleRect, final int orientation, final int direction
    ) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(
        final Rectangle visibleRect, final int orientation, final int direction
    ) {
        return visibleRect.height;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
