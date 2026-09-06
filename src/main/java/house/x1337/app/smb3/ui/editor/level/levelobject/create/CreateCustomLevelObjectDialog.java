package house.x1337.app.smb3.ui.editor.level.levelobject.create;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.file.importer.PngFileImporter;
import house.x1337.app.smb3.ui.editor.level.LevelSceneEditorWindow;
import jakarta.annotation.PostConstruct;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.image.BufferedImage;
import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.SOUTH;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.BoxLayout.Y_AXIS;

/**
 * Initial dialog for creating a custom level object.
 * Allows the user to select Single Tiled or Multi-Tiled, and choose the import method.
 */
@Prototype
public final class CreateCustomLevelObjectDialog extends JDialog {
    private final PngFileImporter pngFileImporter = getBean(PngFileImporter.class);
    private final JFrame parentFrame;

    private final JRadioButton singleTiledRadio = new JRadioButton("Single Tiled", true);
    private final JRadioButton multiTiledRadio = new JRadioButton("Multi-Tiled");
    private final JButton importFromPngButton = new JButton("Import Tile from PNG");
    private final JButton selectExistingButton = new JButton("Select existing Tile");

    public CreateCustomLevelObjectDialog(final LevelSceneEditorWindow parent) {
        super(parent);
        this.parentFrame = parent;
    }

    @PostConstruct
    void init() {
        setTitle("Create Custom Level Object");
        setModal(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        final JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(createEmptyBorder(15, 15, 15, 15));

        // Tile type selection panel
        final JPanel typePanel = new JPanel();
        typePanel.setLayout(new BoxLayout(typePanel, Y_AXIS));
        typePanel.setBorder(createTitledBorder("Object Type"));

        final ButtonGroup typeGroup = new ButtonGroup();
        typeGroup.add(singleTiledRadio);
        typeGroup.add(multiTiledRadio);

        // Multi-tiled is disabled for now
        multiTiledRadio.setEnabled(false);
        multiTiledRadio.setToolTipText("Coming soon");

        typePanel.add(singleTiledRadio);
        typePanel.add(Box.createVerticalStrut(5));
        typePanel.add(multiTiledRadio);

        // Import method panel
        final JPanel importPanel = new JPanel();
        importPanel.setLayout(new BoxLayout(importPanel, Y_AXIS));
        importPanel.setBorder(createTitledBorder("Import Method"));

        importFromPngButton.setAlignmentX(LEFT_ALIGNMENT);
        importFromPngButton.addActionListener(e -> handleImportFromPng());

        selectExistingButton.setAlignmentX(LEFT_ALIGNMENT);
        selectExistingButton.setEnabled(false);
        selectExistingButton.setToolTipText("Coming soon");

        importPanel.add(importFromPngButton);
        importPanel.add(Box.createVerticalStrut(8));
        importPanel.add(selectExistingButton);

        // Main content
        final JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, Y_AXIS));
        mainPanel.add(typePanel);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(importPanel);

        content.add(mainPanel, CENTER);

        // Footer with Cancel button
        final JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        final JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        footer.add(cancelButton);
        content.add(footer, SOUTH);

        setContentPane(content);
        setMinimumSize(new Dimension(320, 250));
        pack();
        setLocationRelativeTo(getParent());
    }

    private void handleImportFromPng() {
        final Optional<BufferedImage> imageOpt = pngFileImporter.importPngFile(parentFrame);
        if (imageOpt.isEmpty()) {
            return;
        }

        final BufferedImage image = imageOpt.get();
        final int width = image.getWidth();
        final int height = image.getHeight();

        // Validate dimensions
        if (width != TILE_SPRITE_SIZE || height != TILE_SPRITE_SIZE) {
            JOptionPane.showMessageDialog(
                this,
                "Invalid tile dimensions: " + width + "×" + height + " pixels.\n\n" +
                    "A single-tiled object must be exactly " + TILE_SPRITE_SIZE + "×" + TILE_SPRITE_SIZE + " pixels.",
                "Invalid Tile Size",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // Extract the original ARGB data
        final int[] originalArgbData = image.getRGB(
            0, 0,
            TILE_SPRITE_SIZE, TILE_SPRITE_SIZE,
            null, 0, TILE_SPRITE_SIZE
        );

        // Close this dialog and open the editor window
        dispose();

        final CreateCustomLevelObjectWindow editorWindow = getBean(
            CreateCustomLevelObjectWindow.class,
            parentFrame,
            originalArgbData
        );
        editorWindow.setVisible(true);
    }
}
