package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.enumeration.MusicTrack;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.game.level.pipe.PipePortalPlacementRules;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.MultiTileArea;
import house.x1337.app.smb3.model.game.PixelArea;
import house.x1337.app.smb3.model.game.enemy.EnemyPlacement;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.EnemyStampService;
import house.x1337.app.smb3.ui.editor.level.service.PaletteSelectionService;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import house.x1337.app.smb3.ui.editor.level.virtualobject.PipePortalDetails;
import house.x1337.app.smb3.ui.editor.level.virtualobject.PipePortalPrompt;
import house.x1337.app.smb3.ui.editor.tab.core.BaseEditorTab;
import house.x1337.app.smb3.util.factory.LevelSceneImportRouter;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

import javax.swing.JLabel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.GameConstants.TILE_SIZE;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.VIEWPORT_TILES_X;
import static house.x1337.app.smb3.GameConstants.VIEWPORT_TILES_Y;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.RENDERING_STARTER;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.SPAWN_POINT;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static house.x1337.app.smb3.util.factory.LevelSceneEditorTabFactory.forTab;
import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.SOUTH;
import static java.awt.Font.MONOSPACED;
import static java.awt.Font.PLAIN;
import static java.util.Comparator.comparingInt;
import static javax.swing.BorderFactory.createEmptyBorder;

