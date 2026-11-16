package house.x1337.app.smb3.ui.editor.level.menu.scene;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.file.importer.PngFileImporter;
import house.x1337.app.smb3.model.service.TileImportResult;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneTabs;
import house.x1337.app.smb3.ui.editor.menu.GameEditorWindowMenuItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

import static house.x1337.app.smb3.util.factory.LevelSceneEditorTabFactory.fromImport;
import static house.x1337.app.smb3.util.factory.NewTilesReviewWindowFactory.fromTiles;
import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.KeyEvent.VK_I;
import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;
import static javax.swing.KeyStroke.getKeyStroke;

@Singleton
@RequiredArgsConstructor
public class ImportFromPngLevelSceneMenuItem extends GameEditorWindowMenuItem {
    private final LevelSceneTabs levelSceneTabs;
    private final PngFileImporter pngFileImporter;
    private final TileService tileService;

    @PostConstruct
    void init() {
        setText("Import from PNG...");
        setAccelerator(getKeyStroke(VK_I, CTRL_DOWN_MASK));
        addActionListener(e -> pngFileImporter
            .importPngFile(getParentFrame())
            .ifPresent(this::importFromPng)
        );
    }

    private void importFromPng(final BufferedImage image) {
        try {
            final TileImportResult result = tileService.importFromImage(image);
            final LevelSceneEditorTab newTab = fromImport(result);

            levelSceneTabs.openUnsaved(newTab);

            final List<Tile> needReview = Arrays.stream(result.grid())
                .flatMap(Arrays::stream)
                .filter(tile -> tile != null && tile.isUnresolved())
                .distinct()
                .toList();

            if (!needReview.isEmpty()) {
                fromTiles(needReview).setVisible(true);
            }
        } catch (final IllegalArgumentException ex) {
            showMessageDialog(this, ex.getMessage(), "Import Error", ERROR_MESSAGE);
        }
    }
}
