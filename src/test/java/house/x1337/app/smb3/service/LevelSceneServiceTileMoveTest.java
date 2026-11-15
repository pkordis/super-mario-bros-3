package house.x1337.app.smb3.service;

import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.model.repository.LevelSceneRecord;
import house.x1337.app.smb3.model.repository.LevelSceneRecord.LevelSceneLayerData;
import house.x1337.app.smb3.model.service.TileMoveSummary;
import house.x1337.app.smb3.repository.LevelSceneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A tile whose type now belongs to another layer moves there in every stored scene: each cell holding it elsewhere is
 * cleared, and the same cell of the new layer gets it.
 */
class LevelSceneServiceTileMoveTest {
    private static final int TILE = 1042;
    private static final int OTHER = 1043;
    private static final long UPDATED_AT = 123_456L;

    private final LevelSceneRepository repository = mock(LevelSceneRepository.class);
    private final LevelSceneService service = new LevelSceneService(
        repository,
        mock(TileService.class),
        mock(PipePortalService.class)
    );

    @BeforeEach
    void prepare() {
        // A 1 x 4 scene with the tile twice on the terrain, once over a brick-layer tile, and a scene without it
        final Map<String, String> titles = new LinkedHashMap<>();
        titles.put("with", "With the tile");
        titles.put("without", "Without the tile");
        when(repository.findTitlesById()).thenReturn(titles);
        when(repository.findById("with")).thenAnswer(invocation -> Optional.of(record(
            "with",
            layer(STATIC_ENVIRONMENT, TILE, 0, TILE, OTHER),
            layer(INTERACTIVE_OBJECTS, 0, 0, OTHER, 0)
        )));
        when(repository.findById("without")).thenAnswer(invocation -> Optional.of(record(
            "without",
            layer(STATIC_ENVIRONMENT, OTHER, OTHER, 0, 0)
        )));
    }

    @Test
    @DisplayName("Counting finds the scenes and cells a move changes, and saves nothing")
    void countingSavesNothing() {
        // Execute
        final TileMoveSummary summary = service.countTileCellsOutside(TILE, INTERACTIVE_OBJECTS);

        // Verify
        assertThat(summary).isEqualTo(new TileMoveSummary(1, 2));
        verify(repository, never()).upsert(any());
    }

    @Test
    @DisplayName("Moving clears the old cells, fills the same cells of the new layer and saves only that scene")
    void movingRewritesTheLayers() {
        // Execute
        final TileMoveSummary summary = service.moveTile(TILE, INTERACTIVE_OBJECTS);

        // Verify
        final ArgumentCaptor<LevelSceneRecord> saved = ArgumentCaptor.forClass(LevelSceneRecord.class);
        verify(repository, times(1)).upsert(saved.capture());
        final LevelSceneRecord record = saved.getValue();
        assertThat(summary).isEqualTo(new TileMoveSummary(1, 2));
        assertThat(record.getId()).isEqualTo("with");
        assertThat(tileIds(record, STATIC_ENVIRONMENT)).containsExactly(0, 0, 0, OTHER);
        assertThat(tileIds(record, INTERACTIVE_OBJECTS))
            .as("The tile overwrites the brick-layer tile it was drawn over")
            .containsExactly(TILE, 0, TILE, 0);
        assertThat(tileIds(record, AIR)).as("Untouched layers stay empty").containsExactly(0, 0, 0, 0);
        assertThat(record.getUpdatedAt()).as("The last edited time is kept").isEqualTo(UPDATED_AT);
    }

    @Test
    @DisplayName("Copies already in the target layer stay, the others move, across every scene")
    void copiesInTheTargetLayerStay() {
        // Execute
        final TileMoveSummary summary = service.moveTile(OTHER, INTERACTIVE_OBJECTS);

        // Verify
        assertThat(summary).as("The terrain copies of the other tile move, the brick-layer one stays")
            .isEqualTo(new TileMoveSummary(2, 3));
    }

    private LevelSceneRecord record(final String id, final LevelSceneLayerData... layers) {
        final LevelSceneRecord.LevelSceneRecordBuilder builder = LevelSceneRecord.builder()
            .id(id)
            .rows(1)
            .columns(4)
            .updatedAt(UPDATED_AT);
        for (final LevelSceneLayerData layer : layers) {
            builder.layer(layer.getType().name(), layer);
        }
        return builder.build();
    }

    private LevelSceneLayerData layer(final LevelSceneLayerType type, final int... tileIds) {
        return LevelSceneLayerData.builder().type(type).tileIds(tileIds).build();
    }

    private int[] tileIds(final LevelSceneRecord record, final LevelSceneLayerType type) {
        return record.getLayers().get(type.name()).getTileIds();
    }
}
