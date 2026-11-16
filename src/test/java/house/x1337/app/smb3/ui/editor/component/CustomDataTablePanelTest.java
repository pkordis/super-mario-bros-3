package house.x1337.app.smb3.ui.editor.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class CustomDataTablePanelTest {
    private CustomDataTablePanel panel;

    @BeforeEach
    void prepare() {
        panel = new CustomDataTablePanel();
    }

    @Test
    @DisplayName("Keys and values are trimmed and kept in row order")
    void trimsKeysAndValuesInRowOrder() {
        // Prepare
        panel.render("Custom Data");
        givenRow("  speed ", " fast  ");
        givenRow("colour", "red");

        // Execute
        final Map<String, Object> data = panel.getData();

        // Verify
        assertThat(data).containsExactly(entry("speed", "fast"), entry("colour", "red"));
    }

    @Test
    @DisplayName("Rows whose key is blank are skipped")
    void skipsRowsWithABlankKey() {
        // Prepare
        panel.render("Custom Data");
        givenRow("", "orphan");
        givenRow("   ", "spaces only");
        givenRow(null, "no key at all");
        givenRow("kept", null);

        // Execute
        final Map<String, Object> data = panel.getData();

        // Verify - a missing value is read as an empty string, not null
        assertThat(data).containsExactly(entry("kept", ""));
    }

    @Test
    @DisplayName("A value still being edited is committed before the data is read")
    void commitsTheCellBeingEdited() {
        // Prepare - the author types a value and clicks Save without pressing Enter or Tab
        panel.render("Custom Data");
        givenRow("speed", "");
        panel.getTable().editCellAt(0, 1);
        ((JTextField) panel.getTable().getEditorComponent()).setText("fast");

        // Execute
        final Map<String, Object> data = panel.getData();

        // Verify
        assertThat(data).containsExactly(entry("speed", "fast"));
        assertThat(panel.getTable().isEditing()).as("The edit is finished, not left open").isFalse();
    }

    @Test
    @DisplayName("The initial map is shown as one row per entry, in its order")
    void prefillsTheInitialMap() {
        // Prepare
        final Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("speed", "fast");
        initial.put("count", 3);

        // Execute
        panel.render("Custom Data", initial);

        // Verify - values are shown in their string form, as the table only holds strings
        assertThat(model().getRowCount()).isEqualTo(2);
        assertThat(panel.getData()).containsExactly(entry("speed", "fast"), entry("count", "3"));
    }

    @Test
    @DisplayName("A key entered on more than one row is reported once, compared after trimming")
    void reportsDuplicateKeys() {
        // Prepare
        panel.render("Custom Data");
        givenRow("speed", "fast");
        givenRow("colour", "red");
        givenRow(" speed", "slow");
        givenRow("speed ", "medium");
        givenRow("", "a");
        givenRow("", "b");

        // Execute & Verify - blank keys are skipped, so they are never duplicates
        assertThat(panel.duplicateKeys()).containsExactly("speed");
        assertThat(panel.getData())
            .as("The last value of a duplicate wins if the host ignored the warning")
            .containsEntry("speed", "medium");
    }

    @Test
    @DisplayName("Distinct keys report no duplicates")
    void distinctKeysReportNoDuplicates() {
        // Prepare
        panel.render("Custom Data");
        givenRow("speed", "fast");
        givenRow("colour", "red");

        // Execute & Verify
        assertThat(panel.duplicateKeys()).isEmpty();
    }

    @Test
    @DisplayName("Showing new data replaces every row and drops an edit in progress")
    void showReplacesTheRows() {
        // Prepare
        panel.render("Custom Data", Map.of("speed", "fast"));
        panel.getTable().editCellAt(0, 1);

        // Execute
        panel.show(Map.of("colour", "red"));

        // Verify
        assertThat(panel.getData()).containsExactly(entry("colour", "red"));
        assertThat(panel.getTable().isEditing()).isFalse();
    }

    @Test
    @DisplayName("A read-only table has no editable cells and no Add or Remove")
    void aReadOnlyTableCannotBeChanged() {
        // Prepare
        panel.render("Custom Data", Map.of("speed", "fast"));

        // Execute
        panel.setEditable(false);

        // Verify
        assertThat(panel.isEditable()).isFalse();
        assertThat(panel.getTable().isCellEditable(0, 1)).isFalse();
        assertThat(panel.getAddRowButton().isEnabled()).isFalse();
        assertThat(panel.getRemoveRowButton().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("A change to the rows is reported")
    void changesAreReported() {
        // Prepare
        final AtomicInteger changes = new AtomicInteger();
        panel.render("Custom Data", Map.of("speed", "fast"));
        panel.onChange(changes::incrementAndGet);

        // Execute
        model().setValueAt("slow", 0, 1);

        // Verify
        assertThat(changes).hasValue(1);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private void givenRow(final String key, final String value) {
        model().addRow(new Object[]{key, value});
    }

    private DefaultTableModel model() {
        return (DefaultTableModel) panel.getTable().getModel();
    }
}
