package house.x1337.app.smb3.ui.editor.level.browse;

import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.ui.editor.level.browse.core.LevelScenesTableOwner;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static house.x1337.app.smb3.ui.editor.level.browse.core.LevelScenesTableOwner.Field.TITLE;
import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.NORTH;
import static java.awt.BorderLayout.SOUTH;
import static java.awt.FlowLayout.RIGHT;
import static java.util.Objects.requireNonNullElse;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.JSplitPane.HORIZONTAL_SPLIT;
import static javax.swing.JTable.AUTO_RESIZE_OFF;
import static javax.swing.ListSelectionModel.SINGLE_SELECTION;
import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE;

public sealed interface ComponentsBuilder extends LevelScenesTableOwner permits BrowseScenesWindow {
    String[] COLUMN_NAMES = {"ID", "Title", "Description", "Dimensions"};

    default JPanel buildContentPanel(
        final JTable table,
        final LevelScenePreviewPanel previewPanel,
        final ButtonsBar buttonsBar
    ) {
        final JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(createEmptyBorder(10, 10, 10, 10));
        root.add(buildSplitPane(table, previewPanel), CENTER);
        root.add(buttonsBar, SOUTH);
        return root;
    }

    default JSplitPane buildSplitPane(
        final JTable table,
        final LevelScenePreviewPanel previewPanel
    ) {
        final JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(createTitledBorder("Scenes"));

        final JPanel previewWrapper = new JPanel(new BorderLayout());
        previewWrapper.setBorder(createTitledBorder("Preview"));
        previewWrapper.add(previewPanel, CENTER);

        final JSplitPane split = new JSplitPane(
            HORIZONTAL_SPLIT,
            tableScroll,
            previewWrapper
        );
        split.setResizeWeight(0.65);
        split.setOneTouchExpandable(false);
        split.setDividerLocation(580);
        return split;
    }

    default Object[] buildRow(final LevelScene levelScene) {
        return new Object[]{
            levelScene.getId(),
            levelScene.getTitle() != null ? levelScene.getTitle() : "",
            levelScene.getDescription() != null ? levelScene.getDescription() : "",
            levelScene.getColumns() + " × " + levelScene.getRows()
        };
    }

    default JTable buildTable() {
        final DefaultTableModel tableModel = buildTableModel();
        final JTable table = new JTable(tableModel);
        table.setSelectionMode(SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoResizeMode(AUTO_RESIZE_OFF);
        table.setRowHeight(22);
        table.getColumnModel().getColumn(0).setPreferredWidth(440);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(220);
        table.getColumnModel().getColumn(3).setPreferredWidth(80);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onRowSelected(table.getSelectedRow());
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                if (e.getClickCount() != 2) {
                    return;
                }
                final int row = table.rowAtPoint(e.getPoint());
                final int column = table.columnAtPoint(e.getPoint());
                final Field field = Field.fromColumnIndex(column);
                if (row >= 0 && row < getLoadedLevelScenes().size() && field != null) {
                    launchValueEditModal(getLoadedLevelScenes().get(row), row, field);
                }
            }
        });
        return table;
    }

    private void launchValueEditModal(
        final LevelScene levelScene,
        final int row,
        final Field field
    ) {
        final BrowseScenesWindow thisWindow = (BrowseScenesWindow) this;
        final String label = field.getLabel();
        final String currentValue = switch (field) {
            case TITLE -> requireNonNullElse(levelScene.getTitle(), "");
            case DESCRIPTION -> requireNonNullElse(levelScene.getDescription(), "");
        };

        final JDialog dialog = new JDialog(thisWindow, "Edit " + label, true);
        final JTextArea textArea = new JTextArea(currentValue, field == TITLE ? 3 : 6, 36);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        final JButton okButton = new JButton("OK");
        final JButton cancelButton = new JButton("Cancel");
        okButton.setEnabled(!currentValue.isBlank());

        final Runnable syncOkState = () -> okButton.setEnabled(!textArea.getText().isBlank());
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(final DocumentEvent e) { syncOkState.run(); }
            @Override public void removeUpdate(final DocumentEvent e) { syncOkState.run(); }
            @Override public void changedUpdate(final DocumentEvent e) { syncOkState.run(); }
        });

        okButton.addActionListener(e -> {
            final String newValue = textArea.getText().trim();
            onFieldValueChangeRequest(levelScene, field, newValue);
            getTableModel().setValueAt(newValue, row, field.getColumnIndex());
            dialog.dispose();
        });
        cancelButton.addActionListener(e -> dialog.dispose());

        final JPanel buttons = new JPanel(new FlowLayout(RIGHT, 6, 0));
        buttons.add(cancelButton);
        buttons.add(okButton);

        final JPanel content = new JPanel(new BorderLayout(6, 6));
        content.setBorder(createEmptyBorder(12, 12, 8, 12));
        content.add(new JLabel(label + ":"), NORTH);
        content.add(new JScrollPane(textArea), CENTER);
        content.add(buttons, SOUTH);

        dialog.setContentPane(content);
        dialog.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(380, dialog.getHeight()));
        dialog.setLocationRelativeTo(thisWindow);
        dialog.setVisible(true);
    }

    default DefaultTableModel buildTableModel() {
        return new DefaultTableModel(COLUMN_NAMES, 0) {
            @Override
            public boolean isCellEditable(final int row, final int column) {
                return false;
            }
        };
    }
}
