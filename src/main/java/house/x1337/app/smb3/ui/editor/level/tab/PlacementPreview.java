package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.MultiTileArea;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyTilesAssembler;
import house.x1337.app.smb3.ui.editor.level.service.PaletteSelectionService;
import house.x1337.app.smb3.ui.editor.level.virtualobject.SpawnPointSprite;
import lombok.RequiredArgsConstructor;

import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.VirtualObjectCategory.SPAWN_POINT;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.awt.AlphaComposite.SRC_OVER;

/**
 * The selected palette item drawn half transparent on the cell under the pointer, where a left click would place
 * it. See kb/editor.md, "Hover preview".
 */
@RequiredArgsConstructor
final class PlacementPreview implements GridBoxDrawing {
    static final float ALPHA = 0.5f;

    private final Map<EnemyStamp, BufferedImage> enemyImages = new IdentityHashMap<>();
    private final LevelSceneEditorTab tab;
    private final SpawnPointSprite spawnPointSprite;
    private final EnemyTilesAssembler enemyTilesAssembler;
    private final Function<Tile, BufferedImage> tileImages;

    /** The scene cells the preview covers with the pointer on {@code cell}; empty when nothing shows. */
    Optional<MultiTileArea> areaAt(final Coordinates cell) {
        return previewAt(cell).flatMap(preview -> preview.area().clippedTo(tab.getLevelSceneDimensions()));
    }

    void paint(final Graphics2D graphics, final Coordinates cell) {
        final Optional<Preview> preview = previewAt(cell);
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

    private Optional<Preview> previewAt(final Coordinates cell) {
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
            return onNpcLayer && enemy.isWellFormed() ? Optional.of(enemyPreview(enemy, cell)) : Optional.empty();
        }
        final Tile tile = selection.getSelectedTile();
        if (tile == null || tile.getType() == null || !tile.getType().isPaintableOn(activeLayer.getType())) {
            return Optional.empty();
        }
        return Optional.of(new Preview(new MultiTileArea(cell), (graphics, box) -> drawImage(graphics, tile, box)));
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
        return Optional.of(new Preview(area, (graphics, box) -> {
            if (type.getCategory() == SPAWN_POINT) {
                spawnPointSprite.draw(graphics, box);
            }
            drawBorder(graphics, type.getColour(), box);
        }));
    }

    private Preview enemyPreview(final EnemyStamp enemy, final Coordinates cell) {
        final BufferedImage image = enemyImages.computeIfAbsent(enemy, enemyTilesAssembler::assemble);
        return new Preview(
            enemy.placedAt(cell).bounds(),
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

    private record Preview(MultiTileArea area, BiConsumer<Graphics2D, Rectangle> painter) {
    }
}
