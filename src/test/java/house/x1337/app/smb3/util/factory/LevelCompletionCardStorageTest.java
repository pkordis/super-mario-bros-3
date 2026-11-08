package house.x1337.app.smb3.util.factory;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.camera.LevelSceneVibration;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.repository.LevelSceneRecord;
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
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The level completion card's cell travels from the editor tab to the {@link LevelScene}, into the stored
 * {@link LevelSceneRecord}, back out to a scene and into a new tab, the way a save and a reload take it.
 */
class LevelCompletionCardStorageTest {
    private static final int ROWS = 6;
    private static final int COLUMNS = 8;

    private final TilesProvider tilesProvider = id -> Optional.empty();
    private final LevelSceneConverter converter = () -> tilesProvider;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        // LevelScene resolves its vibration collaborator in a field initialiser.
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

    @Test
    @DisplayName("A placed card keeps its cell through the scene, the record and back to a new tab")
    void placedCardSurvivesSaveAndReload() {
        // Prepare
        final LevelSceneEditorTab source = tab(of(7, 4));

        // Execute
        final LevelScene saved = LevelSceneEditorTabFactory.fromLevelSceneEditor(source);
        final LevelSceneRecord record = converter.normalize(converter.toLevelSceneRecord(saved));
        final LevelScene loaded = converter.toLevelScene(record);
        final LevelSceneEditorTab target = newTab();
        LevelSceneEditorTabFactory.fromScene(loaded);

        // Verify
        assertThat(saved.getLevelCompletionCard()).isEqualTo(of(7, 4));
        assertThat(record.getLevelCompletionCard()).isEqualTo(of(7, 4));
        assertThat(loaded.getLevelCompletionCard()).isEqualTo(of(7, 4));
        verify(target).setLevelCompletionCard(of(7, 4));
    }

    @Test
    @DisplayName("A scene without a card stores null and opens with no card in the tab")
    void noCardStaysNull() {
        // Prepare
        final LevelSceneEditorTab source = tab(null);

        // Execute
        final LevelScene saved = LevelSceneEditorTabFactory.fromLevelSceneEditor(source);
        final LevelSceneRecord record = converter.normalize(converter.toLevelSceneRecord(saved));
        final LevelScene loaded = converter.toLevelScene(record);
        final LevelSceneEditorTab target = newTab();
        LevelSceneEditorTabFactory.fromScene(loaded);

        // Verify
        assertThat(saved.getLevelCompletionCard()).as("The scene's own 'none'").isNull();
        assertThat(record.getLevelCompletionCard()).isNull();
        assertThat(loaded.getLevelCompletionCard()).isNull();
        verify(target).setLevelCompletionCard(null);
    }

    @Test
    @DisplayName("A record stored before the card existed opens with no card")
    void recordWithoutTheFieldsHasNoCard() {
        // Prepare
        final LevelSceneRecord legacy = LevelSceneRecord.builder().id("scene-1").rows(ROWS).columns(COLUMNS).build();

        // Execute
        final LevelScene loaded = converter.toLevelScene(converter.normalize(legacy));
        final LevelSceneEditorTab target = newTab();
        LevelSceneEditorTabFactory.fromScene(loaded);

        // Verify
        assertThat(loaded.getLevelCompletionCard()).isNull();
        verify(target).setLevelCompletionCard(null);
    }

    private static LevelSceneEditorTab tab(final Coordinates card) {
        final LevelSceneEditorTab tab = mock(LevelSceneEditorTab.class);
        when(tab.getLayers()).thenReturn(List.of());
        when(tab.getSceneId()).thenReturn("scene-1");
        when(tab.getRows()).thenReturn(ROWS);
        when(tab.getColumns()).thenReturn(COLUMNS);
        when(tab.getPipePortals()).thenReturn(List.of());
        when(tab.getLevelCompletionCard()).thenReturn(card);
        when(tab.hasLevelCompletionCard()).thenReturn(card != null);
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
