package house.x1337.app.smb3.ui.editor.level.virtualobject.linkage;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PipePortalType;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.service.LevelSceneService;
import house.x1337.app.smb3.service.PipePortalService;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTabSystem;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.PipePortalType.IN;
import static house.x1337.app.smb3.enumeration.PipePortalType.IN_AND_OUT;
import static house.x1337.app.smb3.enumeration.PipePortalType.OUT;
import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.NORTH;
import static java.awt.BorderLayout.SOUTH;
import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.WEST;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createEtchedBorder;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.JOptionPane.WARNING_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;

@Prototype
public final class PipePortalLinkageWindow extends JDialog {
    private static final Dimension LIST_SIZE = new Dimension(300, 220);
    private static final Dimension BUTTON_SIZE = new Dimension(90, 28);

    private final PipePortalLinkageModel model;
    private final Map<PipePortalType, DefaultListModel<PipePortal>> freeLists = new EnumMap<>(PipePortalType.class);
    private final DefaultListModel<PipePortalLinkagePair> linkedPairs = new DefaultListModel<>();
    private final JList<PipePortalLinkagePair> linkedPairsList = new JList<>(linkedPairs);
    private final JTextField filterField = new JTextField(28);
    private final JTextField entranceField = readOnlyField();
    private final JTextField exitField = readOnlyField();
    private final JButton linkButton = new JButton("Link");
    private final JButton unlinkButton = new JButton("Unlink");

