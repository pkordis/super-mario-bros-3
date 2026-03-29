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
import java.awt.GridLayout;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import static house.x1337.app.smb3.ui.editor.level.tile.palette.TileButton.fromTile;
import static java.awt.BorderLayout.CENTER;
import static javax.swing.BorderFactory.createEmptyBorder;

@Singleton
@RequiredArgsConstructor
public final class TilePalettePanel extends JPanel implements ComponentsBuilder {
    private final TileService tileService;
    private final JPanel tilesPanel = buildTilesPanel();
    private final JScrollPane scrollPane = buildScrollPane(tilesPanel);
    private final VirtualTilesSection virtualTilesSection;

    private final TreeMap<TileType, TileGroup> groups = new TreeMap<>();
    private final Set<Integer> addedTileIds = new HashSet<>();

    @PostConstruct
    void init() {
        setLayout(new BorderLayout());
        add(scrollPane, CENTER);
        setMinimumSize(new Dimension(180, 100));
        setPreferredSize(new Dimension(200, 400));

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
            final JPanel gridPanel = new JPanel(new GridLayout(0, 2, 4, 4));
            gridPanel.setAlignmentX(LEFT_ALIGNMENT);
            gridPanel.setBorder(createEmptyBorder(2, 0, 8, 0));
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
