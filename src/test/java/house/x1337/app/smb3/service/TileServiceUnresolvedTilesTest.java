package house.x1337.app.smb3.service;

import house.x1337.app.smb3.model.repository.TileRecord;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.repository.TileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static house.x1337.app.smb3.enumeration.TileType.SOLID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TileServiceUnresolvedTilesTest {
    private final TileRepository repository = mock(TileRepository.class);
    private final TileService tileService = new TileService(repository);

    @Test
    @DisplayName("The unresolved tiles are every stored tile without a type, by id")
    void unresolvedTilesAreTheUntypedOnes() {
        // Prepare
        when(repository.findAll()).thenReturn(List.of(
            TileRecord.builder().id(1003).build(),
            TileRecord.builder().id(1001).type(SOLID).build(),
            TileRecord.builder().id(1002).build()
        ));
        tileService.initCache();

        // Execute
        final List<Tile> unresolved = tileService.getUnresolvedTiles();

        // Verify
        assertThat(unresolved).extracting(Tile::getId).containsExactly(1002, 1003);
    }

    @Test
    @DisplayName("A tile linked to a level object is resolved")
    void aLinkedTileIsResolved() {
        // Prepare
        when(repository.findAll()).thenReturn(List.of(
            TileRecord.builder().id(1001).levelObjectId(1500).build(),
            TileRecord.builder().id(1002).build()
        ));
        tileService.initCache();

        // Execute
        final List<Tile> unresolved = tileService.getUnresolvedTiles();

        // Verify
        assertThat(unresolved).extracting(Tile::getId).containsExactly(1002);
    }

    @Test
    @DisplayName("The tiles linked to a level object are found by its id")
    void theLinkedTilesAreFound() {
        // Prepare
        when(repository.findAll()).thenReturn(List.of(
            TileRecord.builder().id(1003).type(SOLID).levelObjectId(1500).build(),
            TileRecord.builder().id(1001).type(SOLID).levelObjectId(1500).build(),
            TileRecord.builder().id(1002).type(SOLID).levelObjectId(1600).build(),
            TileRecord.builder().id(1500).type(SOLID).build()
        ));
        tileService.initCache();

        // Execute
        final List<Tile> linked = tileService.getTilesLinkedTo(1500);

        // Verify
        assertThat(linked).extracting(Tile::getId).containsExactly(1001, 1003);
    }

    @Test
    @DisplayName("Updating a tile stores its link with the rest of its metadata")
    void updatingATileStoresItsLink() {
        // Prepare
        final int[] pixels = {1, 2, 3};
        final Tile tile = Tile.builder().id(1001).type(SOLID).description("Ground").argbData(pixels).levelObjectId(1500)
            .build();

        // Execute
        tileService.updateTile(tile);

        // Verify
        verify(repository).updateMetadata(1001, SOLID, "Ground", pixels, 1500);
    }

    @Test
    @DisplayName("A tile keeps its link through the store")
    void theLinkSurvivesTheRecord() {
        // Prepare
        final Tile tile = Tile.builder().id(1001).levelObjectId(1500).build();

        // Execute
        final Tile reloaded = tile.toRecord().toTile();

        // Verify
        assertThat(reloaded.getLevelObjectId()).isEqualTo(1500);
    }
}