    public PipePortalLinkageWindow(final Frame owner, final PipePortalLinkageModel model) {
        super(owner, "Pipe Portal Linkage", true);
        this.model = model;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildContent());
        model.reload();
        refresh();
        pack();
        setLocationRelativeTo(owner);
    }

    public static PipePortalLinkageWindow launch(final Frame owner) {
        final PipePortalLinkageModel model = getBean(
            PipePortalLinkageModel.class,
            getBean(PipePortalService.class),
            getBean(LevelSceneService.class),
            getBean(LevelSceneEditorTabSystem.class)
        );
        return getBean(PipePortalLinkageWindow.class, owner, model);
    }

    private JPanel buildContent() {
        final JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(createEmptyBorder(12, 12, 12, 12));
        root.add(buildFilterPanel(), NORTH);
        root.add(buildFreeListsPanel(), CENTER);
        root.add(buildBottomPanel(), SOUTH);
        return root;
    }

    private JPanel buildFilterPanel() {
        final JPanel panel = new JPanel(new BorderLayout(6, 4));
        panel.add(new JLabel("Filter:"), BorderLayout.WEST);
        panel.add(filterField, CENTER);
        filterField.setToolTipText("Matches a scene title or a portal description");
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(final DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(final DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(final DocumentEvent e) {
                applyFilter();
            }
        });
        final JLabel hint = new JLabel("Only saved scenes are listed. Save a scene to link its new portals.");
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 11f));
        panel.add(hint, SOUTH);
        return panel;
    }

    private JPanel buildFreeListsPanel() {
        final JPanel panel = new JPanel(new GridLayout(1, 3, 8, 0));
        for (final PipePortalType type : List.of(IN, OUT, IN_AND_OUT)) {
            final DefaultListModel<PipePortal> listModel = new DefaultListModel<>();
            freeLists.put(type, listModel);
            final JList<PipePortal> list = new JList<>(listModel);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setCellRenderer(new PortalRenderer());
            list.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(final MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        chooseAt(list, e);
                    }
                }
            });
            final JScrollPane scroll = new JScrollPane(list);
            scroll.setPreferredSize(LIST_SIZE);
            scroll.setBorder(titled(type.getLabel()));
            panel.add(scroll);
        }
        return panel;
    }

    private JPanel buildBottomPanel() {
        final JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(buildPairPanel(), NORTH);
        panel.add(buildLinkedPairsPanel(), CENTER);
        panel.add(buildButtonPanel(), SOUTH);
        return panel;
    }

    private JPanel buildPairPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(titled("Pair (double-click a portal above)"));

        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 6);
        gbc.anchor = WEST;

        addSlotRow(panel, gbc, 0, "Entrance:", entranceField, () -> {
            model.clearEntrance();
            refresh();
        });
        addSlotRow(panel, gbc, 1, "Exit:", exitField, () -> {
            model.clearExit();
            refresh();
        });

        linkButton.setPreferredSize(BUTTON_SIZE);
        linkButton.addActionListener(e -> link());
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(linkButton, gbc);
        return panel;
    }

    private static void addSlotRow(
        final JPanel panel,
        final GridBagConstraints gbc,
        final int row,
        final String label,
        final JTextField field,
        final Runnable clear
    ) {
        gbc.gridy = row;
        gbc.gridheight = 1;

        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.fill = HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(field, gbc);

        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        final JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> clear.run());
        panel.add(clearButton, gbc);
    }

    private JPanel buildLinkedPairsPanel() {
        final JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(titled("Linked pairs"));
        linkedPairsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        linkedPairsList.setCellRenderer(new PairRenderer());
        linkedPairsList.addListSelectionListener(
            e -> unlinkButton.setEnabled(linkedPairsList.getSelectedValue() != null)
        );
        final JScrollPane scroll = new JScrollPane(linkedPairsList);
        scroll.setPreferredSize(new Dimension(LIST_SIZE.width, 120));
        panel.add(scroll, CENTER);

        unlinkButton.setPreferredSize(BUTTON_SIZE);
        unlinkButton.addActionListener(e -> unlink());
        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(unlinkButton);
        panel.add(buttons, SOUTH);
        return panel;
    }

    private JPanel buildButtonPanel() {
        final JButton closeButton = new JButton("Close");
        closeButton.setPreferredSize(BUTTON_SIZE);
        closeButton.addActionListener(e -> dispose());
        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        panel.add(closeButton);
        return panel;
    }

    private void applyFilter() {
        model.setFilter(filterField.getText());
        refresh();
    }

    private void chooseAt(final JList<PipePortal> list, final MouseEvent e) {
        final int index = list.locationToIndex(e.getPoint());
        if (index < 0 || !list.getCellBounds(index, index).contains(e.getPoint())) {
            return;
        }
        model.choose(list.getModel().getElementAt(index));
        refresh();
    }

    private void link() {
        runRefusable(model::link);
    }

    private void unlink() {
        final PipePortalLinkagePair pair = linkedPairsList.getSelectedValue();
        runRefusable(() -> model.unlink(pair));
    }

    private void runRefusable(final Runnable action) {
        try {
            action.run();
        } catch (final IllegalStateException e) {
            showMessageDialog(this, e.getMessage(), "Pipe Portal Linkage", WARNING_MESSAGE);
            model.reload();
        }
        refresh();
    }

    private void refresh() {
        for (final Map.Entry<PipePortalType, DefaultListModel<PipePortal>> entry : freeLists.entrySet()) {
            final DefaultListModel<PipePortal> listModel = entry.getValue();
            listModel.clear();
            listModel.addAll(model.getFreePortals(entry.getKey()));
        }
        linkedPairs.clear();
        linkedPairs.addAll(model.getLinkedPairs());
        entranceField.setText(model.describe(model.getEntrance()));
        exitField.setText(model.describe(model.getExit()));
        entranceField.setToolTipText(model.getEntrance() == null ? null : model.getEntrance().getId());
        exitField.setToolTipText(model.getExit() == null ? null : model.getExit().getId());
        linkButton.setEnabled(model.isLinkEnabled());
        unlinkButton.setEnabled(linkedPairsList.getSelectedValue() != null);
    }

    private static JTextField readOnlyField() {
        final JTextField field = new JTextField(40);
        field.setEditable(false);
        return field;
    }

    private static TitledBorder titled(final String title) {
        return createTitledBorder(createEtchedBorder(), title, TitledBorder.LEADING, TitledBorder.TOP);
    }

    private final class PortalRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
            final JList<?> list,
            final Object value,
            final int index,
            final boolean isSelected,
            final boolean cellHasFocus
        ) {
            final PipePortal portal = (PipePortal) value;
            final Component component = super.getListCellRendererComponent(
                list,
                model.describe(portal),
                index,
                isSelected,
                cellHasFocus
            );
            setToolTipText("<html><tt>%s</tt></html>".formatted(portal.getId()));
            return component;
        }
    }

    private final class PairRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
            final JList<?> list,
            final Object value,
            final int index,
            final boolean isSelected,
            final boolean cellHasFocus
        ) {
            final PipePortalLinkagePair pair = (PipePortalLinkagePair) value;
            final String exitText = pair.isDangling() ? "(missing)" : model.describe(pair.exit());
            return super.getListCellRendererComponent(
                list,
                model.describe(pair.entrance()) + "  ⇄  " + exitText,
                index,
                isSelected,
                cellHasFocus
            );
        }
    }
}
