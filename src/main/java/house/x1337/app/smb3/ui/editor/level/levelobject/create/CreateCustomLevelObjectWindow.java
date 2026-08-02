package house.x1337.app.smb3.ui.editor.level.levelobject.create;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.repository.LevelObjectRecord;
import house.x1337.app.smb3.model.repository.TileRecord;
import house.x1337.app.smb3.service.LevelObjectService;
import house.x1337.app.smb3.service.TileService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.Map;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.SOUTH;
import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.WEST;
import static java.util.Arrays.stream;
import static java.util.Comparator.comparing;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createTitledBorder;

/**
 * Editor window for creating a custom single-tiled level object.
 * Contains three sections:
 * <ul>
 *   <li>Object Properties: LevelObjectTypeSingleTiled and description</li>
 *   <li>Tile Editor: visual editor to modify transparency etc.</li>
 *   <li>Custom Data: key-value pairs for additional metadata</li>
 * </ul>
 */
@Slf4j
@Prototype
public final class CreateCustomLevelObjectWindow extends JDialog {
    private static final String NO_TYPE_LABEL = "- Select Type -";

    private final TileService tileService = getBean(TileService.class);
    private final LevelObjectService levelObjectService = getBean(LevelObjectService.class);
    private final JFrame parentFrame;
    private final int[] originalArgbData;

    // Object properties
    private final JComboBox<String> objectTypeCombo = buildObjectTypeCombo();
    private final JTextField descriptionField = new JTextField(20);

    // Tile editor
    private final CustomLevelObjectTileEditorPanel tileEditorPanel;

    // Custom data table
    private final DefaultTableModel customDataModel = new DefaultTableModel(
        new String[]{"Key", "Value"}, 0
    );
    private final JTable customDataTable = new JTable(customDataModel);

    // Actions
    private final JButton createButton = new JButton("Create");
    private final JButton cancelButton = new JButton("Cancel");

    public CreateCustomLevelObjectWindow(
        final JFrame parent,
        final int[] originalArgbData
    ) {
        super(parent);
        this.parentFrame = parent;
        this.originalArgbData = originalArgbData;
        this.tileEditorPanel = getBean(CustomLevelObjectTileEditorPanel.class, originalArgbData);
    }

    @PostConstruct
    void init() {
        setTitle("Create Custom Level Object – Single Tiled");
        setModal(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);

        final JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(createEmptyBorder(10, 10, 10, 10));

        // Main content with three sections
        final JSplitPane mainSplit = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            buildLeftPanel(),
            buildRightPanel()
        );
        mainSplit.setDividerLocation(380);
        mainSplit.setResizeWeight(0.5);

        content.add(mainSplit, CENTER);
        content.add(buildFooter(), SOUTH);

        setContentPane(content);
        setMinimumSize(new Dimension(750, 520));
        pack();
        setLocationRelativeTo(getParent());

