package house.x1337.app.smb3.ui.editor.level.enemy.edit;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.EnemyStampService;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemiesPalettePanel;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneTabs;
import lombok.RequiredArgsConstructor;

import java.awt.Component;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.lang.System.currentTimeMillis;

/**
 * Saves an enemy edited in its edit window, a new rendering starter and pixels cleared in its parts, and brings
 * everything that shows it up to date: the tile and enemy stores, the Enemies palette and every open level scene
 * tab. A part is a tile other enemies may share, so clearing its pixels changes them too, after the author confirms.
 * See kb/editor.md, "Editing an enemy".
 */
@Singleton
@RequiredArgsConstructor
public class EnemyStampEditing {
    private final EnemyStampService enemyStampService;
    private final TileService tileService;
    private final LevelSceneTabs levelSceneTabs;
    private final EnemiesPalettePanel enemiesPalettePanel;
    private final EnemyWindows enemyWindows;

    /**
     * The pixels of {@code enemy}'s parts, by row and column, as copies to edit. Cells holding the same tile share one
     * copy, so a pixel cleared in one shows in all of them.
     */
    public int[][][] editablePixelsOf(final EnemyStamp enemy) {
        final Map<Integer, int[]> copiesByTileId = new HashMap<>();
        final int[][][] pixels = new int[enemy.getRows()][enemy.getColumns()][];
        for (int row = 0; row < enemy.getRows(); row++) {
            for (int column = 0; column < enemy.getColumns(); column++) {
                final Tile part = enemy.tileAt(of(column, row));
                pixels[row][column] = isEditable(part)
                    ? copiesByTileId.computeIfAbsent(part.getId(), id -> part.getDisplayArgbData().clone())
                    : new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE];
            }
        }
        return pixels;
    }

    /**
     * @param parent           the window the confirmation is centred on
     * @param enemy            the enemy as it is stored
     * @param renderingStarter the part to anchor it by from now on
     * @param pixels           its parts' pixels as edited, from {@link #editablePixelsOf(EnemyStamp)}
     * @return {@code false} when the author cancelled because other enemies share a changed part; nothing changed
     */
    public boolean save(
        final Component parent,
        final EnemyStamp enemy,
        final Coordinates renderingStarter,
        final int[][][] pixels
    ) {
        final Map<Tile, int[]> changedParts = changedParts(enemy, pixels);
        final List<EnemyStamp> sharing = enemiesSharing(enemy, changedParts);
        if (!sharing.isEmpty() && !enemyWindows.confirmSharedParts(parent, sharedPartsMessage(sharing))) {
            return false;
        }
        changedParts.forEach((part, edited) -> {
            part.setArgbData(edited.clone());
            tileService.updateTile(part);
        });
        final EnemyStamp edited = EnemyStamp
            .builder()
            .id(enemy.getId())
            .description(enemy.getDescription())
            .enemyType(enemy.getEnemyType())
            .tiles(enemy.getTiles())
            .renderingStarter(renderingStarter)
            .updatedAt(currentTimeMillis())
            .data(enemy.getData())
            .build();
        enemyStampService.upsert(edited);
        enemiesPalettePanel.addEnemy(edited);
        sharing.forEach(enemiesPalettePanel::addEnemy);
        for (final LevelSceneEditorTab tab : levelSceneTabs.getLevelSceneTabs()) {
            changedParts.keySet().forEach(tab::refreshTile);
            tab.getLevelSceneEditorGrid().repaint();
        }
        return true;
    }

    String sharedPartsMessage(final List<EnemyStamp> sharing) {
        final StringBuilder names = new StringBuilder();
        sharing.forEach(other -> names.append("<br>&nbsp;&nbsp;").append(other.getName()));
        return "<html>Some of the changed parts are also parts of:" + names
            + "<br><br>Clearing their pixels changes those enemies too.<br><br>Save it?</html>";
    }

    private Map<Tile, int[]> changedParts(final EnemyStamp enemy, final int[][][] pixels) {
        final Map<Integer, Tile> partsById = new LinkedHashMap<>();
        final Map<Tile, int[]> changed = new LinkedHashMap<>();
        for (int row = 0; row < enemy.getRows(); row++) {
            for (int column = 0; column < enemy.getColumns(); column++) {
                final Tile part = enemy.tileAt(of(column, row));
                if (!isEditable(part) || partsById.containsKey(part.getId())) {
                    continue;
                }
                partsById.put(part.getId(), part);
                if (!Arrays.equals(part.getDisplayArgbData(), pixels[row][column])) {
                    changed.put(part, pixels[row][column]);
                }
            }
        }
        return changed;
    }

    private List<EnemyStamp> enemiesSharing(final EnemyStamp enemy, final Map<Tile, int[]> changedParts) {
        return enemyStampService
            .findAll()
            .stream()
            .filter(other -> !other.getId().equals(enemy.getId()) && other.isWellFormed())
            .filter(other -> changedParts.keySet().stream().anyMatch(part -> other.containsTile(part.getId())))
            .toList();
    }

    private boolean isEditable(final Tile part) {
        return part != null && part != NULL_TILE && part.getId() != NULL_TILE.getId()
            && part.getDisplayArgbData() != null;
    }
}
