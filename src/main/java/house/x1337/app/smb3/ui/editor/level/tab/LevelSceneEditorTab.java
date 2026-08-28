package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.game.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.tab.core.BaseLevelSceneTab;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import house.x1337.app.smb3.ui.service.SelectedTileService;
import house.x1337.app.smb3.util.factory.LevelSceneImportRouter;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.GameConstants.TILE_SIZE;
import static house.x1337.app.smb3.GameConstants.VIEWPORT_TILES_X;
import static house.x1337.app.smb3.GameConstants.VIEWPORT_TILES_Y;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.DECORATIONS_LAND;
import static house.x1337.app.smb3.enumeration.TileType.RENDERING_STARTER;
import static house.x1337.app.smb3.enumeration.TileType.SPAWN_POINT;
import static house.x1337.app.smb3.util.factory.LevelSceneEditorTabFactory.forTab;
import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.SOUTH;
import static java.awt.Font.MONOSPACED;
import static java.awt.Font.PLAIN;
import static javax.swing.BorderFactory.createEmptyBorder;

@Data
@Prototype
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class LevelSceneEditorTab extends BaseLevelSceneTab {
    private List<LevelSceneLayer> layers;
    private LevelSceneLayer activeLayer;
    private int rows;
    private int columns;
    private int renderingStarterRow = -1;
    private int renderingStarterColumn = -1;
    private int spawnPointRow = -1;
    private int spawnPointColumn = -1;
    private String sceneTitle = null;
    private String sceneDescription = null;
    private String sceneId = null;

    /**
     * Raw {@code Tile[][]} as produced by a PNG import, kept so the layers can be re-routed every time
     * the user classifies another tile in the review window. {@code null} for scenes not built from an
     * import.
     */
    private Tile[][] importGrid;

    private final JLabel statusLabel = new JLabel(" ");

    private final LevelSceneEditorGrid levelSceneEditorGrid = forTab(this);
    private final LevelSceneImportRouter levelSceneImportRouter;
    private final TilePalettePanel tilePalettePanel;
    private final SelectedTileService selectedTileService;

    @PostConstruct
    void init() {
        setLayout(new BorderLayout());

        rows = VIEWPORT_TILES_Y;
        columns = VIEWPORT_TILES_X;
        initLayers();

        final JScrollPane scrollPane = new JScrollPane(levelSceneEditorGrid);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(TILE_SIZE);
        scrollPane.getVerticalScrollBar().setUnitIncrement(TILE_SIZE);

        statusLabel.setBorder(createEmptyBorder(4, 8, 4, 8));
        statusLabel.setFont(new Font(MONOSPACED, PLAIN, 12));

        add(scrollPane, CENTER);
        add(statusLabel, SOUTH);

        updateStatus(-1, -1);
    }

    public void newScene(final int newRows, final int newCols) {
        rows = newRows;
        columns = newCols;
        initLayers();
        renderingStarterRow = -1;
        renderingStarterColumn = -1;
        spawnPointRow = -1;
        spawnPointColumn = -1;
        sceneTitle = null;
        sceneDescription = null;
        sceneId = null;
        levelSceneEditorGrid.setPreferredSize(new Dimension(columns * TILE_SIZE, rows * TILE_SIZE));
        levelSceneEditorGrid.revalidate();
        levelSceneEditorGrid.repaint();
        updateStatus(-1, -1);
    }

    public void clearScene() {
        for (final LevelSceneLayer layer : layers) {
            for (final Tile[] row : layer.getTiles()) {
                Arrays.fill(row, NULL_TILE);
            }
        }
        renderingStarterRow = -1;
        renderingStarterColumn = -1;
        spawnPointRow = -1;
        spawnPointColumn = -1;
        sceneTitle = null;
        sceneDescription = null;
        sceneId = null;
        levelSceneEditorGrid.repaint();
        updateStatus(-1, -1);
    }

    public boolean hasRenderingStarter() {
        return renderingStarterRow >= 0 && renderingStarterColumn >= 0;
    }

    public boolean hasSpawnPoint() {
        return spawnPointRow >= 0 && spawnPointColumn >= 0;
    }

    private void initLayers() {
        final LevelSceneLayer airLayer = LevelSceneLayer.builder()
            .type(AIR)
            .visible(true)
            .tiles(emptyTiles(rows, columns))
            .build();
        final LevelSceneLayer decorationsLayer = LevelSceneLayer.builder()
            .type(DECORATIONS_LAND)
            .visible(true)
            .tiles(emptyTiles(rows, columns))
            .build();
        layers = new ArrayList<>(List.of(airLayer, decorationsLayer));
        activeLayer = airLayer;
    }

    private static Tile[][] emptyTiles(
        final int rows,
        final int cols
    ) {
        final Tile[][] t = new Tile[rows][cols];
        for (final Tile[] row : t) {
            Arrays.fill(row, NULL_TILE);
        }
        return t;
    }

    public void paintTile(final int col, final int row) {
        if (row < 0 || row >= rows || col < 0 || col >= columns) {
            return;
        }
        final Tile selectedTile = selectedTileService.getSelectedTile();
        if (selectedTile.isOfType(RENDERING_STARTER)) {
            // Virtual tile - do NOT modify the tile grid; just record the position.
            renderingStarterRow = row;
            renderingStarterColumn = col;
            levelSceneEditorGrid.repaint();
            return;
        }
        if (selectedTile.isOfType(SPAWN_POINT)) {
            // Virtual tile - do NOT modify the tile grid; just record the position.
            spawnPointRow = row;
            spawnPointColumn = col;
            levelSceneEditorGrid.repaint();
            return;
        }
        activeLayer.getTiles()[row][col] = selectedTile;
        levelSceneEditorGrid.repaint();
    }

    public void eraseTile(final int col, final int row) {
        if (row >= 0 && row < rows && col >= 0 && col < columns) {
            activeLayer.getTiles()[row][col] = NULL_TILE;
            levelSceneEditorGrid.repaint();
        }
    }

    public void updateStatus(final int col, final int row) {
        final Tile selectedTile = selectedTileService.getSelectedTile();
        if (selectedTile == null) {
            statusLabel.setText("No tile selected — pick one from the Tiles palette");
            return;
        }
        if (selectedTile.isOfType(RENDERING_STARTER)) {
            final String rs = hasRenderingStarter()
                ? "  |  set at col=" + renderingStarterColumn + " row=" + renderingStarterRow
                : "  |  not placed yet";
            statusLabel.setText("Rendering Starter (virtual)" + rs);
            return;
        }
        if (selectedTile.isOfType(SPAWN_POINT)) {
            final String sp = hasSpawnPoint()
                ? "  |  set at col=" + spawnPointColumn + " row=" + spawnPointRow
                : "  |  not placed yet";
            statusLabel.setText("Spawn Point (virtual)" + sp);
            return;
        }
        final TileType type = selectedTile.getType();
        final String tileName = type != null ? type.getLabel() : "(none)";
        final String pos = (col >= 0 && row >= 0) ? "  |  col=" + col + "  row=" + row : "";
        statusLabel.setText("Tile: " + tileName + "  |  " + rows + " rows × " + columns + " columns" + pos);
    }

    public Tile getSelectedTile() {
        return selectedTileService.getSelectedTile();
    }

    /**
     * Refreshes the rendering of a tile whose pixel data changed (e.g. after classification),
     * invalidating its cached image and repainting the grid.
     */
    public void refreshTile(final Tile tile) {
        levelSceneEditorGrid.invalidateTile(tile.getId());
        levelSceneEditorGrid.repaint();
    }

    /**
     * Adopts a freshly imported grid and routes its tiles into the per-type layers (see
     * {@link LevelSceneImportRouter}). The grid is retained so the layers can be re-routed as the user
     * classifies more tiles.
     */
    public void applyImportedLayers(final Tile[][] grid) {
        importGrid = grid;
        reRouteImportedLayers();
    }

    /**
     * Re-routes the retained import grid into the layers. Called as the user classifies tiles so newly
     * typed tiles move to their owning layer and the AIR auto-fill is recomputed. No-op for scenes not
     * built from an import.
     */
    public void reRouteImportedLayers() {
        if (importGrid == null) {
            return;
        }
        final Map<LevelSceneLayerType, Boolean> visibilityByType = new EnumMap<>(LevelSceneLayerType.class);
        if (layers != null) {
            for (final LevelSceneLayer layer : layers) {
                visibilityByType.put(layer.getType(), layer.isVisible());
            }
        }
        final LevelSceneLayerType activeType = (activeLayer != null) ? activeLayer.getType() : AIR;

        final List<LevelSceneLayer> routed = levelSceneImportRouter.route(importGrid, rows, columns);
        for (final LevelSceneLayer layer : routed) {
            final Boolean visible = visibilityByType.get(layer.getType());
            if (visible != null) {
                layer.setVisible(visible);
            }
        }
        layers = routed;
        activeLayer = routed.stream()
            .filter(layer -> layer.getType() == activeType)
            .findFirst()
            .orElse(routed.getFirst());
        levelSceneEditorGrid.repaint();
    }

    public String resolveTitle() {
        final String title = getSceneTitle();
        return (title != null && !title.isBlank()) ? title : "Untitled Scene";
    }

    // TODO: simplify way to get the label
    public void updateTabTitle() {
        final String resolvedTitle = resolveTitle();
        final LevelSceneEditorTabSystem tabSystem = getEditorTabSystem();
        final int index = tabSystem.indexOfComponent(this);
        if (index >= 0) {
            tabSystem.setTitleAt(index, resolvedTitle);
            final Component tabComponent = tabSystem.getTabComponentAt(index);
            if (tabComponent instanceof JPanel tabPanel) {
                for (final Component child : tabPanel.getComponents()) {
                    if (child instanceof JLabel label) {
                        label.setText(resolvedTitle);
                        break;
                    }
                }
            }
        }
    }
}