@Data
@Prototype
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class LevelSceneEditorTab extends BaseEditorTab implements LevelSceneTileCapabilities {
    private static final Comparator<Coordinates> TOP_TO_BOTTOM_LEFT_TO_RIGHT =
        comparingInt(Coordinates::y).thenComparingInt(Coordinates::x);

    private final JLabel statusLabel = new JLabel(" ");
    private final LevelSceneEditorGrid levelSceneEditorGrid = forTab(this);
    private final LevelSceneImportRouter levelSceneImportRouter;
    private final TilePalettePanel tilePalettePanel;
    private final PaletteSelectionService paletteSelectionService;
    private final EnemyStampService enemyStampService;
    private final Runnable previewRefresher = levelSceneEditorGrid::refreshPreview;

    private Coordinates levelCompletionCard = null;
    private Coordinates renderingStarter = null;
    private Coordinates spawnPoint = null;
    private EnemyPlacement selectedEnemyPlacement = null;
    private LevelSceneDimensions levelSceneDimensions;
    private LevelSceneLayer activeLayer;
    private List<LevelSceneLayer> layers;
    private List<PipePortal> pipePortals = new ArrayList<>();
    private MusicTrack backgroundMusic = null;
    private String sceneDescription = null;
    private String sceneId = null;
    private String sceneTitle = null;
    private Tile[][] importGrid;

    @PostConstruct
    void init() {
        setLayout(new BorderLayout());

        levelSceneDimensions = new LevelSceneDimensions(VIEWPORT_TILES_X, VIEWPORT_TILES_Y);
        initLayers();

        final JScrollPane scrollPane = new JScrollPane(levelSceneEditorGrid);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(TILE_SIZE);
        scrollPane.getVerticalScrollBar().setUnitIncrement(TILE_SIZE);

        statusLabel.setBorder(createEmptyBorder(4, 8, 4, 8));
        statusLabel.setFont(new Font(MONOSPACED, PLAIN, 12));

        add(scrollPane, CENTER);
        add(statusLabel, SOUTH);

        paletteSelectionService.addSelectionListener(previewRefresher);
        updateStatus(null);
    }

    public void setActiveLayer(final LevelSceneLayer activeLayer) {
        this.activeLayer = activeLayer;
        levelSceneEditorGrid.refreshPreview();
    }

    public void newScene(final int newRows, final int newCols) {
        levelSceneDimensions = new LevelSceneDimensions(newCols, newRows);
        initLayers();
        resetContent();
        resetIdentity();
        refreshSize();
    }

    /**
     * Empties every layer and removes the markers, the card and the pipe portals. The scene keeps its id, title,
     * description, music and size, so saving it afterwards overwrites the stored scene.
     */
    public void clearScene() {
        for (final LevelSceneLayer layer : layers) {
            for (final Tile[] row : layer.getTiles()) {
                Arrays.fill(row, NULL_TILE);
            }
            layer.getEnemyPlacements().clear();
        }
        resetContent();
        levelSceneEditorGrid.repaint();
        updateStatus(null);
    }

    private void resetContent() {
        selectedEnemyPlacement = null;
        renderingStarter = null;
        spawnPoint = null;
        levelCompletionCard = null;
        pipePortals = new ArrayList<>();
    }

    private void resetIdentity() {
        sceneTitle = null;
        sceneDescription = null;
        backgroundMusic = null;
        sceneId = null;
    }

    /**
     * Lists the content a resize to {@code newColumns} by {@code newRows} would cut. The bottom-left corner stays
     * where it is, so shrinking cuts rows at the top and columns at the right.
     *
     * @return the cut cells, in the current coordinates, top to bottom and left to right, grouped by layer label
     *     (the card, the pipe portals and the enemies, by their rendering starter, count as the NPC layer's), then one
     *     group each for the spawn point and the
     *     rendering starter; empty when nothing would be lost
     */
    public Map<String, List<Coordinates>> findContentCutBy(final int newColumns, final int newRows) {
        final int cutTopRows = levelSceneDimensions.rows() - newRows;
        final Map<String, List<Coordinates>> cut = new LinkedHashMap<>();
        for (final LevelSceneLayer layer : layers) {
            final Set<Coordinates> cells = new LinkedHashSet<>();
            final Tile[][] tiles = layer.getTiles();
            for (int row = 0; row < levelSceneDimensions.rows(); row++) {
                for (int column = 0; column < levelSceneDimensions.columns(); column++) {
                    final Tile tile = tiles[row][column];
                    if (tile != null && tile != NULL_TILE && isCut(of(column, row), newColumns, cutTopRows)) {
                        cells.add(of(column, row));
                    }
                }
            }
            if (layer.getType() == NON_PLAYABLE_CHARACTERS) {
                for (final EnemyPlacement enemy : layer.getEnemyPlacements()) {
                    if (isCut(enemy.rendererCell(), newColumns, cutTopRows)) {
                        cells.add(enemy.rendererCell());
                    }
                }
                if (levelCompletionCard != null && isCut(levelCompletionCard, newColumns, cutTopRows)) {
                    cells.add(levelCompletionCard);
                }
                for (final PipePortal portal : pipePortals) {
                    for (final Coordinates cell : portal.cells()) {
                        if (isCut(cell, newColumns, cutTopRows)) {
                            cells.add(cell);
                        }
                    }
                }
            }
            if (!cells.isEmpty()) {
                cut.put(layer.getType().getLabel(), cells.stream().sorted(TOP_TO_BOTTOM_LEFT_TO_RIGHT).toList());
            }
        }
        final List<Coordinates> cutSpawnPointCells = spawnPointCells().stream()
            .filter(cell -> isCut(cell, newColumns, cutTopRows))
            .toList();
        if (!cutSpawnPointCells.isEmpty()) {
            cut.put(SPAWN_POINT.getLabel(), cutSpawnPointCells);
        }
        if (renderingStarter != null && isCut(renderingStarter, newColumns, cutTopRows)) {
            cut.put(RENDERING_STARTER.getLabel(), List.of(renderingStarter));
        }
        return cut;
    }

    private boolean isCut(final Coordinates cell, final int newColumns, final int cutTopRows) {
        return cell.y() < cutTopRows || cell.x() >= newColumns;
    }

    /**
     * Resizes the scene keeping its bottom-left corner where it is: rows are added or removed at the top and
     * columns at the right. Everything placed moves down by the rows added (up by the rows removed), so it keeps
     * its place relative to the ground. Whatever ends up outside the scene is dropped, which is why the size dialog
     * first checks {@link #findContentCutBy(int, int)}.
     */
    public void resizeScene(final int newColumns, final int newRows) {
        final int rowShift = newRows - levelSceneDimensions.rows();
        for (final LevelSceneLayer layer : layers) {
            layer.setTiles(shiftedTiles(layer.getTiles(), newColumns, newRows, rowShift));
        }
        final LevelSceneDimensions resized = new LevelSceneDimensions(newColumns, newRows);
        for (final LevelSceneLayer layer : layers) {
            layer.setEnemyPlacements(new ArrayList<>(layer.getEnemyPlacements()
                .stream()
                .map(enemy -> enemy.movedBy(0, rowShift * TILE_SPRITE_SIZE))
                .filter(enemy -> !isOutside(enemy.rendererCell(), resized))
                .toList()));
        }
        selectedEnemyPlacement = null;
        if (importGrid != null) {
            importGrid = shiftedTiles(importGrid, newColumns, newRows, rowShift);
        }
        levelSceneDimensions = new LevelSceneDimensions(newColumns, newRows);
        renderingStarter = shiftedCell(renderingStarter, rowShift);
        spawnPoint = shiftedCell(spawnPoint, rowShift);
        levelCompletionCard = shiftedCell(levelCompletionCard, rowShift);
        for (final PipePortal portal : pipePortals) {
            portal.setCoordinates(of(portal.getCoordinates().x(), portal.getCoordinates().y() + rowShift));
        }
        pipePortals.removeIf(portal -> portal.cells().stream().anyMatch(this::isOutsideTheScene));
        refreshSize();
    }

    private Coordinates shiftedCell(
        final Coordinates cell,
        final int rowShift
    ) {
        if (cell == null) {
            return null;
        }
        final Coordinates shifted = of(cell.x(), cell.y() + rowShift);
        return isOutsideTheScene(shifted) ? null : shifted;
    }

    private void refreshSize() {
        levelSceneEditorGrid.setPreferredSize(levelSceneDimensions.toPixels());
        levelSceneEditorGrid.revalidate();
        levelSceneEditorGrid.repaint();
        updateStatus(null);
    }

    public boolean hasRenderingStarter() {
        return renderingStarter != null;
    }

    public boolean hasSpawnPoint() {
        return spawnPoint != null;
    }

    /** The spawn point's cells, upper first; empty while it is not placed. */
    public List<Coordinates> spawnPointCells() {
        if (spawnPoint == null) {
            return List.of();
        }
        return List.of(spawnPoint, spawnPoint.getOneBelow());
    }

    /** Whether a spawn point stored at {@code upperCell} has both of its cells inside the scene. */
    public boolean spawnPointFits(final Coordinates upperCell) {
        return !isOutsideTheScene(upperCell) && !isOutsideTheScene(upperCell.getOneBelow());
    }

    private Coordinates belowOf(final Coordinates cell) {
        return of(cell.x(), cell.y() + 1);
    }

    public boolean hasLevelCompletionCard() {
        return levelCompletionCard != null;
    }

    /**
     * Gives a blank scene one empty, visible layer per {@link LevelSceneLayerType}, bottom to top, so every
     * layer is editable from the start, as it is for loaded and imported scenes. Air is the active one.
     */
    private void initLayers() {
        layers = new ArrayList<>(Arrays.stream(LevelSceneLayerType.values())
            .sorted(comparingInt(LevelSceneLayerType::getOrder))
            .map(type -> LevelSceneLayer.builder()
                .type(type)
                .visible(true)
                .tiles(emptyTiles(levelSceneDimensions.rows(), levelSceneDimensions.columns()))
                .build())
            .toList());
        activeLayer = layers.stream()
            .filter(layer -> layer.getType() == AIR)
            .findFirst()
            .orElseThrow();
    }

    public void paintTile(final Coordinates cell) {
        paintTile(cell, null);
    }

    /**
     * Places the armed palette item on {@code cell}. An armed enemy goes on {@code enemyPixel}, the level pixel for
     * the top-left of its rendering starter part, or exactly on the cell when that is {@code null}.
     */
    public void paintTile(final Coordinates cell, final Coordinates enemyPixel) {
        if (isOutsideTheScene(cell)) {
            return;
        }
        final VirtualObjectType selectedVirtualObject = paletteSelectionService.getSelectedVirtualObject();
        if (selectedVirtualObject != null) {
            switch (selectedVirtualObject.getCategory()) {
                case PIPE_PORTAL -> placePipePortal(selectedVirtualObject, cell);
                case CARD -> placeLevelCompletionCard(cell);
                case RENDERING_STARTER -> placeRenderingStarter(cell);
                case SPAWN_POINT -> placeSpawnPoint(cell);
            }
            return;
        }
        final EnemyStamp selectedEnemy = paletteSelectionService.getSelectedEnemy();
        if (selectedEnemy != null) {
            placeEnemy(selectedEnemy, enemyPixel == null
                ? selectedEnemy.placedAt(cell)
                : selectedEnemy.placedAtPixel(enemyPixel));
            return;
        }
        final Tile selectedTile = paletteSelectionService.getSelectedTile();
        if (selectedTile == null) {
            return;
        }
        final PixelArea placed = new MultiTileArea(cell).toPixelArea();
        if (refusesOverlap(placed)) {
            return;
        }
        activeLayer.getTiles()[cell.y()][cell.x()] = selectedTile;
        levelSceneEditorGrid.notePlacement(placed);
        levelSceneEditorGrid.repaint();
    }

    public void eraseTile(final Coordinates cell) {
        final int half = TILE_SPRITE_SIZE / 2;
        eraseAt(cell, of(cell.x() * TILE_SPRITE_SIZE + half, cell.y() * TILE_SPRITE_SIZE + half));
    }

    /**
     * Clears a cell of the active layer, the click falling on {@code levelPixel} inside it. On the NPC layer, a
     * virtual object or an enemy there is removed instead and the layer's tiles are left alone. Objects drawn on
     * top go first: the card, then the rendering starter, then the spawn point, then the pipe portals, then the
     * topmost enemy whose box holds the pixel.
     */
    public void eraseAt(final Coordinates cell, final Coordinates levelPixel) {
        if (!isOutsideTheScene(cell)) {
            if (removeLevelCompletionCardAt(cell)
                || removeRenderingStarterAt(cell)
                || removeSpawnPointAt(cell)
                || removePipePortalAt(cell)
                || removeEnemyAt(levelPixel)) {
                levelSceneEditorGrid.repaint();
                return;
            }
            activeLayer.getTiles()[cell.y()][cell.x()] = NULL_TILE;
            levelSceneEditorGrid.repaint();
        }
    }

    private boolean isOutsideTheScene(final Coordinates cell) {
        return isOutside(cell, levelSceneDimensions);
    }

    private boolean isOutside(final Coordinates cell, final LevelSceneDimensions dimensions) {
        return cell.y() < 0 || cell.y() >= dimensions.rows() || cell.x() < 0 || cell.x() >= dimensions.columns();
    }

    /** Whether a virtual object is armed, which places on a click only, never on a drag. */
    public boolean isPlacingVirtualObject() {
        return paletteSelectionService.getSelectedVirtualObject() != null;
    }

    /**
     * Places a pipe portal anchored on the clicked cell, which becomes its lowest leftmost cell. The placement must
     * pass {@code PipePortalPlacementRules}; otherwise the author is told why and nothing is placed. The author then
     * picks the portal's type and description, and cancelling places nothing.
     */
    private void placePipePortal(final VirtualObjectType type, final Coordinates cell) {
        if (activeLayer == null || activeLayer.getType() != NON_PLAYABLE_CHARACTERS) {
            return;
        }
        final PipePortal candidate = PipePortal.builder()
            .virtualObjectType(type)
            .levelSceneId(sceneId)
            .coordinates(cell)
            .build();
        final PipePortalPrompt prompt = getBean(PipePortalPrompt.class);
        final Optional<String> broken = PipePortalPlacementRules.validate(
            levelSceneDimensions,
            staticEnvironmentTiles(),
            pipePortals,
            candidate
        );
        if (broken.isPresent()) {
            prompt.warn(this, broken.get());
            return;
        }
        final Optional<PipePortalDetails> details = prompt.ask(this, type, cell);
        if (details.isEmpty()) {
            return;
        }
        candidate.setType(details.get().type());
        candidate.setDescription(details.get().description());
        pipePortals.add(candidate);
        levelSceneEditorGrid.repaint();
    }

    private boolean removePipePortalAt(final Coordinates cell) {
        if (activeLayer == null || activeLayer.getType() != NON_PLAYABLE_CHARACTERS) {
            return false;
        }
        return pipePortals.removeIf(portal -> portal.occupies(cell));
    }

    /**
     * Puts the scene's level completion card on the clicked cell, on the NPC layer only. Any cell inside the scene
     * will do: there is no prompt and no placement rule. A scene has at most one card, so placing it again moves it.
     */
    private void placeLevelCompletionCard(final Coordinates cell) {
        if (activeLayer == null || activeLayer.getType() != NON_PLAYABLE_CHARACTERS) {
            return;
        }
        levelCompletionCard = cell;
        levelSceneEditorGrid.repaint();
    }

    private boolean removeLevelCompletionCardAt(final Coordinates cell) {
        if (activeLayer == null || activeLayer.getType() != NON_PLAYABLE_CHARACTERS) {
            return false;
        }
        if (!cell.equals(levelCompletionCard)) {
            return false;
        }
        levelCompletionCard = null;
        return true;
    }

    private void placeRenderingStarter(final Coordinates cell) {
        if (isNpcLayerActive()) {
            renderingStarter = cell;
            levelSceneEditorGrid.repaint();
        }
    }

    private boolean removeRenderingStarterAt(final Coordinates cell) {
        if (!isNpcLayerActive() || !cell.equals(renderingStarter)) {
            return false;
        }
        renderingStarter = null;
        return true;
    }

    /**
     * Puts the scene's one spawn point with its lower cell on the clicked one, on the NPC layer only, moving it if
     * already placed. A click on the top row places nothing, as the upper cell would fall outside the scene.
     */
    private void placeSpawnPoint(final Coordinates cell) {
        final Coordinates upperCell = of(cell.x(), cell.y() - 1);
        if (isNpcLayerActive() && spawnPointFits(upperCell)) {
            spawnPoint = upperCell;
            levelSceneEditorGrid.repaint();
        }
    }

    private boolean removeSpawnPointAt(final Coordinates cell) {
        if (!isNpcLayerActive() || !spawnPointCells().contains(cell)) {
            return false;
        }
        spawnPoint = null;
        return true;
    }

    private Tile[][] staticEnvironmentTiles() {
        if (layers == null) {
            return null;
        }
        return layers.stream()
            .filter(layer -> layer.getType() == STATIC_ENVIRONMENT)
            .findFirst()
            .map(LevelSceneLayer::getTiles)
            .orElse(null);
    }

    /**
     * @param cell the cell under the mouse, or {@code null} when there is none to report
     */
    public void updateStatus(final Coordinates cell) {
        updateStatus(cell, null);
    }

    /**
     * @param cell       the cell under the mouse, or {@code null} when there is none to report
     * @param enemyPixel where a click would place the armed enemy, or {@code null} when there is none
     */
    public void updateStatus(final Coordinates cell, final Coordinates enemyPixel) {
        final String pos = (cell != null && cell.x() >= 0 && cell.y() >= 0)
            ? "  |  col=" + cell.x() + "  row=" + cell.y()
            : "";
        final VirtualObjectType selectedVirtualObject = paletteSelectionService.getSelectedVirtualObject();
        if (selectedVirtualObject != null) {
            statusLabel.setText(
                selectedVirtualObject.getLabel()
                    + "  |  " + selectedVirtualObject.getColumns() + " × " + selectedVirtualObject.getRows() + " tiles"
                    + "  |  click places its lower-left tile"
                    + placementNote(selectedVirtualObject)
                    + pos
            );
            return;
        }
        final EnemyStamp selectedEnemy = paletteSelectionService.getSelectedEnemy();
        if (selectedEnemy != null) {
            statusLabel.setText(
                "Enemy: " + selectedEnemy.getEnemyType().getLabel()
                    + "  |  " + selectedEnemy.getColumns() + " × " + selectedEnemy.getRows() + " tiles"
                    + enemyPlacementNote(enemyPixel) + pos + selectedEnemyNote()
            );
            return;
        }
        final Tile selectedTile = paletteSelectionService.getSelectedTile();
        if (selectedTile == null) {
            statusLabel.setText("No tile selected — pick one from the Tiles palette" + selectedEnemyNote());
            return;
        }
        final TileType type = selectedTile.getType();
        final String tileName = type != null ? type.getLabel() : "(none)";
        final int rows = levelSceneDimensions.rows();
        final int columns = levelSceneDimensions.columns();
        statusLabel.setText(
            "Tile: " + tileName + "  |  " + rows + " rows × " + columns + " columns" + pos + selectedEnemyNote()
        );
    }

    private String enemyPlacementNote(final Coordinates enemyPixel) {
        return enemyPixel == null
            ? "  |  click places its rendering starter"
            : "  |  click places its rendering starter at x=" + enemyPixel.x() + " y=" + enemyPixel.y()
                + " (Ctrl: on the cell)";
    }

    /** The selected enemy's position in level pixels, and how to nudge it; empty while none is selected. */
    private String selectedEnemyNote() {
        return selectedEnemyPlacement == null
            ? ""
            : "  |  selected enemy at x=" + selectedEnemyPlacement.x() + " y=" + selectedEnemyPlacement.y()
                + " (arrows: 1 px, Shift: 4 px)";
    }

    /** Where the rendering starter or the spawn point sits, by its lower-left cell; empty for the other objects. */
    private String placementNote(final VirtualObjectType type) {
        return switch (type.getCategory()) {
            case RENDERING_STARTER -> placedAt(renderingStarter);
            case SPAWN_POINT -> placedAt(spawnPoint == null ? null : spawnPoint.getOneBelow());
            case CARD, PIPE_PORTAL -> "";
        };
    }

    private String placedAt(final Coordinates lowerLeft) {
        return lowerLeft == null
            ? "  |  not placed yet"
            : "  |  set at col=" + lowerLeft.x() + " row=" + lowerLeft.y();
    }

    /** Whether a grid click has anything to place — a tile, a whole enemy, or a virtual object. */
    public boolean hasPlaceableSelection() {
        return paletteSelectionService.hasSelection();
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

    @Override
    public String resolveTitle() {
        final String title = getSceneTitle();
        return (title != null && !title.isBlank()) ? title : "Untitled Scene";
    }

    @Override
    public void onClose() {
        paletteSelectionService.removeSelectionListener(previewRefresher);
        levelSceneEditorGrid.dispose();
        getBean(LevelSceneTabs.class).forget(this);
    }
}
