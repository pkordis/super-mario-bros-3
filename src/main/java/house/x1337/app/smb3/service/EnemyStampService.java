package house.x1337.app.smb3.service;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.repository.EnemyRepository;
import house.x1337.app.smb3.util.converter.EnemyConverter;
import house.x1337.app.smb3.util.provider.TilesProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
            "Upserted enemy (id={}, type={}, {}x{} tiles, rendering starter at row={}, column={}).",
            enemy.getId(),
            enemy.getEnemyType(),
            enemy.getColumns(),
            enemy.getRows(),
            enemy.getRenderingStarterRow(),
            enemy.getRenderingStarterColumn()
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
