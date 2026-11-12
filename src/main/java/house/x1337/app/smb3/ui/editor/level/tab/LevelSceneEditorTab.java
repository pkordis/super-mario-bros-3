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
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyLevelSceneGridStamper;
import house.x1337.app.smb3.ui.editor.level.tab.core.BaseLevelSceneTab;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import house.x1337.app.smb3.ui.editor.level.virtualobject.PipePortalDetails;
import house.x1337.app.smb3.ui.editor.level.virtualobject.PipePortalPrompt;
import house.x1337.app.smb3.ui.service.PaletteSelectionService;
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
import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.GameConstants.TILE_SIZE;
import static house.x1337.app.smb3.GameConstants.VIEWPORT_TILES_X;
import static house.x1337.app.smb3.GameConstants.VIEWPORT_TILES_Y;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static house.x1337.app.smb3.enumeration.TileType.RENDERING_STARTER;
import static house.x1337.app.smb3.enumeration.TileType.SPAWN_POINT;
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
public class LevelSceneEditorTab extends BaseLevelSceneTab {
    private List<LevelSceneLayer> layers;
    private LevelSceneLayer activeLayer;
    private int rows;
    private int columns;
    /** Where each virtual marker sits; {@code null} while it is not placed. */
    private Coordinates renderingStarter = null;
    private Coordinates spawnPoint = null;
    private Coordinates levelCompletionCard = null;
    private String sceneTitle = null;
    private String sceneDescription = null;
    /** The music the scene plays; {@code null} for none. */
    private MusicTrack backgroundMusic = null;
    private String sceneId = null;

    /**
     * The scene's pipe portals. They are not tiles: they are drawn over the NPC layer and copied to and from
     * {@code LevelScene.pipePortals} by {@code LevelSceneEditorTabFactory}.
     */
    private List<PipePortal> pipePortals = new ArrayList<>();

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
    private final PaletteSelectionService paletteSelectionService;
    private final EnemyLevelSceneGridStamper enemyLevelSceneGridStamper;

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

