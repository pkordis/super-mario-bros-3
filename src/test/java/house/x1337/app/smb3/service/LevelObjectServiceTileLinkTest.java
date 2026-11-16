package house.x1337.app.smb3.service;

import house.x1337.app.smb3.model.repository.LevelObjectRecord;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.repository.LevelObjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.GOOMBA;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.COIN_FLIPPING;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.QUESTION_BLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Finding a tile's level object when many tiles can be linked to one. */
class LevelObjectServiceTileLinkTest {
    private static final int QUESTION_BLOCK_ID = 1500;
    private static final int COIN_ID = 1600;
    private static final int GOOMBA_ID = 1700;
    private static final int UNTYPED_ID = 1800;

    private final LevelObjectRepository repository = mock(LevelObjectRepository.class);
    private final LevelObjectService levelObjectService = new LevelObjectService(repository);
    private final LevelObjectRecord questionBlock = record(QUESTION_BLOCK_ID, QUESTION_BLOCK.name());
    private final LevelObjectRecord coin = record(COIN_ID, COIN_FLIPPING.name());

    @BeforeEach
    void prepare() {
        when(repository.findAll()).thenReturn(List.of(
            questionBlock,
            coin,
            record(GOOMBA_ID, GOOMBA.name()),
            record(UNTYPED_ID, null)
        ));
        levelObjectService.initCache();
    }

    @Test
    @DisplayName("A linked tile stands for the level object it is linked to")
    void aLinkedTileFindsItsLevelObject() {
        // Execute & Verify
        assertThat(levelObjectService.findByTile(tile(2000, QUESTION_BLOCK_ID))).contains(questionBlock);
    }

    @Test
    @DisplayName("An unlinked tile stands for the level object with its own id")
    void anUnlinkedTileFindsItsOwnLevelObject() {
        // Execute & Verify
        assertThat(levelObjectService.findByTile(tile(COIN_ID, null))).contains(coin);
        assertThat(levelObjectService.findByTile(tile(2000, null))).isEmpty();
    }

    @Test
    @DisplayName("A link to a missing level object falls back to the tile's own one")
    void aDanglingLinkFallsBack() {
        // Execute & Verify
        assertThat(levelObjectService.findByTile(tile(COIN_ID, 9999))).contains(coin);
    }

    @Test
    @DisplayName("The records of many tiles are keyed by tile id, and tiles linked to one object share it")
    void manyTilesShareOneLevelObject() {
        // Execute
        final var records = levelObjectService.findAllByTiles(List.of(
            tile(2000, QUESTION_BLOCK_ID),
            tile(2001, QUESTION_BLOCK_ID),
            tile(2002, null)
        ));

        // Verify
        assertThat(records).containsOnly(entry(2000, questionBlock), entry(2001, questionBlock));
    }

    @Test
    @DisplayName("A tile linked to a typed level object is classified")
    void aLinkedTileIsClassified() {
        // Execute
        final var unclassified = levelObjectService.getUnclassifiedIds(List.of(
            tile(2000, COIN_ID),
            tile(2001, null),
            tile(UNTYPED_ID, null)
        ));

        // Verify
        assertThat(unclassified).containsExactlyInAnyOrder(2001, UNTYPED_ID);
    }

    @Test
    @DisplayName("Only typed single-tiled level objects are offered, by type label then id")
    void onlySingleTiledLevelObjectsAreOffered() {
        // Execute & Verify
        assertThat(levelObjectService.getSingleTiledLevelObjects())
            .extracting(LevelObjectRecord::getId)
            .containsExactly(COIN_ID, QUESTION_BLOCK_ID);
    }

    private LevelObjectRecord record(final int id, final String type) {
        return LevelObjectRecord.builder().id(id).type(type).build();
    }

    private Tile tile(final int id, final Integer levelObjectId) {
        return Tile.builder().id(id).levelObjectId(levelObjectId).build();
    }
}
