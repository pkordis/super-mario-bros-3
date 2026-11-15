package house.x1337.app.smb3.ui.editor.level.virtualobject;

import house.x1337.app.smb3.annotation.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.DefaultResourceLoader;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import javax.imageio.ImageIO;

import static house.x1337.app.smb3.GameConstants.TILE_SCALE;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * The standing player drawn inside the spawn point on the editor grid: trimmed to its opaque pixels, mirrored, and
 * drawn at the tile scale, bottom-aligned and centred in the spawn point's box.
 */
@Slf4j
@Singleton
public class SpawnPointSprite {
    static final String RESOURCE = "classpath:sprites/player/mario/level/normal/still.png";

    private BufferedImage image;
    private boolean loaded;

    public void draw(final Graphics2D graphics, final Rectangle box) {
        final BufferedImage sprite = getImage();
        if (sprite == null) {
            return;
        }
        final int width = sprite.getWidth() * TILE_SCALE;
        final int height = sprite.getHeight() * TILE_SCALE;
        graphics.drawImage(
            sprite,
            box.x + (box.width - width) / 2,
            box.y + box.height - height,
            width,
            height,
            null
        );
    }

    /** @return the trimmed, mirrored sprite at its own size, or {@code null} when it cannot be read */
    public BufferedImage getImage() {
        if (!loaded) {
            loaded = true;
            image = read().map(this::mirrored).orElse(null);
        }
        return image;
    }

    private Optional<BufferedImage> read() {
        try (final InputStream input = new DefaultResourceLoader().getResource(RESOURCE).getInputStream()) {
            return Optional.ofNullable(ImageIO.read(input)).map(this::trimmed);
        } catch (final IOException e) {
            log.error("Failed to load the spawn point sprite: {}", RESOURCE, e);
            return Optional.empty();
        }
    }

    BufferedImage trimmed(final BufferedImage source) {
        int left = source.getWidth();
        int right = -1;
        int top = source.getHeight();
        int bottom = -1;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                if ((source.getRGB(x, y) >>> 24) != 0) {
                    left = min(left, x);
                    right = max(right, x);
                    top = min(top, y);
                    bottom = max(bottom, y);
                }
            }
        }
        if (right < 0) {
            return source;
        }
        return source.getSubimage(left, top, right - left + 1, bottom - top + 1);
    }

    BufferedImage mirrored(final BufferedImage source) {
        final int width = source.getWidth();
        final int height = source.getHeight();
        final BufferedImage mirror = new BufferedImage(width, height, TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                mirror.setRGB(width - 1 - x, y, source.getRGB(x, y));
            }
        }
        return mirror;
    }
}
