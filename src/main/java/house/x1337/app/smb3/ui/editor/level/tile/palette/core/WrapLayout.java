package house.x1337.app.smb3.ui.editor.level.tile.palette.core;

import house.x1337.app.smb3.annotation.Singleton;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * A {@link FlowLayout} subclass that wraps components to the next row when
 * the container width is insufficient, and correctly reports preferred height
 * so that parent scroll panes show a vertical scrollbar.
 *
 * <p>Standard {@code FlowLayout} computes preferred size assuming a single
 * row, which breaks inside a {@link JScrollPane} with horizontal scrollbar
 * disabled. This layout computes the preferred height based on the actual
 * target width available.
 */
@Singleton
public final class WrapLayout extends FlowLayout {
    public WrapLayout() {
        super(LEFT, 2, 2);
    }

    @Override
    public Dimension preferredLayoutSize(final Container target) {
        return computeSize(target);
    }

    @Override
    public Dimension minimumLayoutSize(final Container target) {
        return computeSize(target);
    }

    private Dimension computeSize(final Container target) {
        synchronized (target.getTreeLock()) {
            final int targetWidth = getTargetWidth(target);
            if (targetWidth <= 0) {
                return super.preferredLayoutSize(target);
            }

            final Insets insets = target.getInsets();
            final int maxWidth = targetWidth - insets.left - insets.right;
            final int hgap = getHgap();
            final int vgap = getVgap();

            int rowWidth = 0;
            int rowHeight = 0;
            int totalHeight = insets.top + vgap;

            for (int i = 0; i < target.getComponentCount(); i++) {
                final Component comp = target.getComponent(i);
                if (!comp.isVisible()) {
                    continue;
                }
                final Dimension size = comp.getPreferredSize();
                if (rowWidth + size.width > maxWidth && rowWidth > 0) {
                    totalHeight += rowHeight + vgap;
                    rowWidth = 0;
                    rowHeight = 0;
                }
                rowWidth += size.width + hgap;
                rowHeight = Math.max(rowHeight, size.height);
            }
            totalHeight += rowHeight + insets.bottom + vgap;

            return new Dimension(targetWidth, totalHeight);
        }
    }

    private int getTargetWidth(final Container target) {
        final Container parent = target.getParent();
        if (parent == null) {
            return target.getWidth();
        }
        final JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(
            JScrollPane.class, target
        );
        if (scrollPane != null) {
            return scrollPane.getViewport().getWidth();
        }
        return parent.getWidth();
    }
}
