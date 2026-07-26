package house.x1337.app.smb3.util.factory;

import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.game.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.service.TileImportResult;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorGrid;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import house.x1337.app.smb3.ui.service.SelectedTileService;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.GameConstants.TILE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.*;
import static java.time.LocalDateTime.now;
import static java.time.format.DateTimeFormatter.ofPattern;

public interface LevelSceneEditorTabFactory {
    static LevelSceneEditorTab fromImport(final TileImportResult result) {
        final LevelSceneEditorTab tab = newInstance();
        final LevelSceneEditorGrid grid = tab.getLevelSceneEditorGrid();
        final int rows = result.rows();
        final int columns = result.columns();
        tab.setRows(rows);
        tab.setColumns(columns);
        // Distribute the imported tiles across the per-type layers; already-resolved tiles land in
        // their owning layer now, the rest follow as the user classifies them (see review window).
        tab.applyImportedLayers(result.grid());
        tab.setRenderingStarterRow(-1);
        tab.setRenderingStarterColumn(-1);
        tab.setSpawnPointRow(-1);
        tab.setSpawnPointColumn(-1);
        tab.setSceneTitle(generateTemporarySceneTitle());
        tab.setSceneDescription(null);
        tab.setSceneId(null);
        tabAndGridUpdate(tab, grid);
        return tab;
    }

    static LevelScene fromLevelSceneEditor(final LevelSceneEditorTab editor) {
        final LevelScene levelScene = getBean(LevelScene.class);
        final List<LevelSceneLayer> layers = editor.getLayers();
        levelScene.setId(editor.getSceneId());
        levelScene.setTitle(editor.getSceneTitle());
        levelScene.setDescription(editor.getSceneDescription());
        levelScene.setDimensions(new LevelSceneDimensions(
            editor.getColumns(),
            editor.getRows()
        ));
        for (final LevelSceneLayer layer : layers) {
            final LevelSceneLayer copy = copyLevelSceneLayer(layer);
            switch (layer.getType()) {
                case AIR -> levelScene.setAirLayer(copy);
                case DECORATIONS_AIR -> levelScene.setAirDecorationsLayer(copy);
                case DECORATIONS_LAND -> levelScene.setLandDecorationsLayer(copy);
                case STATIC_ENVIRONMENT -> levelScene.setStaticEnvironmentLayer(copy);
                case INTERACTIVE_OBJECTS -> levelScene.setInteractiveObjectsLayer(copy);
                case NON_PLAYABLE_CHARACTERS -> levelScene.setNonPlayableCharactersLayer(copy);
            }
        }
        levelScene.setRenderingStarterRow(editor.getRenderingStarterRow());
        levelScene.setRenderingStarterColumn(editor.getRenderingStarterColumn());
        levelScene.setSpawnPointRow(editor.getSpawnPointRow());
        levelScene.setSpawnPointColumn(editor.getSpawnPointColumn());
        return levelScene;
    }

    static LevelSceneEditorTab fromScene(final LevelScene levelScene) {
        final LevelSceneEditorTab tab = newInstance();
        final LevelSceneEditorGrid grid = tab.getLevelSceneEditorGrid();
        final int columns = levelScene.getDimensions().columns();
        final int rows = levelScene.getDimensions().rows();
        tab.setRows(rows);
        tab.setColumns(columns);
        final LevelSceneLayer airLayer = copyLevelSceneLayer(levelScene, AIR);
        final LevelSceneLayer airDecorationsLayer = copyLevelSceneLayer(levelScene, DECORATIONS_AIR);
        final LevelSceneLayer landDecorationsLayer = copyLevelSceneLayer(levelScene, DECORATIONS_LAND);
        final LevelSceneLayer staticEnvironmentLayer = copyLevelSceneLayer(levelScene, STATIC_ENVIRONMENT);
        final LevelSceneLayer interactiveObjectsLayer = copyLevelSceneLayer(levelScene, INTERACTIVE_OBJECTS);
        final LevelSceneLayer nonPlayableCharactersLayer = copyLevelSceneLayer(levelScene, NON_PLAYABLE_CHARACTERS);
        applyLayers(
            tab,
            airLayer,
            airDecorationsLayer,
            landDecorationsLayer,
            staticEnvironmentLayer,
            interactiveObjectsLayer,
            nonPlayableCharactersLayer
        );
        final Integer rsRow = levelScene.getRenderingStarterRow();
        final Integer rsCol = levelScene.getRenderingStarterColumn();
        final Integer spRow = levelScene.getSpawnPointRow();
        final Integer spCol = levelScene.getSpawnPointColumn();
        tab.setRenderingStarterRow((rsRow != null) ? rsRow : -1);
        tab.setRenderingStarterColumn((rsCol != null) ? rsCol : -1);
        tab.setSpawnPointRow((spRow != null) ? spRow : -1);
        tab.setSpawnPointColumn((spCol != null) ? spCol : -1);
        tab.setSceneTitle(levelScene.getTitle());
        tab.setSceneDescription(levelScene.getDescription());
        tab.setSceneId(levelScene.getId());
        tabAndGridUpdate(tab, grid);
        return tab;
    }

