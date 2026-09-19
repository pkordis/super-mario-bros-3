package house.x1337.app.smb3.service;

import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.repository.TileRecord;
import house.x1337.app.smb3.repository.TileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.awt.image.BufferedImage;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TileServiceEnemyPartsTest {
    private TileRepository tileRepository;
    private TileService tileService;

    @BeforeEach
    void prepare() {
        tileRepository = mock(TileRepository.class);
        when(tileRepository.findAll()).thenReturn(emptyList());
        tileService = new TileService(tileRepository);
        tileService.initCache();
    }

    @Test
    @DisplayName("An image is accepted only when both dimensions are multiples of the tile size")
    void tileGridMultiplesAreEnforced() {
        assertThatCode(() -> tileService.assertTileGridMultiples(imageOf(TILE_SPRITE_SIZE, TILE_SPRITE_SIZE)))
            .as("A single tile is the smallest valid enemy")
            .doesNotThrowAnyException();
        assertThatCode(() -> tileService.assertTileGridMultiples(imageOf(TILE_SPRITE_SIZE * 2, TILE_SPRITE_SIZE * 3)))
            .as("Any whole number of tiles in each direction")
            .doesNotThrowAnyException();

        assertThatThrownBy(() -> tileService.assertTileGridMultiples(imageOf(TILE_SPRITE_SIZE + 1, TILE_SPRITE_SIZE)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not multiples of");
        assertThatThrownBy(() -> tileService.assertTileGridMultiples(imageOf(TILE_SPRITE_SIZE, TILE_SPRITE_SIZE - 1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Enemy parts are stored as ENEMY_PART tiles, with identical parts sharing one tile")
    void identicalPartsShareOneTile() {
        // Prepare - two identical parts and one different, as a sprite sheet with a repeated cell would give
        final int[] filled = partFilledWith(0xFF00FF00);
        final int[] blank = partFilledWith(0x00000000);

        // Execute
        final int[] tileIds = tileService.createEnemyPartTiles(List.of(filled, blank, filled.clone()), "Goomba");

        // Verify
        assertThat(tileIds).hasSize(3);
        assertThat(tileIds[0]).as("The repeated part resolves to the same tile").isEqualTo(tileIds[2]);
        assertThat(tileIds[1]).as("A different part gets its own tile").isNotEqualTo(tileIds[0]);

        final ArgumentCaptor<TileRecord> inserted = ArgumentCaptor.forClass(TileRecord.class);
        verify(tileRepository, atLeastOnce()).insert(inserted.capture());
        final List<TileRecord> enemyParts = inserted
            .getAllValues()
            .stream()
            .filter(record -> record.getType() == TileType.ENEMY_PART)
            .toList();

        assertThat(enemyParts).as("Two distinct parts were persisted, not three").hasSize(2);
        assertThat(enemyParts)
            .as("A part is renderable straight away: its displayed pixels are set, not left to classification")
            .allSatisfy(record -> assertThat(record.getArgbData()).isNotNull());
    }

    @Test
    @DisplayName("A part matching an already classified tile reuses it without re-typing it")
    void alreadyClassifiedTilesAreNotRetyped() {
        // Prepare - the same pixels already exist as a solid tile
        final int[] pixels = partFilledWith(0xFF123456);
        final TileRecord existing = tileService.createCustomTile(TileType.SOLID, "ground", pixels, pixels);

        // Execute
        final int[] tileIds = tileService.createEnemyPartTiles(List.of(pixels.clone()), "Goomba");

        // Verify
        assertThat(tileIds).containsExactly(existing.getId());
        verify(tileRepository, atLeastOnce()).insert(any());
        assertThat(tileService.findById(existing.getId()).orElseThrow().getType())
            .as("Someone else's classification is left alone")
            .isEqualTo(TileType.SOLID);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private BufferedImage imageOf(final int width, final int height) {
        return new BufferedImage(width, height, TYPE_INT_ARGB);
    }

    private int[] partFilledWith(final int argb) {
        final int[] pixels = new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE];
        java.util.Arrays.fill(pixels, argb);
        return pixels;
    }
}
