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
import static org.mockito.Mockito.when;

class TileServiceUnresolvedTilesTest {
    @Test
    @DisplayName("The unresolved tiles are every stored tile without a type, by id")
    void unresolvedTilesAreTheUntypedOnes() {
        // Prepare
        final TileRepository repository = mock(TileRepository.class);
        when(repository.findAll()).thenReturn(List.of(
            TileRecord.builder().id(1003).build(),
            TileRecord.builder().id(1001).type(SOLID).build(),
            TileRecord.builder().id(1002).build()
        ));
        final TileService tileService = new TileService(repository);
        tileService.initCache();

        // Execute
        final List<Tile> unresolved = tileService.getUnresolvedTiles();

        // Verify
        assertThat(unresolved).extracting(Tile::getId).containsExactly(1002, 1003);
    }
}