        updateStatus(null);
    }

    public void newScene(final int newRows, final int newCols) {
        rows = newRows;
        columns = newCols;
        initLayers();
        resetData();
        levelSceneEditorGrid.setPreferredSize(new Dimension(columns * TILE_SIZE, rows * TILE_SIZE));
        levelSceneEditorGrid.revalidate();
        levelSceneEditorGrid.repaint();
        updateStatus(null);
    }

    public void clearScene() {
        for (final LevelSceneLayer layer : layers) {
            for (final Tile[] row : layer.getTiles()) {
                Arrays.fill(row, NULL_TILE);
            }
        }
        resetData();
        levelSceneEditorGrid.repaint();
        updateStatus(null);
    }

    private void resetData() {
        renderingStarter = null;
        spawnPoint = null;
        levelCompletionCard = null;
        sceneTitle = null;
        sceneDescription = null;
        backgroundMusic = null;
        sceneId = null;
        pipePortals = new ArrayList<>();
    }

    public boolean hasRenderingStarter() {
        return renderingStarter != null;
    }

    public boolean hasSpawnPoint() {
        return spawnPoint != null;
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
                .tiles(emptyTiles(rows, columns))
                .build())
            .toList());
        activeLayer = layers.stream()
            .filter(layer -> layer.getType() == AIR)
            .findFirst()
            .orElseThrow();
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

    public void paintTile(final Coordinates cell) {
        if (isOutsideTheScene(cell)) {
            return;
        }
        final VirtualObjectType selectedVirtualObject = paletteSelectionService.getSelectedVirtualObject();
        if (selectedVirtualObject != null) {
            switch (selectedVirtualObject.getCategory()) {
                case PIPE_PORTAL -> placePipePortal(selectedVirtualObject, cell);
                case CARD -> placeLevelCompletionCard(cell);
            }
            return;
        }
        final EnemyStamp selectedEnemy = paletteSelectionService.getSelectedEnemy();
        if (selectedEnemy != null) {
            stampEnemy(selectedEnemy, cell);
            return;
        }
        final Tile selectedTile = paletteSelectionService.getSelectedTile();
        if (selectedTile == null) {
            return;
        }
        if (selectedTile.isOfType(RENDERING_STARTER)) {
            // Virtual tile - do NOT modify the tile grid; just record the position.
            renderingStarter = cell;
            levelSceneEditorGrid.repaint();
            return;
        }
        if (selectedTile.isOfType(SPAWN_POINT)) {
            // Virtual tile - do NOT modify the tile grid; just record the position.
            spawnPoint = cell;
            levelSceneEditorGrid.repaint();
            return;
        }
        activeLayer.getTiles()[cell.y()][cell.x()] = selectedTile;
        levelSceneEditorGrid.repaint();
    }

    /**
     * Clears a cell of the active layer. On the NPC layer, a cell holding the level completion card or covered by a
     * pipe portal removes that object instead and leaves the layer's tiles alone. The card is drawn over the portals,
     * so on a cell holding both the card goes first.
     */
    public void eraseTile(final Coordinates cell) {
        if (!isOutsideTheScene(cell)) {
            if (removeLevelCompletionCardAt(cell) || removePipePortalAt(cell)) {
                levelSceneEditorGrid.repaint();
                return;
            }
            activeLayer.getTiles()[cell.y()][cell.x()] = NULL_TILE;
            levelSceneEditorGrid.repaint();
        }
    }

    private boolean isOutsideTheScene(final Coordinates cell) {
        return cell.y() < 0 || cell.y() >= rows || cell.x() < 0 || cell.x() >= columns;
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
            new LevelSceneDimensions(columns, rows),
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
     * Stamps a whole enemy onto the NPC layer, the clicked cell taking the enemy's rendering starter (see
     * {@link EnemyLevelSceneGridStamper}). Refuses to write anywhere but the NPC layer — the palette only arms
     * an enemy once that layer is active (switching to it on the click if need be) and releases it when the
     * layer moves away, and this keeps that true even if an enemy is armed by some other path.
     */
    private void stampEnemy(final EnemyStamp enemy, final Coordinates cell) {
        if (activeLayer == null || activeLayer.getType() != NON_PLAYABLE_CHARACTERS) {
            return;
        }
        enemyLevelSceneGridStamper.stamp(activeLayer.getTiles(), enemy, cell);
        levelSceneEditorGrid.repaint();
    }

    /**
     * @param cell the cell under the mouse, or {@code null} when there is none to report
     */
    public void updateStatus(final Coordinates cell) {
        final String pos = (cell != null && cell.x() >= 0 && cell.y() >= 0)
            ? "  |  col=" + cell.x() + "  row=" + cell.y()
            : "";
        final VirtualObjectType selectedVirtualObject = paletteSelectionService.getSelectedVirtualObject();
        if (selectedVirtualObject != null) {
            statusLabel.setText(
                selectedVirtualObject.getLabel()
                    + "  |  " + selectedVirtualObject.getColumns() + " × " + selectedVirtualObject.getRows() + " tiles"
                    + "  |  click places its lower-left tile" + pos
            );
            return;
        }
        final EnemyStamp selectedEnemy = paletteSelectionService.getSelectedEnemy();
        if (selectedEnemy != null) {
            statusLabel.setText(
                "Enemy: " + selectedEnemy.getEnemyType().getLabel()
                    + "  |  " + selectedEnemy.getColumns() + " × " + selectedEnemy.getRows() + " tiles"
                    + "  |  click places its rendering starter" + pos
            );
            return;
        }
        final Tile selectedTile = paletteSelectionService.getSelectedTile();
        if (selectedTile == null) {
            statusLabel.setText("No tile selected — pick one from the Tiles palette");
            return;
        }
        if (selectedTile.isOfType(RENDERING_STARTER)) {
            final String rs = hasRenderingStarter()
                ? "  |  set at col=" + renderingStarter.x() + " row=" + renderingStarter.y()
                : "  |  not placed yet";
            statusLabel.setText("Rendering Starter (virtual)" + rs);
            return;
        }
        if (selectedTile.isOfType(SPAWN_POINT)) {
            final String sp = hasSpawnPoint()
                ? "  |  set at col=" + spawnPoint.x() + " row=" + spawnPoint.y()
                : "  |  not placed yet";
            statusLabel.setText("Spawn Point (virtual)" + sp);
            return;
        }
        final TileType type = selectedTile.getType();
        final String tileName = type != null ? type.getLabel() : "(none)";
        statusLabel.setText("Tile: " + tileName + "  |  " + rows + " rows × " + columns + " columns" + pos);
    }

    /** Whether a grid click has anything to place — a tile, a whole enemy, or a virtual object. */
    public boolean hasPlaceableSelection() {
        return paletteSelectionService.hasSelection();
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
