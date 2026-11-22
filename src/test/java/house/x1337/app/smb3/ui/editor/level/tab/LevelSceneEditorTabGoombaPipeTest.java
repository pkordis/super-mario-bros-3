package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.PixelArea;
import house.x1337.app.smb3.model.game.enemy.EnemyPlacement;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.repository.EnemyRepository;
import house.x1337.app.smb3.service.EnemyStampService;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.service.PaletteSelectionService;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import house.x1337.app.smb3.util.factory.LevelSceneImportRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static house.x1337.app.smb3.enumeration.TileType.ENEMY_PART;
import static house.x1337.app.smb3.enumeration.TileType.PIPE_BODY_PART;
import static house.x1337.app.smb3.enumeration.TileType.PIPE_TERMINATION_PART;
import static house.x1337.app.smb3.enumeration.enemy.EnemyType.GOOMBA_PIPE_RIGHT;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Placing a Goomba pipe on the editor grid. The scene is 6 columns by 6 rows with a side pipe opening to the right:
 * body on column 1, lip on column 2, rows 3 and 4, and no pipe portal. The stamp is 1x2, anchored on its lower part,
 * so right of the lower lip cell, on (3, 4), its box is (48, 48) to (63, 79).
 */
class LevelSceneEditorTabGoombaPipeTest {
    private static final int SIZE = 6;
    private static final List<LevelSceneLayerType> LAYER_TYPES = List.of(STATIC_ENVIRONMENT, NON_PLAYABLE_CHARACTERS);

    private final EnemyStamp goombaPipe = EnemyStamp
        .builder()
        .id("goomba-pipe")
        .enemyType(GOOMBA_PIPE_RIGHT)
        .tiles(new Tile[][] {{tile(31, ENEMY_PART)}, {tile(32, ENEMY_PART)}})
        .renderingStarter(of(0, 1))
        .build();
    private final Map<LevelSceneLayerType, LevelSceneLayer> layers = new EnumMap<>(LevelSceneLayerType.class);
    private final PaletteSelectionService paletteSelectionService = mock(PaletteSelectionService.class);

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private LevelSceneEditorGrid grid;
    private LevelSceneEditorTab tab;

    @BeforeEach
    void prepare() {
        grid = mock(LevelSceneEditorGrid.class);
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(LevelSceneEditorGrid.class), any(), any(), any()))
            .thenReturn(grid);
        final EnemyStampService enemyStampService = new EnemyStampService(
            mock(EnemyRepository.class),
            mock(TileService.class)
        );
        enemyStampService.upsert(goombaPipe);
        tab = new LevelSceneEditorTab(
            mock(LevelSceneImportRouter.class),
            mock(TilePalettePanel.class),
            paletteSelectionService,
            enemyStampService
        );
        tab.setLevelSceneDimensions(new LevelSceneDimensions(SIZE, SIZE));
        final List<LevelSceneLayer> ordered = new ArrayList<>();
        for (final LevelSceneLayerType type : LAYER_TYPES) {
            final LevelSceneLayer layer = LevelSceneLayer.builder().type(type).visible(true).tiles(empty()).build();
            layers.put(type, layer);
            ordered.add(layer);
        }
        tab.setLayers(ordered);
        tab.setActiveLayer(layers.get(NON_PLAYABLE_CHARACTERS));
        final Tile[][] terrain = layers.get(STATIC_ENVIRONMENT).getTiles();
        terrain[3][1] = tile(41, PIPE_BODY_PART);
        terrain[4][1] = tile(41, PIPE_BODY_PART);
        terrain[3][2] = tile(42, PIPE_TERMINATION_PART);
        terrain[4][2] = tile(42, PIPE_TERMINATION_PART);
        when(paletteSelectionService.getSelectedEnemy()).thenReturn(goombaPipe);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A Goomba pipe right of the pipe lip is placed, with no pipe portal")
    void isPlacedRightOfTheLip() {
        // Execute
        tab.paintTile(of(3, 4));

        // Verify
        assertThat(enemies()).containsExactly(new EnemyPlacement("goomba-pipe", 48, 64));
        verify(grid, never()).refusePlacement(any());
    }

    @Test
    @DisplayName("A refused Goomba pipe is not placed, and the box it would have covered blinks")
    void aRefusalBlinksTheFootprint() {
        // Execute
        tab.paintTile(of(4, 4));

        // Verify
        assertThat(enemies()).isEmpty();
        verify(grid).refusePlacement(List.of(new PixelArea(64, 48, 16, 32)));
    }

    @Test
    @DisplayName("A Goomba pipe placed on a pixel inside its cell is refused")
    void isRefusedOnAPixel() {
        // Execute
        tab.paintTile(of(3, 4), of(51, 64));

        // Verify
        assertThat(enemies()).isEmpty();
        verify(grid).refusePlacement(List.of(new PixelArea(51, 48, 16, 32)));
    }

    @Test
    @DisplayName("A placed Goomba pipe cannot be nudged off its cell")
    void cannotBeNudged() {
        // Prepare
        tab.paintTile(of(3, 4));

        // Execute
        final boolean moved = tab.nudgeSelectedEnemy(1, 0);

        // Verify
        assertThat(moved).isFalse();
        assertThat(enemies()).containsExactly(new EnemyPlacement("goomba-pipe", 48, 64));
        verify(grid).refusePlacement(List.of(new PixelArea(49, 48, 16, 32)));
    }

    private List<EnemyPlacement> enemies() {
        return layers.get(NON_PLAYABLE_CHARACTERS).getEnemyPlacements();
    }

    private Tile tile(final int id, final TileType type) {
        return Tile.builder().id(id).type(type).build();
    }

    private Tile[][] empty() {
        final Tile[][] tiles = new Tile[SIZE][SIZE];
        for (final Tile[] row : tiles) {
            Arrays.fill(row, NULL_TILE);
        }
        return tiles;
    }
}
