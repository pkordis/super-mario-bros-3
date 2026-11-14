package house.x1337.app.smb3.ui.editor.tab.core;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static java.awt.Color.DARK_GRAY;
import static java.awt.Color.WHITE;
import static javax.swing.BorderFactory.createEmptyBorder;

/**
 * Draws each tab's title with a close button. Closing a tab removes it first and then calls the tab's
 * {@link BaseEditorTab#onClose()}, so whatever the tab cleans up no longer finds itself among the open tabs.
 */
public abstract class BaseEditorTabSystem extends JTabbedPane {
    private static final int CLOSE_BUTTON_PADDING = 4;

    protected void installCloseButton(final int index) {
        final String title = getTitleAt(index);

        final JPanel tabComponent = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tabComponent.setOpaque(false);

        final JLabel titleLabel = new JLabel(title);
        tabComponent.add(titleLabel);

        final JButton closeButton = buildCloseTabButton();
        tabComponent.add(closeButton);

        setTabComponentAt(index, tabComponent);
    }

    private JButton buildCloseTabButton() {
        final int btnSize = 14;
        final JButton button = new JButton() {
            @Override
            protected void paintComponent(final Graphics g) {
                super.paintComponent(g);
                final Graphics2D graphics2D = (Graphics2D) g.create();
                graphics2D.setStroke(new BasicStroke(1.5f));
                graphics2D.setColor(getModel().isRollover() ? WHITE : DARK_GRAY);

                graphics2D.drawLine(
                    CLOSE_BUTTON_PADDING,
                    CLOSE_BUTTON_PADDING,
                    getWidth() - CLOSE_BUTTON_PADDING - 1,
                    getHeight() - CLOSE_BUTTON_PADDING - 1
                );
                graphics2D.drawLine(
                    getWidth() - CLOSE_BUTTON_PADDING - 1,
                    CLOSE_BUTTON_PADDING,
                    CLOSE_BUTTON_PADDING,
                    getHeight() - CLOSE_BUTTON_PADDING - 1
                );
                graphics2D.dispose();
            }
        };
        button.setPreferredSize(new Dimension(btnSize, btnSize));
        button.setToolTipText("Close tab");
        button.setUI(new BasicButtonUI());
        button.setContentAreaFilled(false);
        button.setFocusable(false);
        button.setBorder(createEmptyBorder());
        button.setBorderPainted(false);
        button.setRolloverEnabled(true);
        setCloseButtonEventListeners(button);
        return button;
    }

    private void setCloseButtonEventListeners(final JButton button) {
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(final MouseEvent e) {
                if (e.getComponent() instanceof AbstractButton btn) {
                    btn.setContentAreaFilled(true);
                    btn.setBackground(new Color(220, 80, 80));
                }
            }

            @Override
            public void mouseExited(final MouseEvent e) {
                if (e.getComponent() instanceof AbstractButton btn) {
                    btn.setContentAreaFilled(false);
                }
            }
        });

        button.addActionListener(e -> {
            for (int i = 0; i < getTabCount(); i++) {
                if (getTabComponentAt(i) == button.getParent()) {
                    closeTabAt(i);
                    break;
                }
            }
        });
    }

    /**
     * Closes the tab at {@code index} as its close button does.
     */
    public void closeTabAt(final int index) {
        final Component content = getComponentAt(index);
        removeTabAt(index);
        if (content instanceof final BaseEditorTab tab) {
            tab.onClose();
        }
    }
}
