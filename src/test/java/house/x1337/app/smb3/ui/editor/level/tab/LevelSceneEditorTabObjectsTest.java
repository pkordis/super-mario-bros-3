package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.MultiTileArea;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.repository.EnemyRepository;
import house.x1337.app.smb3.service.EnemyStampService;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyLevelSceneGridStamper;
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
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static house.x1337.app.smb3.enumeration.TileType.ENEMY_PART;
import static house.x1337.app.smb3.enumeration.TileType.OBJECT_INTERACTIVE_PART;
import static house.x1337.app.smb3.enumeration.TileType.OBJECT_INTERACTIVE_SINGLE;
import static house.x1337.app.smb3.enumeration.TileType.SOLID;
import static house.x1337.app.smb3.enumeration.enemy.EnemyType.GOOMBA_NORMAL;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Enemies and interactive objects on the editor grid: a right click removes a whole enemy, and placing an object
 * over another one on the same layer is refused, with the blocker shown. Terrain still paints over terrain.
 *
 * <p>The scene is 8 columns by 6 rows. The enemy is 2x2, anchored on its bottom-left part.
 */
class LevelSceneEditorTabObjectsTest {
    private static final int ROWS = 6;
    private static final int COLUMNS = 8;
    private static final List<LevelSceneLayerType> LAYER_TYPES =
        List.of(AIR, STATIC_ENVIRONMENT, INTERACTIVE_OBJECTS, NON_PLAYABLE_CHARACTERS);

    private final Tile topLeft = tile(31, ENEMY_PART);
    private final Tile topRight = tile(32, ENEMY_PART);
    private final Tile bottomLeft = tile(33, ENEMY_PART);
    private final Tile bottomRight = tile(34, ENEMY_PART);
    private final EnemyStamp goomba = EnemyStamp
        .builder()
        .id("goomba")
        .enemyType(GOOMBA_NORMAL)
        .tiles(new Tile[][] {
            {topLeft, topRight},
            {bottomLeft, bottomRight}
        })
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
        enemyStampService.upsert(goomba);
        tab = new LevelSceneEditorTab(
            mock(LevelSceneImportRouter.class),
            mock(TilePalettePanel.class),
            paletteSelectionService,
            new EnemyLevelSceneGridStamper(),
            enemyStampService
        );
        tab.setLevelSceneDimensions(new LevelSceneDimensions(COLUMNS, ROWS));
        final List<LevelSceneLayer> ordered = new ArrayList<>();
        for (final LevelSceneLayerType type : LAYER_TYPES) {
            final LevelSceneLayer layer = LevelSceneLayer.builder().type(type).visible(true).tiles(empty()).build();
            layers.put(type, layer);
            ordered.add(layer);
        }
        tab.setLayers(ordered);
        tab.setActiveLayer(layers.get(NON_PLAYABLE_CHARACTERS));
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A right click on any part of an enemy removes all of its parts and nothing else")
    void rightClickRemovesTheWholeEnemy() {
        // Prepare
        armEnemy();
        tab.paintTile(of(2, 4));
        tab.paintTile(of(5, 4));

        // Execute - the top-right part of the first enemy
        tab.eraseTile(of(3, 3));

        // Verify
        final Tile[][] npc = layers.get(NON_PLAYABLE_CHARACTERS).getTiles();
        assertThat(cells(npc, of(2, 3), of(3, 3), of(2, 4), of(3, 4))).containsOnly(NULL_TILE);
        assertThat(cells(npc, of(5, 3), of(6, 3), of(5, 4), of(6, 4)))
            .as("The other enemy stays")
            .containsExactly(topLeft, topRight, bottomLeft, bottomRight);
    }

    @Test
    @DisplayName("A right click on a part that belongs to no enemy clears that cell only")
    void rightClickOnAnOrphanPartClearsItsCell() {
        // Prepare
        final Tile[][] npc = layers.get(NON_PLAYABLE_CHARACTERS).getTiles();
        npc[1][6] = topRight;
        npc[1][7] = bottomRight;

        // Execute
        tab.eraseTile(of(6, 1));

        // Verify
        assertThat(npc[1][6]).isEqualTo(NULL_TILE);
        assertThat(npc[1][7]).isEqualTo(bottomRight);
    }

    @Test
    @DisplayName("An enemy over any cell of another one is refused, and the other one is shown as the blocker")
    void enemyOverAnotherEnemyIsRefused() {
        // Prepare
        armEnemy();
        tab.paintTile(of(2, 4));

        // Execute - one column right, so the two would share a column
        tab.paintTile(of(3, 4));

        // Verify
        final Tile[][] npc = layers.get(NON_PLAYABLE_CHARACTERS).getTiles();
        assertThat(cells(npc, of(2, 3), of(3, 3), of(2, 4), of(3, 4)))
            .containsExactly(topLeft, topRight, bottomLeft, bottomRight);
        assertThat(cells(npc, of(4, 3), of(4, 4))).containsOnly(NULL_TILE);
        verify(grid).refusePlacement(List.of(new MultiTileArea(2, 3, 2, 2)));
    }

