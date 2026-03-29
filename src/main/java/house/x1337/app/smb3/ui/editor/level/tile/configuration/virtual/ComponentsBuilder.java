package house.x1337.app.smb3.ui.editor.level.tile.configuration.virtual;

import house.x1337.app.smb3.model.ui.tile.Tile;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;

import static house.x1337.app.smb3.model.ui.tile.TileCapabilities.THUMB_SIZE;
import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.NORTH;
import static java.awt.BorderLayout.SOUTH;
import static java.awt.FlowLayout.RIGHT;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.JSplitPane.HORIZONTAL_SPLIT;
import static javax.swing.ListSelectionModel.SINGLE_SELECTION;
import static javax.swing.event.TableModelEvent.UPDATE;

public sealed interface ComponentsBuilder permits ConfigureVirtualTilesWindow {
    List<Tile> getVirtualTiles();
    VirtualTilePreviewPanel getPreviewPanel();

    void onTileUpdated(Tile tile);

    private TableModel buildTableModel() {
        final TableModel model = new AbstractTableModel() {
            private static final String[] COLUMNS = {"ID", "Type", "Description"};

            @Override
            public int getRowCount() {
                return getVirtualTiles().size();
            }

            @Override
            public int getColumnCount() {
                return COLUMNS.length;
            }

            @Override
            public String getColumnName(final int column) {
                return COLUMNS[column];
            }

            @Override
            public Object getValueAt(final int rowIndex, final int columnIndex) {
                final Tile tile = getVirtualTiles().get(rowIndex);
                return switch (columnIndex) {
                    case 0 -> tile.getId();
                    case 1 -> tile.getType() != null ? tile.getType().getLabel() : "";
                    case 2 -> tile.getDescription() != null ? tile.getDescription() : "";
                    default -> "";
                };
            }

            @Override
            public void setValueAt(
                final Object value,
                final int rowIndex,
                final int columnIndex
            ) {
                if (columnIndex != 2) {
                    return;
                }
                final Tile tile = getVirtualTiles().get(rowIndex);
                final String desc = value != null ? value.toString().trim() : "";
                tile.setDescription(desc.isEmpty() ? null : desc);
                fireTableCellUpdated(rowIndex, columnIndex);
            }

            @Override
            public boolean isCellEditable(final int rowIndex, final int columnIndex) {
                return columnIndex == 2;
            }
        };
        model.addTableModelListener(e -> {
            if (e.getType() == UPDATE && e.getColumn() == 2) {
                final int row = e.getFirstRow();
                if (row >= 0 && row < getVirtualTiles().size()) {
                    onTileUpdated(getVirtualTiles().get(row));
                }
            }
        });
        return model;
    }

    private JTable buildTable() {
        final TableModel model = buildTableModel();
        final JTable table = new JTable(model);
        table.setSelectionMode(SINGLE_SELECTION);
        table.setRowHeight(28);
        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(240);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                final int row = table.getSelectedRow();
                getPreviewPanel().setTile(row >= 0 ? getVirtualTiles().get(row) : null);
            }
        });

        return table;
    }

    private JPanel buildPreviewPanel() {
        final JPanel panel = new JPanel(new BorderLayout(4, 8));
        panel.setBorder(createTitledBorder("Sprite Preview"));
        panel.setPreferredSize(new Dimension(THUMB_SIZE + 40, THUMB_SIZE + 60));

        final JLabel hint = new JLabel("Select a tile to preview", JLabel.CENTER);
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 11f));
        panel.add(hint, NORTH);
        panel.add(getPreviewPanel(), CENTER);

        return panel;
    }

    default JPanel buildContent() {
        final ConfigureVirtualTilesWindow thisWindow = (ConfigureVirtualTilesWindow) this;
        final JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(createEmptyBorder(10, 10, 10, 10));

        final JTable table = buildTable();
        final JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(createTitledBorder("Virtual Tiles"));

        final JSplitPane split = new JSplitPane(
            HORIZONTAL_SPLIT,
            tableScroll,
            buildPreviewPanel()
        );
        split.setDividerLocation(480);
        split.setResizeWeight(0.75);
        root.add(split, CENTER);

        final JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> {
            thisWindow.setVisible(false);
            thisWindow.dispose();
        });
        final JPanel footer = new JPanel(new FlowLayout(RIGHT));
        footer.add(closeBtn);
        root.add(footer, SOUTH);

        return root;
    }
}
