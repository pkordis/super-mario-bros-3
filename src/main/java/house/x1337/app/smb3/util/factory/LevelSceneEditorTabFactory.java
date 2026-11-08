package house.x1337.app.smb3.util.factory;

import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.service.TileImportResult;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyLevelSceneGridStamper;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorGrid;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import house.x1337.app.smb3.ui.service.PaletteSelectionService;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.GameConstants.TILE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.DECORATIONS_AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.DECORATIONS_LAND;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
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
        tab.setRenderingStarter(null);
        tab.setSpawnPoint(null);
        tab.setLevelCompletionCard(null);
        tab.setSceneTitle(generateTemporarySceneTitle());
        tab.setSceneDescription(null);
        tab.setSceneId(null);
        tab.setPipePortals(new ArrayList<>());
        tabAndGridUpdate(tab, grid);
        return tab;
    }

    static LevelScene fromLevelSceneEditor(final LevelSceneEditorTab editor) {
        final LevelScene levelScene = getBean(LevelScene.class);
        final List<LevelSceneLayer> layers = editor.getLayers();
        // A tab that was never saved has no scene id yet. The new scene then keeps the random id it was created
        // with, which the save hands back to the tab, instead of every first save sharing a null id.
        if (editor.getSceneId() != null) {
            levelScene.setId(editor.getSceneId());
        }
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
        levelScene.setRenderingStarter(editor.getRenderingStarter());
        levelScene.setSpawnPoint(editor.getSpawnPoint());
        levelScene.setLevelCompletionCard(editor.getLevelCompletionCard());
        levelScene.setPipePortals(copyOf(editor.getPipePortals()));
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
        tab.setRenderingStarter(placedOrNull(levelScene.getRenderingStarter()));
        tab.setSpawnPoint(placedOrNull(levelScene.getSpawnPoint()));
        tab.setLevelCompletionCard(placedOrNull(levelScene.getLevelCompletionCard()));
        tab.setSceneTitle(levelScene.getTitle());
        tab.setSceneDescription(levelScene.getDescription());
        tab.setSceneId(levelScene.getId());
        tab.setPipePortals(copyOf(levelScene.getPipePortals()));
        tabAndGridUpdate(tab, grid);
        return tab;
    }

    /**
     * A scene saved while a marker was unplaced may hold it at {@code -1}; the tab reads that as not placed.
     */
    private static Coordinates placedOrNull(final Coordinates cell) {
        return (cell == null || cell.x() < 0 || cell.y() < 0) ? null : cell;
    }

    private static List<PipePortal> copyOf(final List<PipePortal> pipePortals) {
        return pipePortals == null ? new ArrayList<>() : new ArrayList<>(pipePortals);
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
        tab.updateStatus(null);
    }

    private static LevelSceneEditorTab newInstance() {
        return getBean(
            LevelSceneEditorTab.class,
            getBean(LevelSceneImportRouter.class),
            getBean(TilePalettePanel.class),
            getBean(PaletteSelectionService.class),
            getBean(EnemyLevelSceneGridStamper.class)
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
