package house.x1337.app.smb3.util.factory;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.MusicTrack;
import house.x1337.app.smb3.game.camera.LevelSceneVibration;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.model.repository.LevelSceneRecord;
import house.x1337.app.smb3.model.service.TileImportResult;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyLevelSceneGridStamper;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorGrid;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import house.x1337.app.smb3.ui.service.PaletteSelectionService;
import house.x1337.app.smb3.util.converter.LevelSceneConverter;
import house.x1337.app.smb3.util.provider.TilesProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A scene's background music travels from the editor tab to the {@link LevelScene}, into the stored
 * {@link LevelSceneRecord}, back out to a scene and into a new tab, the way a save and a reload take it.
 */
@TestInstance(PER_CLASS)
class BackgroundMusicStorageTest {
    private static final int ROWS = 6;
    private static final int COLUMNS = 8;

    private final TilesProvider tilesProvider = id -> Optional.empty();
    private final LevelSceneConverter converter = () -> tilesProvider;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(LevelSceneVibration.class))
            .thenReturn(mock(LevelSceneVibration.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(LevelScene.class))
            .thenAnswer(invocation -> new LevelScene());
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    Stream<MusicTrack> loopingTracks() {
        return Arrays.stream(MusicTrack.values()).filter(MusicTrack::isLooping);
    }

    @ParameterizedTest
    @MethodSource("loopingTracks")
    @DisplayName("A chosen track survives the scene, the normalized record and the reload into a new tab")
    void aChosenTrackSurvivesSaveAndReload(final MusicTrack track) {
        // Prepare
        final LevelSceneEditorTab source = tab(track);

        // Execute
        final LevelScene saved = LevelSceneEditorTabFactory.fromLevelSceneEditor(source);
        final LevelSceneRecord record = converter.toLevelSceneRecord(saved);
        final LevelScene loaded = converter.toLevelScene(record);
        final LevelSceneEditorTab target = newTab();
        LevelSceneEditorTabFactory.fromScene(loaded);

        // Verify
        assertThat(saved.getBackgroundMusic()).isEqualTo(track);
        assertThat(record.getBackgroundMusic()).as("Kept by the normalizer the save runs").isEqualTo(track);
        assertThat(loaded.getBackgroundMusic()).isEqualTo(track);
        verify(target).setBackgroundMusic(track);
    }

    @Test
    @DisplayName("No music stays null all the way through")
    void noMusicStaysNull() {
        // Prepare
        final LevelSceneEditorTab source = tab(null);

        // Execute
        final LevelScene saved = LevelSceneEditorTabFactory.fromLevelSceneEditor(source);
        final LevelSceneRecord record = converter.toLevelSceneRecord(saved);
        final LevelScene loaded = converter.toLevelScene(record);
        final LevelSceneEditorTab target = newTab();
        LevelSceneEditorTabFactory.fromScene(loaded);

        // Verify
        assertThat(saved.getBackgroundMusic()).isNull();
        assertThat(record.getBackgroundMusic()).isNull();
        assertThat(loaded.getBackgroundMusic()).isNull();
        verify(target).setBackgroundMusic(null);
    }

    @Test
    @DisplayName("Normalizing a record keeps its background music")
    void theNormalizerKeepsTheMusic() {
        // Prepare
        final LevelSceneRecord record = LevelSceneRecord.builder()
            .id("scene-1")
            .rows(ROWS)
            .columns(COLUMNS)
            .backgroundMusic(MusicTrack.ATHLETIC)
            .build();

        // Execute
        final LevelSceneRecord normalized = converter.normalize(record);

        // Verify
        assertThat(normalized.getBackgroundMusic()).isEqualTo(MusicTrack.ATHLETIC);
    }

    @Test
    @DisplayName("A record stored before the field existed opens with no music")
    void aRecordWithoutTheFieldHasNoMusic() {
        // Prepare
        final LevelSceneRecord legacy = LevelSceneRecord.builder().id("scene-1").rows(ROWS).columns(COLUMNS).build();

        // Execute
        final LevelScene loaded = converter.toLevelScene(converter.normalize(legacy));
        final LevelSceneEditorTab target = newTab();
        LevelSceneEditorTabFactory.fromScene(loaded);

        // Verify
        assertThat(loaded.getBackgroundMusic()).isNull();
        verify(target).setBackgroundMusic(null);
    }

    @Test
    @DisplayName("An imported scene starts with no music")
    void anImportStartsWithNoMusic() {
        // Prepare
        final LevelSceneEditorTab target = newTab();

        // Execute
        LevelSceneEditorTabFactory.fromImport(new TileImportResult(new Tile[ROWS][COLUMNS], ROWS, COLUMNS, List.of()));

        // Verify
        verify(target).setBackgroundMusic(null);
    }

    private LevelSceneEditorTab tab(final MusicTrack track) {
        final LevelSceneEditorTab tab = mock(LevelSceneEditorTab.class);
        when(tab.getLayers()).thenReturn(List.of());
        when(tab.getSceneId()).thenReturn("scene-1");
        when(tab.getRows()).thenReturn(ROWS);
        when(tab.getColumns()).thenReturn(COLUMNS);
        when(tab.getPipePortals()).thenReturn(List.of());
        when(tab.getBackgroundMusic()).thenReturn(track);
        return tab;
    }

    /** Has the factory's next new tab be a mock, so what it is given can be verified. */
    private LevelSceneEditorTab newTab() {
        final LevelSceneEditorTab tab = mock(LevelSceneEditorTab.class);
        when(tab.getLevelSceneEditorGrid()).thenReturn(mock(LevelSceneEditorGrid.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(LevelSceneImportRouter.class))
            .thenReturn(mock(LevelSceneImportRouter.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(TilePalettePanel.class))
            .thenReturn(mock(TilePalettePanel.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(PaletteSelectionService.class))
            .thenReturn(mock(PaletteSelectionService.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(EnemyLevelSceneGridStamper.class))
            .thenReturn(mock(EnemyLevelSceneGridStamper.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(LevelSceneEditorTab.class), any(), any(), any(), any()))
            .thenReturn(tab);
        return tab;
    }
}
