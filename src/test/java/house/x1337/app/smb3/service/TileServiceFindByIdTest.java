package house.x1337.app.smb3.service;

import house.x1337.app.smb3.model.repository.TileRecord;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.repository.TileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.enumeration.TileType.SOLID;
import static house.x1337.app.smb3.enumeration.TileType.SPAWN_POINT;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Loading a scene resolves every cell of every layer through {@link TileService#findById}, so a lookup that misses the
 * cache must not go to the database each time: most cells are empty, and with a remote database each miss is a network
 * round trip.
 */
class TileServiceFindByIdTest {
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
    @DisplayName("Empty cells resolve to nothing without going to the database")
    void emptyCellsNeverHitTheDatabase() {
        // Execute
        for (int cell = 0; cell < 1000; cell++) {
            assertThat(tileService.findById(NULL_TILE.getId())).isEmpty();
        }

        // Verify
        verify(tileRepository, never()).findById(anyInt());
    }

    @Test
    @DisplayName("A virtual tile resolves to nothing without going to the database")
    void virtualTilesNeverHitTheDatabase() {
        // Execute
        final Optional<Tile> spawnPoint = tileService.findById(SPAWN_POINT.ordinal());

        // Verify
        assertThat(spawnPoint).isEmpty();
        verify(tileRepository, never()).findById(anyInt());
    }

    @Test
    @DisplayName("An id with no record is looked up once, then remembered")
    void unknownIdsAreLookedUpOnce() {
        // Prepare
        when(tileRepository.findById(4242)).thenReturn(Optional.empty());

        // Execute
        tileService.findById(4242);
        final Optional<Tile> second = tileService.findById(4242);

        // Verify
        assertThat(second).isEmpty();
        verify(tileRepository, times(1)).findById(4242);
    }

    @Test
    @DisplayName("A tile found in the database is cached")
    void foundTilesAreCached() {
        // Prepare
        when(tileRepository.findById(4243)).thenReturn(Optional.of(TileRecord.builder().id(4243).type(SOLID).build()));

        // Execute
        final Optional<Tile> first = tileService.findById(4243);
        final Optional<Tile> second = tileService.findById(4243);

        // Verify
        assertThat(first).isPresent();
        assertThat(second.orElseThrow()).isSameAs(first.orElseThrow());
        verify(tileRepository, times(1)).findById(4243);
    }

    @Test
    @DisplayName("An id remembered as missing resolves once a tile is created with it")
    void aRememberedMissDoesNotHideANewTile() {
        // Prepare - the empty store hands out ids from 1000, so the next tile created is 1000
        when(tileRepository.findById(1000)).thenReturn(Optional.empty());
        assertThat(tileService.findById(1000)).isEmpty();
        final int[] pixels = new int[256];

        // Execute
        final int created = tileService.createCustomTile(SOLID, "made after the miss", pixels, pixels).getId();

        // Verify
        assertThat(created).isEqualTo(1000);
        assertThat(tileService.findById(1000)).isPresent();
    }
}