        updateCreateButtonState();
    }

    private JPanel buildLeftPanel() {
        final JPanel panel = new JPanel(new BorderLayout(8, 8));

        // Object properties section
        final JPanel propertiesPanel = buildPropertiesPanel();

        // Tile editor section
        final JPanel editorContainer = new JPanel(new BorderLayout());
        editorContainer.setBorder(createTitledBorder("Tile Editor (click to set transparency)"));
        editorContainer.add(tileEditorPanel, CENTER);

        panel.add(propertiesPanel, BorderLayout.NORTH);
        panel.add(editorContainer, CENTER);

        return panel;
    }

    private JPanel buildPropertiesPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(createTitledBorder("Object Properties"));

        final GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.anchor = WEST;
        labelGbc.insets = new Insets(5, 8, 5, 6);
        labelGbc.gridx = 0;
        labelGbc.gridy = 0;

        final GridBagConstraints fieldGbc = new GridBagConstraints();
        fieldGbc.anchor = WEST;
        fieldGbc.fill = HORIZONTAL;
        fieldGbc.weightx = 1.0;
        fieldGbc.insets = new Insets(5, 0, 5, 8);
        fieldGbc.gridx = 1;
        fieldGbc.gridy = 0;

        panel.add(new JLabel("Object Type:"), labelGbc);
        objectTypeCombo.addItemListener(e -> updateCreateButtonState());
        panel.add(objectTypeCombo, fieldGbc);

        labelGbc.gridy++;
        fieldGbc.gridy++;
        panel.add(new JLabel("Description:"), labelGbc);
        panel.add(descriptionField, fieldGbc);

        return panel;
    }

    private JPanel buildRightPanel() {
        final JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(createTitledBorder("Custom Data"));

        // Table for key-value pairs
        customDataTable.setRowHeight(24);
        customDataTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        customDataTable.getColumnModel().getColumn(1).setPreferredWidth(180);

        final JScrollPane tableScroll = new JScrollPane(customDataTable);
        tableScroll.setPreferredSize(new Dimension(300, 200));

        // Buttons for adding/removing rows
        final JPanel tableButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        final JButton addRowButton = new JButton("Add");
        addRowButton.addActionListener(e -> customDataModel.addRow(new String[]{"", ""}));
        final JButton removeRowButton = new JButton("Remove");
        removeRowButton.addActionListener(e -> {
            final int selectedRow = customDataTable.getSelectedRow();
            if (selectedRow >= 0) {
                customDataModel.removeRow(selectedRow);
            }
        });
        tableButtons.add(addRowButton);
        tableButtons.add(removeRowButton);

        panel.add(tableScroll, CENTER);
        panel.add(tableButtons, SOUTH);

        return panel;
    }

    private JPanel buildFooter() {
        final JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        createButton.setEnabled(false);
        createButton.addActionListener(e -> handleCreate());

        cancelButton.addActionListener(e -> dispose());

        footer.add(createButton);
        footer.add(cancelButton);

        return footer;
    }

    private void handleCreate() {
        final String selectedLabel = (String) objectTypeCombo.getSelectedItem();
        if (NO_TYPE_LABEL.equals(selectedLabel)) {
            JOptionPane.showMessageDialog(
                this,
                "Please select an object type.",
                "Validation Error",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        // Get the enum name from the label
        final String typeName = nameForLabel(selectedLabel);
        if (typeName == null) {
            JOptionPane.showMessageDialog(
                this,
                "Invalid object type selected.",
                "Error",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // Get description
        final String description = descriptionField.getText().trim();

        // Get edited tile data
        final int[] editedArgbData = tileEditorPanel.getWorkingPixels();

        // Build custom data map
        final Map<String, Object> customData = buildCustomDataMap();

        // Create the tile record
        final TileRecord tileRecord = tileService.createCustomTile(
            TileType.OBJECT_INTERACTIVE_SINGLE,
            description.isEmpty() ? null : description,
            originalArgbData,
            editedArgbData
        );

        // Create the level object record with the same ID
        final LevelObjectRecord levelObjectRecord = LevelObjectRecord.builder()
            .id(tileRecord.getId())
            .type(typeName)
            .description(description.isEmpty() ? null : description)
            .data(customData.isEmpty() ? null : customData)
            .build();

        levelObjectService.upsert(levelObjectRecord);

        log.info(
            "Created custom level object: id={}, type={}, description={}",
            tileRecord.getId(),
            typeName,
            description
        );

        JOptionPane.showMessageDialog(
            this,
            "Custom level object created successfully.\nTile ID: " + tileRecord.getId(),
            "Success",
            JOptionPane.INFORMATION_MESSAGE
        );

        dispose();
    }

    private Map<String, Object> buildCustomDataMap() {
        final Map<String, Object> data = new HashMap<>();
        for (int i = 0; i < customDataModel.getRowCount(); i++) {
            final String key = (String) customDataModel.getValueAt(i, 0);
            final String value = (String) customDataModel.getValueAt(i, 1);
            if (key != null && !key.trim().isEmpty()) {
                data.put(key.trim(), value != null ? value.trim() : "");
            }
        }
        return data;
    }

    private void updateCreateButtonState() {
        final boolean typeSelected = !NO_TYPE_LABEL.equals(objectTypeCombo.getSelectedItem());
        createButton.setEnabled(typeSelected);
    }

    private static JComboBox<String> buildObjectTypeCombo() {
        final JComboBox<String> combo = new JComboBox<>();
        combo.addItem(NO_TYPE_LABEL);
        stream(LevelObjectTypeSingleTiled.values())
            .sorted(comparing(LevelObjectTypeSingleTiled::getLabel))
            .forEach(t -> combo.addItem(t.getLabel()));
        return combo;
    }

    private static String nameForLabel(final String label) {
        for (final LevelObjectTypeSingleTiled value : LevelObjectTypeSingleTiled.values()) {
            if (value.getLabel().equals(label)) {
                return value.name();
            }
        }
        return null;
    }
}
