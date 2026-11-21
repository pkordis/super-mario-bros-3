package house.x1337.app.smb3.service;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.game.MultiTileArea;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.enemy.EnemyPlacement;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.repository.EnemyRepository;
import house.x1337.app.smb3.util.converter.EnemyConverter;
import house.x1337.app.smb3.util.provider.TilesProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.util.Comparator.comparing;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class EnemyStampService implements EnemyConverter {
    private final Map<String, EnemyStamp> cache = new HashMap<>();
    private final EnemyRepository enemyRepository;
    private final TileService tileService;

    @PostConstruct
    public void initCache() {
        enemyRepository.findAll().forEach(record -> {
            final EnemyStamp enemy = toEnemy(record);
            cache.put(enemy.getId(), enemy);
        });
        log.info("Enemy cache initialised with {} entries.", cache.size());
    }

    public void upsert(final EnemyStamp enemy) {
        enemyRepository.upsert(toEnemyRecord(enemy));
        cache.put(enemy.getId(), enemy);
        log.info(
            "Upserted enemy (id={}, type={}, {}x{} tiles, rendering starter at {}).",
            enemy.getId(),
            enemy.getEnemyType(),
            enemy.getColumns(),
            enemy.getRows(),
            enemy.getRenderingStarter()
        );
    }

    public Optional<EnemyStamp> findById(final String id) {
        return Optional.ofNullable(cache.get(id));
    }

    public List<EnemyStamp> findAll() {
        return cache
            .values()
            .stream()
            .sorted(comparing(EnemyStampService::sortKeyOf))
            .toList();
    }

    private static String sortKeyOf(final EnemyStamp enemy) {
        final String type = enemy.getEnemyType() != null ? enemy.getEnemyType().name() : "";
        final String id = enemy.getId() != null ? enemy.getId() : "";
        return type + id;
    }

    public Optional<EnemyStamp> findByRenderingStarterTileId(final int tileId) {
        return cache
            .values()
            .stream()
            .filter(enemy -> enemy.isWellFormed() && enemy.renderingStarterTileId() == tileId)
            .findFirst();
    }

    /**
     * The enemy whose rendering starter part is on {@code rendererCell} of {@code layer}. When several stamps share
     * that part, the one whose parts are all still in place wins, then the one with the most parts in place.
     */
    public Optional<EnemyPlacement> findPlacementAt(final Tile[][] layer, final Coordinates rendererCell) {
        final Tile tile = tileAt(layer, rendererCell);
        if (tile == null || tile.isVirtual()) {
            return Optional.empty();
        }
        return best(
            placeableStamps()
                .filter(stamp -> stamp.renderingStarterTileId() == tile.getId())
                .map(stamp -> stamp.placedAt(rendererCell)),
            layer
        );
    }

    /**
     * The enemy one of whose parts is on {@code cell} of {@code layer}: a placement holding that part there whose
     * rendering starter part is in place too. Ties are settled as in {@link #findPlacementAt}.
     */
    public Optional<EnemyPlacement> findPlacementHolding(final Tile[][] layer, final Coordinates cell) {
        final Tile tile = tileAt(layer, cell);
        if (tile == null || tile.isVirtual()) {
            return Optional.empty();
        }
        return best(
            placeableStamps()
                .flatMap(stamp -> rendererCellsHolding(stamp, tile.getId(), cell).map(stamp::placedAt))
                .filter(placement -> isRenderingStarterInPlace(layer, placement)),
            layer
        );
    }

    /** Every enemy placed in {@code layer} whose grid, empty parts included, reaches into {@code area}. */
    public List<EnemyPlacement> findPlacementsOverlapping(final Tile[][] layer, final MultiTileArea area) {
        final Map<Coordinates, EnemyPlacement> byRendererCell = new LinkedHashMap<>();
        placeableStamps().forEach(stamp -> {
            final Coordinates renderingStarter = stamp.getRenderingStarter();
            final int firstRow = area.row() - stamp.getRows() + 1 + renderingStarter.y();
            final int firstColumn = area.column() - stamp.getColumns() + 1 + renderingStarter.x();
            for (int row = firstRow; row <= area.lastRow() + renderingStarter.y(); row++) {
                for (int column = firstColumn; column <= area.lastColumn() + renderingStarter.x(); column++) {
                    final EnemyPlacement placement = stamp.placedAt(of(column, row));
                    if (isRenderingStarterInPlace(layer, placement)) {
                        byRendererCell.merge(
                            placement.rendererCell(),
                            placement,
                            (kept, other) -> isBetter(other, kept, layer) ? other : kept
                        );
                    }
                }
            }
        });
        return List.copyOf(byRendererCell.values());
    }

    private Stream<EnemyStamp> placeableStamps() {
        return findAll()
            .stream()
            .filter(stamp -> stamp.isWellFormed()
                && stamp.renderingStarterTile() != null
                && !stamp.renderingStarterTile().isVirtual());
    }

    private Stream<Coordinates> rendererCellsHolding(
        final EnemyStamp stamp,
        final int tileId,
        final Coordinates cell
    ) {
        final Coordinates renderingStarter = stamp.getRenderingStarter();
        final Stream.Builder<Coordinates> rendererCells = Stream.builder();
        for (int partRow = 0; partRow < stamp.getRows(); partRow++) {
            for (int partColumn = 0; partColumn < stamp.getColumns(); partColumn++) {
                final Tile part = stamp.tileAt(of(partColumn, partRow));
                if (part != null && part.getId() == tileId) {
                    rendererCells.add(of(
                        cell.x() - partColumn + renderingStarter.x(),
                        cell.y() - partRow + renderingStarter.y()
                    ));
                }
            }
        }
        return rendererCells.build();
    }

    private boolean isRenderingStarterInPlace(
        final Tile[][] layer,
        final EnemyPlacement placement
    ) {
        final Tile tile = tileAt(layer, placement.rendererCell());
        return tile != null && tile.getId() == placement.stamp().renderingStarterTileId();
    }

    private Optional<EnemyPlacement> best(
        final Stream<EnemyPlacement> candidates,
        final Tile[][] layer
    ) {
        return candidates.reduce((kept, other) -> isBetter(other, kept, layer) ? other : kept);
    }

    private boolean isBetter(
        final EnemyPlacement candidate,
        final EnemyPlacement than,
        final Tile[][] layer
    ) {
        final boolean candidateWhole = candidate.isWhole(layer);
        if (candidateWhole != than.isWhole(layer)) {
            return candidateWhole;
        }
        return candidate.heldCells(layer).size() > than.heldCells(layer).size();
    }

    private Tile tileAt(
        final Tile[][] layer,
        final Coordinates cell
    ) {
        if (layer == null || cell.y() < 0 || cell.y() >= layer.length) {
            return null;
        }
        final Tile[] row = layer[cell.y()];
        return cell.x() < 0 || cell.x() >= row.length ? null : row[cell.x()];
    }

    public boolean isEnemyPartTile(final int tileId) {
        return cache
            .values()
            .stream()
            .filter(EnemyStamp::isWellFormed)
            .anyMatch(enemy -> enemy.containsTile(tileId));
    }

    @Override
    public TilesProvider getTilesProvider() {
        return tileService;
    }
}
