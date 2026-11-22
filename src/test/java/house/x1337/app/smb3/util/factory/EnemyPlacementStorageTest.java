package house.x1337.app.smb3.util.factory;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.camera.LevelSceneVibration;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.enemy.EnemyPlacement;
import house.x1337.app.smb3.model.repository.EnemyPlacementRecord;
import house.x1337.app.smb3.model.repository.LevelSceneRecord;
import house.x1337.app.smb3.model.repository.LevelSceneRecord.LevelSceneLayerData;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.service.PaletteSelectionService;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorGrid;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import house.x1337.app.smb3.util.converter.LevelSceneConverter;
import house.x1337.app.smb3.util.provider.TilesProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A scene's enemy placements travel with its NPC layer: from the editor tab to the {@link LevelScene}, into the
 * stored {@link LevelSceneRecord} the save normalizes, and back, to the pixel.
 */
class EnemyPlacementStorageTest {
    private static final int ROWS = 6;
    private static final int COLUMNS = 8;

    private final TilesProvider tilesProvider = id -> Optional.empty();
    private final LevelSceneConverter converter = () -> tilesProvider;
    private final List<EnemyPlacement> placements = List.of(
        new EnemyPlacement("goomba", 1695, 343),
        new EnemyPlacement("para-goomba", 912, 336)
    );

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

    @Test
    @DisplayName("Placements survive the scene, the normalized record and the reload, each to the pixel")
    void placementsSurviveSaveAndReload() {
        // Prepare
        final LevelSceneEditorTab source = mock(LevelSceneEditorTab.class);
        final LevelSceneLayer npc = layer(NON_PLAYABLE_CHARACTERS, new ArrayList<>(placements));
        when(source.getLayers()).thenReturn(List.of(layer(STATIC_ENVIRONMENT, new ArrayList<>()), npc));
        when(source.getLevelSceneDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

        // Execute
        final LevelScene saved = LevelSceneEditorTabFactory.fromLevelSceneEditor(source);
        final LevelSceneRecord record = converter.normalize(converter.toLevelSceneRecord(saved));
        final LevelScene loaded = converter.toLevelScene(record);

        // Verify
        assertThat(saved.getNonPlayableCharactersLayer().getEnemyPlacements())
            .as("A copy, not the tab's own list")
            .isNotSameAs(npc.getEnemyPlacements())
            .containsExactlyElementsOf(placements);
        assertThat(record.getLayers().get(NON_PLAYABLE_CHARACTERS.name()).getEnemyPlacements()).containsExactly(
            new EnemyPlacementRecord("goomba", 1695, 343),
            new EnemyPlacementRecord("para-goomba", 912, 336)
        );
        assertThat(record.getLayers().get(STATIC_ENVIRONMENT.name()).getEnemyPlacements()).isEmpty();
        assertThat(loaded.getNonPlayableCharactersLayer().getEnemyPlacements()).containsExactlyElementsOf(placements);
    }

    @Test
    @DisplayName("A layer stored before placements existed loads with none, and the normalizer gives it an empty list")
    void aRecordWithoutPlacementsHasNone() {
        // Prepare - an NPC layer holding only tile ids, as every scene saved before had
        final LevelSceneRecord legacy = LevelSceneRecord.builder()
            .id("scene-1")
            .rows(ROWS)
            .columns(COLUMNS)
            .layer(NON_PLAYABLE_CHARACTERS.name(), LevelSceneLayerData.builder()
                .type(NON_PLAYABLE_CHARACTERS)
                .tileIds(new int[ROWS * COLUMNS])
                .build())
            .build();

        // Execute
        final LevelSceneRecord normalized = converter.normalize(legacy);
        final LevelScene loaded = converter.toLevelScene(legacy);

        // Verify
        assertThat(normalized.getLayers().get(NON_PLAYABLE_CHARACTERS.name()).getEnemyPlacements()).isEmpty();
        assertThat(loaded.getNonPlayableCharactersLayer().getEnemyPlacements()).isEmpty();
    }

    @Test
    @DisplayName("Opening a scene gives the tab its own copy of the placements, which it can change")
    void openingASceneCopiesThePlacements() {
        // Prepare
        final LevelScene scene = new LevelScene();
        scene.setDimensions(new LevelSceneDimensions(COLUMNS, ROWS));
        final List<EnemyPlacement> stored = List.copyOf(placements);
        scene.setNonPlayableCharactersLayer(layer(NON_PLAYABLE_CHARACTERS, stored));
        final LevelSceneEditorTab target = newTab();

        // Execute
        LevelSceneEditorTabFactory.fromScene(scene);

        // Verify
        final ArgumentCaptor<List<LevelSceneLayer>> layers = captor();
        verify(target).setLayers(layers.capture());
        final List<EnemyPlacement> opened = layers.getValue().stream()
            .filter(layer -> layer.getType() == NON_PLAYABLE_CHARACTERS)
            .findFirst()
            .orElseThrow()
            .getEnemyPlacements();
        assertThat(opened).containsExactlyElementsOf(placements).isNotSameAs(stored);
        opened.add(new EnemyPlacement("goomba", 0, 0));
        assertThat(stored).as("The scene's list is untouched").hasSize(2);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<LevelSceneLayer>> captor() {
        return ArgumentCaptor.forClass(List.class);
    }

    private LevelSceneEditorTab newTab() {
        final LevelSceneEditorTab tab = mock(LevelSceneEditorTab.class);
        when(tab.getLevelSceneEditorGrid()).thenReturn(mock(LevelSceneEditorGrid.class));
        when(tab.getLevelSceneDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));
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
            .when(() -> StaticBeanFactory.getBean(eq(LevelSceneEditorTab.class), any(), any(), any(), any()))
            .thenReturn(tab);
        return tab;
    }

    private LevelSceneLayer layer(final LevelSceneLayerType type, final List<EnemyPlacement> enemies) {
        return LevelSceneLayer.builder()
            .type(type)
            .visible(true)
            .tiles(empty())
            .enemyPlacements(enemies)
            .build();
    }

    private Tile[][] empty() {
        final Tile[][] tiles = new Tile[ROWS][COLUMNS];
        for (final Tile[] row : tiles) {
            Arrays.fill(row, NULL_TILE);
        }
        return tiles;
    }
}
