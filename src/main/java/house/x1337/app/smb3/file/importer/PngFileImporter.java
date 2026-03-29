package house.x1337.app.smb3.file.importer;

import house.x1337.app.smb3.annotation.Singleton;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

@Slf4j
@Singleton
public class PngFileImporter {
    public Optional<BufferedImage> importPngFile(final JFrame parentFrame) {
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Import PNG");
        fileChooser.setFileFilter(new FileNameExtensionFilter("PNG Images", "png"));
        fileChooser.setAcceptAllFileFilterUsed(false);

        final int result = fileChooser.showOpenDialog(parentFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            final File file = fileChooser.getSelectedFile();

            if (!file.getName().toLowerCase().endsWith(".png")) {
                log.warn("Rejected non-PNG file: {}", file.getAbsolutePath());
                return Optional.empty();
            } else {
                BufferedImage bufferedImage = null;
                try {
                    bufferedImage = ImageIO.read(file);
                } catch (final IOException exception) {
                    log.error("Failed to read PNG file: {}", file.getAbsolutePath(), exception);
                }
                if (bufferedImage == null) {
                    log.warn("Could not decode PNG file: {}", file.getAbsolutePath());
                }
                return Optional.ofNullable(bufferedImage);
            }
        }
        return Optional.empty();
    }
}