    private static void applyLayers(
        final LevelSceneEditorTab tab,
        final LevelSceneLayer airLayer,
        final LevelSceneLayer airDecorationsLayer,
        final LevelSceneLayer landDecorationsLayer,
        final LevelSceneLayer staticEnvironmentLayer,
        final LevelSceneLayer interactiveObjectsLayer,
        final LevelSceneLayer nonPlayableCharactersLayer
    ) {
        tab.setLayers(new ArrayList<>(List.of(
            airLayer,
            airDecorationsLayer,
            landDecorationsLayer,
            staticEnvironmentLayer,
            interactiveObjectsLayer,
            nonPlayableCharactersLayer
        )));
        tab.setActiveLayer(airLayer);
    }

    private static LevelSceneLayer newVisibleLayer(
        final LevelSceneLayerType type,
        final Tile[][] tiles
    ) {
        return LevelSceneLayer.builder()
            .type(type)
            .visible(true)
            .tiles(tiles)
            .build();
    }

    private static LevelSceneLayer copyLevelSceneLayer(
        final LevelScene sourceScene,
        final LevelSceneLayerType targetType
    ) {
        if (sourceScene == null) {
            return null;
        }
        final LevelSceneLayer sourceLayer = switch (targetType) {
            case AIR -> sourceScene.getAirLayer();
            case DECORATIONS_AIR -> sourceScene.getAirDecorationsLayer();
            case DECORATIONS_LAND -> sourceScene.getLandDecorationsLayer();
            case STATIC_ENVIRONMENT -> sourceScene.getStaticEnvironmentLayer();
            case INTERACTIVE_OBJECTS -> sourceScene.getInteractiveObjectsLayer();
            case NON_PLAYABLE_CHARACTERS -> sourceScene.getNonPlayableCharactersLayer();
        };
        final int rows = sourceScene.getDimensions().rows();
        final int columns = sourceScene.getDimensions().columns();
        if (sourceLayer == null) {
            return newVisibleLayer(targetType, emptyTiles(rows, columns));
        }
        final Tile[][] sourceTiles = sourceLayer.getTiles();
        final Tile[][] tiles = new Tile[rows][columns];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                final Tile t = (sourceTiles != null && sourceTiles[r] != null) ? sourceTiles[r][c] : null;
                tiles[r][c] = (t != null && t.isRenderable()) ? t : NULL_TILE;
            }
        }
        return LevelSceneLayer
            .builder()
            .visible(sourceLayer.isVisible())
            .type(sourceLayer.getType())
            .tiles(tiles)
            .build();
    }

    private static LevelSceneLayer copyLevelSceneLayer(final LevelSceneLayer layer) {
        final Tile[][] source = layer.getTiles();
        final Tile[][] copy = new Tile[source.length][];
        for (int r = 0; r < source.length; r++) {
            copy[r] = (source[r] != null) ? source[r].clone() : null;
        }
        return LevelSceneLayer.builder()
            .type(layer.getType())
            .visible(layer.isVisible())
            .tiles(copy)
            .build();
    }

    private static Tile[][] emptyTiles(
        final int rows,
        final int cols
    ) {
        final Tile[][] tiles = new Tile[rows][cols];
        for (final Tile[] row : tiles) {
            Arrays.fill(row, NULL_TILE);
        }
        return tiles;
    }

    private static void tabAndGridUpdate(
        final LevelSceneEditorTab tab,
        final LevelSceneEditorGrid grid
    ) {
        grid.setPreferredSize(new Dimension(tab.getColumns() * TILE_SIZE, tab.getRows() * TILE_SIZE));
        grid.revalidate();
        grid.repaint();
        tab.updateStatus(-1, -1);
    }

    private static LevelSceneEditorTab newInstance() {
        return getBean(
            LevelSceneEditorTab.class,
            getBean(LevelSceneImportRouter.class),
            getBean(TilePalettePanel.class),
            getBean(SelectedTileService.class)
        );
    }

    private static String generateTemporarySceneTitle() {
        final String timestamp = now().format(ofPattern("yyyy-MM-dd HH:mm:ss"));
        return "Untitled " + timestamp;
    }

    static LevelSceneEditorGrid forTab(final LevelSceneEditorTab levelSceneEditorTab) {
        return getBean(
            LevelSceneEditorGrid.class,
            levelSceneEditorTab
        );
    }
}
