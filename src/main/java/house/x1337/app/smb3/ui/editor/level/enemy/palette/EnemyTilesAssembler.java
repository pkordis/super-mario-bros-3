package house.x1337.app.smb3.ui.editor.level.enemy.palette;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.ImageIcon;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.awt.RenderingHints.KEY_INTERPOLATION;
import static java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.lang.Math.clamp;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class EnemyTilesAssembler {
    private static final int MAX_ICON_WIDTH = 168;
    private static final int MAX_PIXEL_SCALE = 3;

    public BufferedImage assemble(final EnemyStamp enemy) {
        if (!enemy.isWellFormed()) {
            log.warn("Enemy {} cannot be assembled: {}", enemy.getId(), enemy);
            return new BufferedImage(TILE_SPRITE_SIZE, TILE_SPRITE_SIZE, TYPE_INT_ARGB);
        }
        final BufferedImage image = new BufferedImage(
            enemy.getColumns() * TILE_SPRITE_SIZE,
            enemy.getRows() * TILE_SPRITE_SIZE,
            TYPE_INT_ARGB
        );
        final Graphics2D graphics = image.createGraphics();
        try {
            for (int row = 0; row < enemy.getRows(); row++) {
                for (int column = 0; column < enemy.getColumns(); column++) {
                    final Tile tile = enemy.tileAt(row, column);
                    if (tile == null) {
                        continue;
                    }
                    final BufferedImage part = tile.toImage();
                    if (part != null) {
                        graphics.drawImage(part, column * TILE_SPRITE_SIZE, row * TILE_SPRITE_SIZE, null);
                    }
                }
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    public ImageIcon toIcon(final EnemyStamp enemy) {
        final BufferedImage assembled = assemble(enemy);
        final int scale = clamp(MAX_ICON_WIDTH / assembled.getWidth(), 1, MAX_PIXEL_SCALE);
        final int width = assembled.getWidth() * scale;
        final int height = assembled.getHeight() * scale;
        final BufferedImage scaled = new BufferedImage(width, height, TYPE_INT_ARGB);
        final Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(assembled, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return new ImageIcon(scaled);
    }
}
