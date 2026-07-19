package house.x1337.app.smb3.ui.editor.level.tab.core;

import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;

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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.awt.Color.DARK_GRAY;
import static java.awt.Color.WHITE;
import static javax.swing.BorderFactory.createEmptyBorder;

public abstract class BaseLevelSceneTabSystem extends JTabbedPane {
    private static final int CLOSE_BUTTON_PADDING = 4;
    private final Map<String, LevelSceneEditorTab> openLevelSceneTabs = new HashMap<>();

    protected JButton installCloseButton(final int index) {
        final String title = getTitleAt(index);

        final JPanel tabComponent = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tabComponent.setOpaque(false);

        final JLabel titleLabel = new JLabel(title);
        tabComponent.add(titleLabel);

        final JButton closeButton = buildCloseTabButton();
        tabComponent.add(closeButton);

        setTabComponentAt(index, tabComponent);
        return closeButton;
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
            final int tabCount = getTabCount();
            for (int i = 0; i < tabCount; i++) {
                final Component tabComp = getTabComponentAt(i);
                if (tabComp != null && tabComp == button.getParent()) {
                    // Untrack the closed scene panel
                    final Component content = getComponentAt(i);
                    if (content instanceof LevelSceneEditorTab closedPanel) {
                        final String closedId = closedPanel.getSceneId();
                        openLevelSceneTabs.remove(closedId);
                    }
                    removeTabAt(i);
                    break;
                }
            }
        });
    }

    public void track(final LevelSceneEditorTab tab) {
        final String id = tab.getSceneId();
        openLevelSceneTabs.put(id, tab);
        tab.updateTabTitle();
    }

    public void untrack(final LevelSceneEditorTab tab) {
        final String id = tab.getSceneId();
        openLevelSceneTabs.remove(id);
        tab.clearScene();
        tab.updateTabTitle();
    }

    public Optional<LevelSceneEditorTab> getTrackedTab(final LevelScene levelScene) {
        final String id = levelScene.getId();
        if (openLevelSceneTabs.containsKey(id)) {
            final LevelSceneEditorTab alreadyOpen = openLevelSceneTabs.get(id);
            return Optional.of(alreadyOpen);
        }
        return Optional.empty();
    }
}
