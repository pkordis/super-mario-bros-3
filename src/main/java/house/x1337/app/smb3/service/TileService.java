package house.x1337.app.smb3.service;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.repository.TileRecord;
import house.x1337.app.smb3.model.service.TileImportResult;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.repository.TileRepository;
import house.x1337.app.smb3.util.provider.TilesProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.TileType.Category.VIRTUAL;
import static java.util.Comparator.comparing;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class TileService implements TilesProvider {
    private static final int TILE_ID_POOL_START = 1000;

    private final Map<Integer, Tile> tileCache = new HashMap<>();
    private final Map<Integer, Tile> virtualTileCache = new HashMap<>();
    private final Map<String, Tile> tilesBySha256 = new HashMap<>();
    private final TileRepository tileRepository;

    private int nextTileId = TILE_ID_POOL_START;

    @PostConstruct
    public void initCache() {
        tileRepository
            .findAll()
            .forEach(record -> {
                final Tile tile = record.toTile();
                if (tile.isVirtual()) {
                    virtualTileCache.put(tile.getId(), tile);
                } else {
                    tileCache.put(tile.getId(), tile);
                    if (tile.getSha256() != null) {
                        tilesBySha256.put(tile.getSha256(), tile);
                    }
                    nextTileId = Math.max(nextTileId, tile.getId() + 1);
                }
            });
        log.info("Tile cache initialised with {} entries.", tileCache.size());
        ensureVirtualTilesExist();
    }

    private void ensureVirtualTilesExist() {
        for (final TileType type : TileType.getByCategory(VIRTUAL)) {
            if (type == TileType.NULL) {
                continue;
            }
            final int id = type.ordinal();
            if (!virtualTileCache.containsKey(id)) {
                final TileRecord record = TileRecord.builder()
                    .id(id)
                    .type(type)
                    .build();
                tileRepository.insert(record);
                virtualTileCache.put(id, record.toTile());
                log.info("Created missing virtual tile: {}", type.name());
            }
        }
    }

    public List<Tile> getVirtualTiles() {
        return virtualTileCache
            .values()
            .stream()
            .sorted(comparing(Tile::getId))
            .toList();
    }

    public TileImportResult importFromImage(final BufferedImage image) {
        final int s = TILE_SPRITE_SIZE;
        final int w = image.getWidth();
        final int h = image.getHeight();

        if (w % s != 0 || h % s != 0) {
            throw new IllegalArgumentException(
                "Image dimensions (" + w + "\u2014" + h + ") are not multiples of " + s + "\u2014" + s + ".\n" +
                    "Each tile must be exactly " + s + "\u2014" + s + " pixels."
            );
        }

        final int cols = w / s;
        final int rows = h / s;
        final Tile[][] grid = new Tile[rows][cols];
        final List<Tile> newTiles = new ArrayList<>();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                final int[] pixels = image.getRGB(col * s, row * s, s, s, null, 0, s);
                final String sha256 = calculateSha256(pixels);

                final Tile existing = tilesBySha256.get(sha256);
                if (existing != null) {
                    grid[row][col] = existing;
                } else {
                    final Tile tile = fromPixels(sha256, pixels);
                    tileRepository.insert(tile.toRecord());
                    tileCache.put(tile.getId(), tile);
                    tilesBySha256.put(sha256, tile);
                    newTiles.add(tile);
                    grid[row][col] = tile;
                    log.debug("Inserted new tile: id={}, sha256={}", tile.getId(), sha256);
                }
            }
        }

        log.info("Import complete: {}x{} tiles ({} new).", cols, rows, newTiles.size());
        return new TileImportResult(grid, rows, cols, Collections.unmodifiableList(newTiles));
    }

    public Optional<Tile> findById(final int id) {
        return Optional.ofNullable(tileCache.get(id));
    }

    public List<Tile> getClassifiedTiles() {
        return tileCache
            .values()
            .stream()
            .filter(t -> t.getType() != null && !t.isVirtual())
            .sorted(comparing(Tile::getType).thenComparing(Tile::getId))
            .toList();
    }

    public List<Tile> getInteractiveSingleTiles() {
        return tileCache
            .values()
            .stream()
            .filter(t -> t.getType() == TileType.OBJECT_INTERACTIVE_SINGLE)
            .sorted(comparing(Tile::getId))
            .toList();
    }

    public void updateTile(final Tile tile) {
        tileRepository.updateMetadata(tile.getId(), tile.getType(), tile.getDescription(), tile.getArgbData());
        tileCache.put(tile.getId(), tile);
        if (tile.getSha256() != null) {
            tilesBySha256.put(tile.getSha256(), tile);
        }
        log.debug(
            "Updated tile metadata (type={}, description={}): {}",
            tile.getType(),
            tile.getDescription(),
            tile.getId()
        );
    }

    /**
     * Creates a new custom tile record with the given properties.
     *
     * @param type             the tile type
     * @param description      optional description
     * @param originalArgbData the original ARGB pixel data (as imported from PNG)
     * @param editedArgbData   the edited ARGB pixel data (after user modifications)
     * @return the created TileRecord
     */
    public TileRecord createCustomTile(
        final TileType type,
        final String description,
        final int[] originalArgbData,
        final int[] editedArgbData
    ) {
        final String sha256 = calculateSha256(originalArgbData);

        final TileRecord record = TileRecord.builder()
            .id(nextTileId++)
            .sha256(sha256)
            .type(type)
            .description(description)
            .originalArgbData(originalArgbData)
            .argbData(editedArgbData)
            .build();

        tileRepository.insert(record);

        final Tile tile = record.toTile();
        tileCache.put(tile.getId(), tile);
        tilesBySha256.put(sha256, tile);

        log.info("Created custom tile: id={}, type={}, sha256={}", record.getId(), type, sha256);

        return record;
    }

    private Tile fromPixels(final String sha256, final int[] argbPixels) {
        return Tile.builder()
            .id(nextTileId++)
            .sha256(sha256)
            .originalArgbData(argbPixels)
            .build();
    }

    private String calculateSha256(final int[] argbPixels) {
        final MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available.", exception);
        }

        final ByteBuffer byteBuffer = ByteBuffer.allocate(argbPixels.length * Integer.BYTES);
        for (final int pixel : argbPixels) {
            byteBuffer.putInt(pixel);
        }

        return HexFormat.of().formatHex(messageDigest.digest(byteBuffer.array()));
    }
}
