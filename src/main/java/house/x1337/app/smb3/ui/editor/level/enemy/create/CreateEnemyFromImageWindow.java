package house.x1337.app.smb3.ui.editor.level.enemy.create;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.enemy.EnemyType;
import house.x1337.app.smb3.model.game.enemy.Enemy;
import house.x1337.app.smb3.service.EnemyService;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemiesPalettePanel;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import lombok.extern.slf4j.Slf4j;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.NORTH;
import static java.awt.BorderLayout.SOUTH;
import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.WEST;
import static java.lang.System.currentTimeMillis;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.JOptionPane.INFORMATION_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;

@Slf4j
@Prototype
public final class CreateEnemyFromImageWindow extends JDialog {
    private final TileService tileService = getBean(TileService.class);
    private final EnemyService enemyService = getBean(EnemyService.class);

    private int[][][] partPixels;
    private EnemyTilesGridPanel gridPanel;

    private final JComboBox<EnemyType> enemyTypeCombo = new JComboBox<>(EnemyType.values());
    private final JTextField descriptionField = new JTextField(24);
    private final JButton saveButton = new JButton("Save");
    private final JButton cancelButton = new JButton("Cancel");

    public CreateEnemyFromImageWindow(final JFrame parent) {
        super(parent);
    }

    public void render(final BufferedImage image) {
        this.partPixels = splitIntoParts(image);
        this.gridPanel = getBean(EnemyTilesGridPanel.class);
        this.gridPanel.render(partPixels);

        setTitle("Create Enemy from Image");
        setModal(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);

        final JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(createEmptyBorder(10, 10, 10, 10));
        content.add(buildPropertiesPanel(), NORTH);
        content.add(buildGridPanel(), CENTER);
        content.add(buildFooter(), SOUTH);

        setContentPane(content);
        setMinimumSize(new Dimension(520, 420));
        pack();
        setLocationRelativeTo(getParent());
    }

    private JPanel buildPropertiesPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(createTitledBorder("Enemy Properties"));

        final GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.anchor = WEST;
        labelConstraints.insets = new Insets(5, 8, 5, 6);
        labelConstraints.gridx = 0;
        labelConstraints.gridy = 0;

        final GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.anchor = WEST;
        fieldConstraints.fill = HORIZONTAL;
        fieldConstraints.weightx = 1.0;
        fieldConstraints.insets = new Insets(5, 0, 5, 8);
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = 0;

        panel.add(new JLabel("Enemy Type:"), labelConstraints);
        enemyTypeCombo.setRenderer(getBean(EnemyTypeCellRenderer.class));
        panel.add(enemyTypeCombo, fieldConstraints);

        labelConstraints.gridy++;
        fieldConstraints.gridy++;
        panel.add(new JLabel("Description:"), labelConstraints);
        panel.add(descriptionField, fieldConstraints);

        return panel;
    }

    private JPanel buildGridPanel() {
        final JPanel container = new JPanel(new BorderLayout());
        container.setBorder(createTitledBorder("Tiles (click a tile to set the rendering starter)"));

        final JPanel centered = new JPanel(new FlowLayout(FlowLayout.CENTER));
        centered.add(gridPanel);
        container.add(new JScrollPane(centered), CENTER);

        return container;
    }

    private JPanel buildFooter() {
        final JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        saveButton.addActionListener(event -> handleSave());
        cancelButton.addActionListener(event -> dispose());
        footer.add(saveButton);
        footer.add(cancelButton);
        return footer;
    }

    private void handleSave() {
        final EnemyType enemyType = (EnemyType) enemyTypeCombo.getSelectedItem();
        final String typed = descriptionField.getText().trim();
        final String description = typed.isEmpty() ? null : typed;

        final int rows = partPixels.length;
        final int columns = partPixels[0].length;
        final int[] tileIds = tileService.createEnemyPartTiles(flattenParts(), description);

        final Enemy enemy = Enemy
            .builder()
            .id(UUID.randomUUID().toString())
            .description(description)
            .enemyType(enemyType)
            .tiles(enemyService.toTileGrid(tileIds, rows, columns))
            .renderingStarterRow(gridPanel.getRenderingStarterRow())
            .renderingStarterColumn(gridPanel.getRenderingStarterColumn())
            .updatedAt(currentTimeMillis())
            .build();

        enemyService.upsert(enemy);
        publishPartsToPalette(tileIds);
        getBean(EnemiesPalettePanel.class).addEnemy(enemy);

        assert enemyType != null;
        showMessageDialog(
            this,
            "Enemy saved.\n\nType: %s\nTiles: %d x %d\nRendering starter: row %d, column %d (tile id %d)"
                .formatted(
                    enemyType.getLabel(),
                    columns,
                    rows,
                    enemy.getRenderingStarterRow(),
                    enemy.getRenderingStarterColumn(),
                    enemy.renderingStarterTileId()
                ),
            "Enemy Created",
            INFORMATION_MESSAGE
        );

        dispose();
    }

    /**
     * Puts the freshly saved parts into the tile palette so the author can paint the enemy into a level
     * straight away, exactly as classifying a tile in the new-tiles review does. {@code addTile} ignores
     * ids it already holds, so a part reused from an earlier enemy is not duplicated.
     */
    private void publishPartsToPalette(final int[] tileIds) {
        final TilePalettePanel palette = getBean(TilePalettePanel.class);
        for (final int tileId : tileIds) {
            tileService.findById(tileId).ifPresent(palette::addTile);
        }
    }

    private List<int[]> flattenParts() {
        final List<int[]> flattened = new ArrayList<>();
        for (final int[][] row : partPixels) {
            flattened.addAll(Arrays.asList(row));
        }
        return flattened;
    }

    private static int[][][] splitIntoParts(final BufferedImage image) {
        final int size = TILE_SPRITE_SIZE;
        final int rows = image.getHeight() / size;
        final int columns = image.getWidth() / size;
        final int[][][] parts = new int[rows][columns][];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                parts[row][column] = image.getRGB(column * size, row * size, size, size, null, 0, size);
            }
        }
        return parts;
    }
}
