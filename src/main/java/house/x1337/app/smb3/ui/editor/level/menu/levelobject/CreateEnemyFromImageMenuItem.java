package house.x1337.app.smb3.ui.editor.level.menu.levelobject;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.file.importer.PngFileImporter;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.enemy.create.CreateEnemyFromImageWindow;
import house.x1337.app.smb3.ui.editor.level.menu.LevelSceneEditorWindowMenuItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import java.awt.image.BufferedImage;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;

/**
 * {@code Level Objects > Enemies > Create from Image...}: picks a PNG, checks it is a whole number of
 * 16x16 tiles — the same rule a level-scene import enforces — and hands it to
 * {@link CreateEnemyFromImageWindow}. Nothing is persisted at this stage; a rejected or abandoned import
 * leaves no trace.
 */
@Singleton
@RequiredArgsConstructor
public class CreateEnemyFromImageMenuItem extends LevelSceneEditorWindowMenuItem {
    private final PngFileImporter pngFileImporter;
    private final TileService tileService;

    @PostConstruct
    void init() {
        setText("Create from Image...");
        addActionListener(event -> pngFileImporter
            .importPngFile(getParentFrame())
            .ifPresent(this::openWindowFor)
        );
    }

    private void openWindowFor(final BufferedImage image) {
        try {
            tileService.assertTileGridMultiples(image);
        } catch (final IllegalArgumentException exception) {
            showMessageDialog(getParentFrame(), exception.getMessage(), "Invalid Enemy Image", ERROR_MESSAGE);
            return;
        }

        final CreateEnemyFromImageWindow window = getBean(
            CreateEnemyFromImageWindow.class,
            getParentFrame()
        );
        window.render(image);
        window.setVisible(true);
    }
}
