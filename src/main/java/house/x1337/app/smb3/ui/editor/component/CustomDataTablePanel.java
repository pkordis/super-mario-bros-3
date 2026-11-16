package house.x1337.app.smb3.ui.editor.component;

import house.x1337.app.smb3.annotation.Prototype;
import lombok.AccessLevel;
import lombok.Getter;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.SOUTH;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.JOptionPane.WARNING_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;

@Prototype
public final class CustomDataTablePanel extends JPanel {
    private static final int KEY_COLUMN = 0;
    private static final int VALUE_COLUMN = 1;

    private final DefaultTableModel model = new DefaultTableModel(new String[] {"Key", "Value"}, 0) {
        @Override
        public boolean isCellEditable(final int row, final int column) {
            return editable;
        }
    };

    @Getter(AccessLevel.PACKAGE)
    private final JTable table = new JTable(model);
    @Getter(AccessLevel.PACKAGE)
    private final JButton addRowButton = new JButton("Add");
    @Getter(AccessLevel.PACKAGE)
    private final JButton removeRowButton = new JButton("Remove");
    private boolean editable = true;

    public CustomDataTablePanel() {
        super(new BorderLayout(8, 8));
    }

    public void render(final String title) {
        render(title, Map.of());
    }

    public void render(final String title, final Map<String, ?> initial) {
        setBorder(createTitledBorder(title));

        show(initial);

        table.setRowHeight(24);
        table.getColumnModel().getColumn(KEY_COLUMN).setPreferredWidth(120);
        table.getColumnModel().getColumn(VALUE_COLUMN).setPreferredWidth(180);

        final JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(300, 200));

        add(tableScroll, CENTER);
        add(buildButtons(), SOUTH);
    }

    /** Replaces the rows with {@code data}, dropping any edit in progress. */
    public void show(final Map<String, ?> data) {
        final TableCellEditor editor = table.getCellEditor();
        if (editor != null) {
            editor.cancelCellEditing();
        }
        model.setRowCount(0);
        data.forEach((key, value) -> model.addRow(new String[]{key, value == null ? "" : String.valueOf(value)}));
    }

    public void setEditable(final boolean editable) {
        this.editable = editable;
        addRowButton.setEnabled(editable);
        removeRowButton.setEnabled(editable);
    }

    public boolean isEditable() {
        return editable;
    }

    public void onChange(final Runnable action) {
        model.addTableModelListener(event -> action.run());
    }

    public Map<String, Object> getData() {
        commitPendingEdit();
        final Map<String, Object> data = new LinkedHashMap<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            final String key = trimmedAt(row, KEY_COLUMN);
            if (!key.isEmpty()) {
                data.put(key, trimmedAt(row, VALUE_COLUMN));
            }
        }
        return data;
    }

    public List<String> duplicateKeys() {
        commitPendingEdit();
        final Set<String> seen = new HashSet<>();
        final Set<String> duplicates = new LinkedHashSet<>();
        for (int row = 0; row < model.getRowCount(); row++) {
            final String key = trimmedAt(row, KEY_COLUMN);
            if (!key.isEmpty() && !seen.add(key)) {
                duplicates.add(key);
            }
        }
        return new ArrayList<>(duplicates);
    }

    public boolean warnAboutDuplicateKeys(final Component parent) {
        final List<String> duplicates = duplicateKeys();
        if (duplicates.isEmpty()) {
            return false;
        }
        showMessageDialog(
            parent,
            """
            Each custom data key may appear only once.
            Duplicated: %s
            Remove or rename the extra rows.""".formatted(String.join(", ", duplicates)),
            "Duplicate Custom Data Keys",
            WARNING_MESSAGE
        );
        return true;
    }

    private JPanel buildButtons() {
        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addRowButton.addActionListener(event -> addRow());
        removeRowButton.addActionListener(event -> removeSelectedRow());
        buttons.add(addRowButton);
        buttons.add(removeRowButton);
        return buttons;
    }

    private void addRow() {
        model.addRow(new String[]{"", ""});
    }

    private void removeSelectedRow() {
        commitPendingEdit();
        final int selectedRow = table.getSelectedRow();
        if (selectedRow >= 0) {
            model.removeRow(selectedRow);
        }
    }

    private void commitPendingEdit() {
        final TableCellEditor editor = table.getCellEditor();
        if (editor != null && !editor.stopCellEditing()) {
            editor.cancelCellEditing();
        }
    }

    private String trimmedAt(final int row, final int column) {
        final Object value = model.getValueAt(row, column);
        return value == null ? "" : value.toString().trim();
    }
}
