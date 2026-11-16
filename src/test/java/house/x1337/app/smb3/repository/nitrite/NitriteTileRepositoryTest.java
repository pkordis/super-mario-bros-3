package house.x1337.app.smb3.repository.nitrite;

import house.x1337.app.smb3.config.db.NitriteJacksonMapper;
import house.x1337.app.smb3.model.repository.TileRecord;
import org.dizitart.no2.Nitrite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.enumeration.TileType.OBJECT_INTERACTIVE_SINGLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.dizitart.no2.common.module.NitriteModule.module;

/** Runs the Nitrite tile repository against an in-memory database with the production Jackson mapper. */
class NitriteTileRepositoryTest {
    private static final int TILE_ID = 1042;

    private Nitrite nitrite;
    private NitriteTileRepository repository;

    @BeforeEach
    void prepare() {
        nitrite = Nitrite.builder()
            .loadModule(module(new NitriteJacksonMapper()))
            .openOrCreate();
        repository = new NitriteTileRepository(nitrite.getRepository(TileRecord.class));
    }

    @AfterEach
    void tearDown() {
        nitrite.close();
    }

    @Test
    @DisplayName("A tile stored without a link reads back with none")
    void aTileWithoutALinkHasNone() {
        // Prepare
        repository.insert(TileRecord.builder().id(TILE_ID).sha256("abc").build());

        // Execute & Verify
        assertThat(repository.findById(TILE_ID)).get().extracting(TileRecord::getLevelObjectId).isNull();
    }

    @Test
    @DisplayName("Updating a tile's metadata stores its link, and a later update can clear it")
    void theLinkIsStoredAndCleared() {
        // Prepare
        final int[] pixels = {1, 2, 3};
        repository.insert(TileRecord.builder().id(TILE_ID).sha256("abc").build());

        // Execute
        repository.updateMetadata(TILE_ID, OBJECT_INTERACTIVE_SINGLE, "Blue brick", pixels, 1500);
        final TileRecord linked = repository.findById(TILE_ID).orElseThrow();
        repository.updateMetadata(TILE_ID, OBJECT_INTERACTIVE_SINGLE, "Blue brick", pixels, null);
        final TileRecord unlinked = repository.findById(TILE_ID).orElseThrow();

        // Verify
        assertThat(linked.getLevelObjectId()).isEqualTo(1500);
        assertThat(linked.getType()).isEqualTo(OBJECT_INTERACTIVE_SINGLE);
        assertThat(linked.getDescription()).isEqualTo("Blue brick");
        assertThat(unlinked.getLevelObjectId()).isNull();
    }
}
