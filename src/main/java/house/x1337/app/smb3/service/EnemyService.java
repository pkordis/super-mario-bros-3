package house.x1337.app.smb3.service;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.game.enemy.Enemy;
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
public class EnemyService implements EnemyConverter {
    private final Map<String, Enemy> cache = new HashMap<>();
    private final EnemyRepository enemyRepository;
    private final TileService tileService;

    @PostConstruct
    public void initCache() {
        enemyRepository.findAll().forEach(record -> {
            final Enemy enemy = toEnemy(record);
            cache.put(enemy.getId(), enemy);
        });
        log.info("Enemy cache initialised with {} entries.", cache.size());
    }

    public void upsert(final Enemy enemy) {
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

    public Optional<Enemy> findById(final String id) {
        return Optional.ofNullable(cache.get(id));
    }

    public List<Enemy> findAll() {
        return cache
            .values()
            .stream()
            .sorted(comparing(EnemyService::sortKeyOf))
            .toList();
    }

    private static String sortKeyOf(final Enemy enemy) {
        final String type = enemy.getEnemyType() != null ? enemy.getEnemyType().name() : "";
        final String id = enemy.getId() != null ? enemy.getId() : "";
        return type + id;
    }

    public Optional<Enemy> findByRenderingStarterTileId(final int tileId) {
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
            .filter(Enemy::isWellFormed)
            .anyMatch(enemy -> enemy.containsTile(tileId));
    }

    @Override
    public TilesProvider getTilesProvider() {
        return tileService;
    }
}
