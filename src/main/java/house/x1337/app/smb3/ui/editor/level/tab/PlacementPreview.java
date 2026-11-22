package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.MultiTileArea;
import house.x1337.app.smb3.model.game.PixelArea;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.enemy.EnemyPlacement;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.service.PaletteSelectionService;
import house.x1337.app.smb3.ui.editor.level.virtualobject.SpawnPointSprite;
import lombok.RequiredArgsConstructor;

import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.VirtualObjectCategory.SPAWN_POINT;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.awt.AlphaComposite.SRC_OVER;

/**
 * The selected palette item drawn half transparent under the pointer, where a left click would place it: on the cell,
 * or for an enemy on the pixel. See kb/editor.md, "Hover preview".
 */
@RequiredArgsConstructor
final class PlacementPreview implements GridBoxDrawing {
    static final float ALPHA = 0.5f;

    private final LevelSceneEditorTab tab;
    private final SpawnPointSprite spawnPointSprite;
    private final EnemyImages enemyImages;
    private final Function<Tile, BufferedImage> tileImages;

    /**
     * The pixels the preview covers with the pointer on {@code cell}, an armed enemy on {@code enemyPixel} (see
     * {@link LevelSceneEditorTab#paintTile(Coordinates, Coordinates)}); empty when nothing shows.
     */
    Optional<Rectangle> boxAt(final Coordinates cell, final Coordinates enemyPixel) {
        return previewAt(cell, enemyPixel).flatMap(preview -> preview
            .area()
            .clippedTo(tab.getLevelSceneDimensions())
            .map(this::pixelBox));
    }

    void paint(final Graphics2D graphics, final Coordinates cell, final Coordinates enemyPixel) {
        final Optional<Preview> preview = previewAt(cell, enemyPixel);
        if (preview.isEmpty()) {
            return;
        }
        final Graphics2D translucent = (Graphics2D) graphics.create();
        try {
            final Dimension levelSceneDimension = tab.getLevelSceneDimensions().toPixels();
            translucent.clip(new Rectangle(0, 0, levelSceneDimension.width, levelSceneDimension.height));
            translucent.setComposite(AlphaComposite.getInstance(SRC_OVER, ALPHA));
            preview.get().painter().accept(translucent, pixelBox(preview.get().area()));
        } finally {
            translucent.dispose();
        }
    }

    private Optional<Preview> previewAt(final Coordinates cell, final Coordinates enemyPixel) {
        final LevelSceneLayer activeLayer = tab.getActiveLayer();
        if (cell == null || isOutsideTheScene(cell) || activeLayer == null || !activeLayer.isVisible()) {
            return Optional.empty();
        }
        final boolean onNpcLayer = activeLayer.getType() == NON_PLAYABLE_CHARACTERS;
        final PaletteSelectionService selection = tab.getPaletteSelectionService();
        final VirtualObjectType virtualObject = selection.getSelectedVirtualObject();
        if (virtualObject != null) {
            return onNpcLayer ? virtualObjectPreview(virtualObject, cell) : Optional.empty();
        }
        final EnemyStamp enemy = selection.getSelectedEnemy();
        if (enemy != null) {
            if (!onNpcLayer || !enemy.isWellFormed()) {
                return Optional.empty();
            }
            final EnemyPlacement placement = enemyPixel == null
                ? enemy.placedAt(cell)
                : enemy.placedAtPixel(enemyPixel);
            return tab.isOutsideTheScene(placement) ? Optional.empty() : Optional.of(enemyPreview(enemy, placement));
        }
        final Tile tile = selection.getSelectedTile();
        if (tile == null || tile.getType() == null || !tile.getType().isPaintableOn(activeLayer.getType())) {
            return Optional.empty();
        }
        return Optional.of(new Preview(
            new MultiTileArea(cell).toPixelArea(),
            (graphics, box) -> drawImage(graphics, tile, box)
        ));
    }

    private Optional<Preview> virtualObjectPreview(final VirtualObjectType type, final Coordinates cell) {
        final MultiTileArea area = switch (type.getCategory()) {
            case SPAWN_POINT -> new MultiTileArea(cell.x(), cell.y() - 1, 1, 2);
            case PIPE_PORTAL -> new MultiTileArea(
                cell.x(),
                cell.y() - type.getRows() + 1,
                type.getColumns(),
                type.getRows()
            );
            case CARD, RENDERING_STARTER -> new MultiTileArea(cell);
        };
        if (type.getCategory() == SPAWN_POINT && !tab.spawnPointFits(of(cell.x(), cell.y() - 1))) {
            return Optional.empty();
        }
        return Optional.of(new Preview(area.toPixelArea(), (graphics, box) -> {
            if (type.getCategory() == SPAWN_POINT) {
                spawnPointSprite.draw(graphics, box);
            }
            drawBorder(graphics, type.getColour(), box);
        }));
    }

    private Preview enemyPreview(final EnemyStamp enemy, final EnemyPlacement placement) {
        final BufferedImage image = enemyImages.of(enemy);
        return new Preview(
            enemy.boundsOf(placement),
            (graphics, box) -> graphics.drawImage(image, box.x, box.y, box.width, box.height, null)
        );
    }

    private void drawImage(final Graphics2D graphics, final Tile tile, final Rectangle box) {
        final BufferedImage image = tileImages.apply(tile);
        if (image != null) {
            graphics.drawImage(image, box.x, box.y, box.width, box.height, null);
        }
    }

    private boolean isOutsideTheScene(final Coordinates cell) {
        final LevelSceneDimensions dimensions = tab.getLevelSceneDimensions();
        return cell.x() < 0 || cell.y() < 0 || cell.x() >= dimensions.columns() || cell.y() >= dimensions.rows();
    }

    private record Preview(PixelArea area, BiConsumer<Graphics2D, Rectangle> painter) {
    }
}
