package house.x1337.app.smb3.ui.editor.level.tile.palette;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.model.ui.tile.TileGroup;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.menu.scene.level.ActiveLayerMenu;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTabSystem;
import house.x1337.app.smb3.ui.editor.level.tile.palette.core.ComponentsBuilder;
import house.x1337.app.smb3.ui.service.SelectedTileService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.model.ui.tile.Tile.THUMB_SIZE;
import static house.x1337.app.smb3.ui.editor.level.tile.palette.TileButton.fromTile;
import static java.awt.BorderLayout.CENTER;

/**
 * The Tiles palette, grouped by tile type.
 *
 * <p>Every tile type declares the layer it belongs on, and a tile may only be painted while that layer is
 * the active one — which is the safeguard against the mistake of, say, dropping brick blocks into the
 * static-environment layer where nothing can break them. Tiles for other layers are greyed rather than
 * disabled, because clicking one is how the user says "switch me to that layer": see
 * {@link #activateLayerOf}. Virtual tiles belong to no layer and are never gated.
 */
@Singleton
@RequiredArgsConstructor
public final class TilePalettePanel extends JPanel implements ComponentsBuilder {
    private final TileService tileService;
    private final LevelSceneEditorTabSystem tabSystem;
    private final SelectedTileService selectedTileService;
    private final JPanel tilesPanel = buildTilesPanel();
    private final JScrollPane scrollPane = buildScrollPane(tilesPanel);
    private final VirtualTilesSection virtualTilesSection;

    private final TreeMap<TileType, TileGroup> groups = new TreeMap<>();
    private final Set<Integer> addedTileIds = new HashSet<>();
    private final List<TileButton> buttons = new ArrayList<>();

    /** Minimum width to display 4 tile buttons per row (tile + border + gap). */
    private static final int TILES_PER_ROW = 4;
    private static final int TILE_BUTTON_SIZE = THUMB_SIZE + 2;
    private static final int GAP = 2;
    private static final int PADDING = 4;
    private static final int MIN_WIDTH =
        TILES_PER_ROW * TILE_BUTTON_SIZE + (TILES_PER_ROW - 1) * GAP + PADDING * 2 + 20;

    @PostConstruct
    void init() {
        setLayout(new BorderLayout());
        add(scrollPane, CENTER);
        setMinimumSize(new Dimension(MIN_WIDTH, 100));
        setPreferredSize(new Dimension(MIN_WIDTH, 400));

        // Revalidate tile grid panels when viewport resizes so WrapLayout
        // recalculates row wrapping and preferred height.
        scrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(final ComponentEvent e) {
                tilesPanel.revalidate();
            }
        });

        tilesPanel.add(virtualTilesSection);
        tileService.getClassifiedTiles().forEach(this::addTile);

        // The active layer moves with the tab, and a new tab starts on Air.
        tabSystem.addChangeListener(event -> syncEnabledState());
        syncEnabledState();
    }

    public void addTile(final Tile tile) {
        if (tile.getType() == null) {
            return;
        }
        if (addedTileIds.contains(tile.getId())) {
            return;
        }
        addedTileIds.add(tile.getId());

        final TileGroup group = getOrCreateGroup(tile.getType());
        final TileButton button = fromTile(tile);
        buttons.add(button);
        group.gridPanel().add(button);
        group.gridPanel().revalidate();
        group.gridPanel().repaint();
        syncEnabledState();
    }

    /**
     * Greys every tile whose layer is not the active one, and releases the armed tile if it has just
     * become unpaintable — otherwise a selection made on one layer would keep painting after switching.
     */
    public void syncEnabledState() {
        final LevelSceneLayerType activeLayer = activeLayerType();
        for (final TileButton button : buttons) {
            button.markOutOfActiveLayer(isOutOfLayer(button, activeLayer));
        }
        final TileButton selected = selectedTileService.getSelectedTileButton();
        if (selected != null && isOutOfLayer(selected, activeLayer)) {
            selectedTileService.clearSelection();
        }
        tilesPanel.revalidate();
        tilesPanel.repaint();
    }

    /**
     * Switches the active layer to the one the given tile belongs on, so that picking a tile from another
     * layer's group takes the user there rather than being rejected.
     *
     * <p>Routed through {@code ActiveLayerMenu}, which owns activation: it moves the tab's active layer,
     * re-selects the matching radio item and re-syncs both palettes. Resolved lazily to keep the palette
     * and the menu from depending on each other at construction.
     */
    public void activateLayerOf(final Tile tile) {
        final LevelSceneLayerType owningLayer = tile.getType() == null
            ? null
            : tile.getType().getLevelSceneLayerOwningType();
        if (owningLayer == null) {
            return;
        }
        getBean(ActiveLayerMenu.class).activate(owningLayer);
    }

    private boolean isOutOfLayer(final TileButton button, final LevelSceneLayerType activeLayer) {
        final TileType type = button.getTile().getType();
        return type != null && !type.isPaintableOn(activeLayer);
    }

    private LevelSceneLayerType activeLayerType() {
        final LevelSceneEditorTab tab = tabSystem.getActiveTab();
        if (tab == null) {
            return null;
        }
        final LevelSceneLayer activeLayer = tab.getActiveLayer();
        return activeLayer == null ? null : activeLayer.getType();
    }

    private TileGroup getOrCreateGroup(final TileType type) {
        final TileGroup group = groups.get(type);
        if (group == null) {
            final JLabel header = buildGroupHeader(type);
            final JPanel gridPanel = buildGridPanel();
            final TileGroup newGroup = new TileGroup(header, gridPanel);
            groups.put(type, newGroup);
            rebuildGroupLayout();
            return newGroup;
        }
        return group;
    }

    private void rebuildGroupLayout() {
        tilesPanel.removeAll();
        tilesPanel.add(virtualTilesSection);
        for (final TileGroup group : groups.values()) {
            tilesPanel.add(group.header());
            tilesPanel.add(group.gridPanel());
        }
        tilesPanel.revalidate();
        tilesPanel.repaint();
    }
}