    @Test
    @DisplayName("An enemy right next to another one goes in, and is recorded as placed")
    void enemyNextToAnotherEnemyIsPlaced() {
        // Prepare
        armEnemy();
        tab.paintTile(of(2, 4));

        // Execute
        tab.paintTile(of(4, 4));

        // Verify
        final Tile[][] npc = layers.get(NON_PLAYABLE_CHARACTERS).getTiles();
        assertThat(cells(npc, of(4, 3), of(5, 3), of(4, 4), of(5, 4)))
            .containsExactly(topLeft, topRight, bottomLeft, bottomRight);
        verify(grid).notePlacement(new MultiTileArea(4, 3, 2, 2));
        verify(grid, never()).refusePlacement(any());
    }

    @Test
    @DisplayName("A tile painted on the NPC layer inside an enemy is refused")
    void tileInsideAnEnemyIsRefused() {
        // Prepare
        armEnemy();
        tab.paintTile(of(2, 4));
        armTile(tile(35, ENEMY_PART));

        // Execute
        tab.paintTile(of(3, 3));

        // Verify
        assertThat(layers.get(NON_PLAYABLE_CHARACTERS).getTiles()[3][3]).isEqualTo(topRight);
        verify(grid).refusePlacement(List.of(new MultiTileArea(2, 3, 2, 2)));
    }

    @Test
    @DisplayName("An interactive object over another one is refused, and the cell is shown as the blocker")
    void interactiveObjectOverAnotherIsRefused() {
        // Prepare
        final Tile brick = tile(61, OBJECT_INTERACTIVE_SINGLE);
        final Tile giantPart = tile(62, OBJECT_INTERACTIVE_PART);
        tab.setActiveLayer(layers.get(INTERACTIVE_OBJECTS));
        armTile(brick);
        tab.paintTile(of(1, 1));
        armTile(giantPart);

        // Execute
        tab.paintTile(of(1, 1));
        tab.paintTile(of(2, 1));

        // Verify
        assertThat(layers.get(INTERACTIVE_OBJECTS).getTiles()[1][1]).isEqualTo(brick);
        assertThat(layers.get(INTERACTIVE_OBJECTS).getTiles()[1][2]).as("A free cell").isEqualTo(giantPart);
        verify(grid).refusePlacement(List.of(new MultiTileArea(of(1, 1))));
    }

    @Test
    @DisplayName("Terrain still paints over terrain")
    void terrainPaintsOverTerrain() {
        // Prepare
        final Tile ground = tile(71, SOLID);
        final Tile otherGround = tile(72, SOLID);
        tab.setActiveLayer(layers.get(STATIC_ENVIRONMENT));
        armTile(ground);
        tab.paintTile(of(4, 5));
        armTile(otherGround);

        // Execute
        tab.paintTile(of(4, 5));

        // Verify
        assertThat(layers.get(STATIC_ENVIRONMENT).getTiles()[5][4]).isEqualTo(otherGround);
        verify(grid, never()).refusePlacement(any());
    }

    @Test
    @DisplayName("Changing the active layer refreshes the placement preview")
    void changingTheActiveLayerRefreshesThePreview() {
        // Execute
        tab.setActiveLayer(layers.get(AIR));

        // Verify - once for the layer set up in prepare, once here
        verify(grid, times(2)).refreshPreview();
    }

    private void armEnemy() {
        when(paletteSelectionService.getSelectedEnemy()).thenReturn(goomba);
        when(paletteSelectionService.getSelectedTile()).thenReturn(null);
    }

    private void armTile(final Tile tile) {
        when(paletteSelectionService.getSelectedEnemy()).thenReturn(null);
        when(paletteSelectionService.getSelectedTile()).thenReturn(tile);
    }

    private List<Tile> cells(final Tile[][] tiles, final Coordinates... cells) {
        return Arrays.stream(cells).map(cell -> tiles[cell.y()][cell.x()]).toList();
    }

    private Tile tile(final int id, final TileType type) {
        return Tile.builder().id(id).type(type).build();
    }

    private Tile[][] empty() {
        final Tile[][] tiles = new Tile[ROWS][COLUMNS];
        for (final Tile[] row : tiles) {
            Arrays.fill(row, NULL_TILE);
        }
        return tiles;
    }
}
