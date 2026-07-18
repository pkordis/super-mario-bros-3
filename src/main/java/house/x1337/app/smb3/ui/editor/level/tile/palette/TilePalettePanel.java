package house.x1337.app.smb3.ui.editor.level.tile.palette;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.model.ui.tile.TileGroup;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.tile.palette.core.ComponentsBuilder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import static house.x1337.app.smb3.model.ui.tile.Tile.THUMB_SIZE;
import static house.x1337.app.smb3.ui.editor.level.tile.palette.TileButton.fromTile;
import static java.awt.BorderLayout.CENTER;

@Singleton
@RequiredArgsConstructor
public final class TilePalettePanel extends JPanel implements ComponentsBuilder {
    private final TileService tileService;
    private final JPanel tilesPanel = buildTilesPanel();
    private final JScrollPane scrollPane = buildScrollPane(tilesPanel);
    private final VirtualTilesSection virtualTilesSection;

    private final TreeMap<TileType, TileGroup> groups = new TreeMap<>();
    private final Set<Integer> addedTileIds = new HashSet<>();

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
        group.gridPanel().add(fromTile(tile));
        group.gridPanel().revalidate();
        group.gridPanel().repaint();
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
