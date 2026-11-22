package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.MultiTileArea;
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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Enemies and interactive objects on the editor grid. Enemies are placements at a pixel, not tiles: placing one
 * selects it, arrow keys nudge it, a right click inside its box removes it, and placing an object over another one
 * on the same layer is refused, with the blocker shown. Terrain still paints over terrain.
 *
 * <p>The scene is 8 columns by 6 rows. The enemy is 2x2, anchored on its bottom-left part, so placed on cell
 * (2, 4) its rendering starter starts on pixel (32, 64) and its box is (32, 48) to (63, 79).
 */
class LevelSceneEditorTabObjectsTest {
    private static final int ROWS = 6;
    private static final int COLUMNS = 8;
    private static final List<LevelSceneLayerType> LAYER_TYPES =
        List.of(AIR, STATIC_ENVIRONMENT, INTERACTIVE_OBJECTS, NON_PLAYABLE_CHARACTERS);
    private static final PixelArea BOX_ON_2_4 = new PixelArea(32, 48, 32, 32);

    private final EnemyStamp goomba = EnemyStamp
        .builder()
        .id("goomba")
        .enemyType(GOOMBA_NORMAL)
        .tiles(new Tile[][] {
            {tile(31, ENEMY_PART), tile(32, ENEMY_PART)},
            {tile(33, ENEMY_PART), tile(34, ENEMY_PART)}
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
    @DisplayName("Placing an enemy adds it on the clicked cell's top-left pixel, selects it, and writes no tiles")
    void placingAnEnemyAddsAPlacement() {
        // Prepare
        armEnemy();

        // Execute
        tab.paintTile(of(2, 4));

        // Verify
        final EnemyPlacement placed = new EnemyPlacement("goomba", 32, 64);
        assertThat(enemies()).containsExactly(placed);
        assertThat(tab.getSelectedEnemyPlacement()).isEqualTo(placed);
        assertThat(Arrays.stream(layers.get(NON_PLAYABLE_CHARACTERS).getTiles()).flatMap(Arrays::stream).toList())
            .as("No part tiles")
            .containsOnly(NULL_TILE);
        verify(grid).notePlacement(BOX_ON_2_4);
    }

    @Test
    @DisplayName("An enemy placed on a pixel goes exactly there, and nowhere with its rendering starter off the scene")
    void placingAnEnemyOnAPixel() {
        // Prepare
        armEnemy();

        // Execute
        tab.paintTile(of(2, 4), of(37, 70));
        tab.paintTile(of(0, 0), of(-3, 70));

        // Verify
        assertThat(enemies()).containsExactly(new EnemyPlacement("goomba", 37, 70));
        assertThat(tab.getSelectedEnemyPlacement()).isEqualTo(new EnemyPlacement("goomba", 37, 70));
        verify(grid).notePlacement(new PixelArea(37, 54, 32, 32));
    }

    @Test
    @DisplayName("A right click inside an enemy's box removes that enemy, wherever in its box, and nothing else")
    void rightClickRemovesTheEnemy() {
        // Prepare
        armEnemy();
        tab.paintTile(of(2, 4));
        tab.paintTile(of(5, 4));

        // Execute - the top-right part of the first enemy
        tab.eraseAt(of(3, 3), of(60, 50));

        // Verify
        assertThat(enemies()).containsExactly(new EnemyPlacement("goomba", 80, 64));
        assertThat(tab.getSelectedEnemyPlacement()).as("The other one stays selected").isNotNull();
    }

    @Test
    @DisplayName("Removing the selected enemy drops the selection")
    void removingTheSelectedEnemyDropsTheSelection() {
        // Prepare
        armEnemy();
        tab.paintTile(of(2, 4));

        // Execute
        tab.eraseTile(of(2, 4));

        // Verify
        assertThat(enemies()).isEmpty();
        assertThat(tab.getSelectedEnemyPlacement()).isNull();
    }

    @Test
    @DisplayName("A right click on the NPC layer away from any enemy clears that cell's tile only")
    void rightClickAwayFromEnemiesClearsTheCell() {
        // Prepare
        armEnemy();
        tab.paintTile(of(2, 4));
        final Tile[][] npc = layers.get(NON_PLAYABLE_CHARACTERS).getTiles();
        npc[1][6] = tile(35, ENEMY_PART);

        // Execute
        tab.eraseTile(of(6, 1));

        // Verify
        assertThat(npc[1][6]).isEqualTo(NULL_TILE);
        assertThat(enemies()).hasSize(1);
    }

    @Test
    @DisplayName("An enemy over another one is refused, and the other one's box is shown as the blocker")
    void enemyOverAnotherEnemyIsRefused() {
        // Prepare
        armEnemy();
        tab.paintTile(of(2, 4));

        // Execute - one column right, so the two would share a column
        tab.paintTile(of(3, 4));

        // Verify
        assertThat(enemies()).containsExactly(new EnemyPlacement("goomba", 32, 64));
        verify(grid).refusePlacement(List.of(BOX_ON_2_4));
    }

    @Test
    @DisplayName("Boxes overlap by the pixel: an enemy nudged a pixel into the next cell blocks a placement there")
    void boxesOverlapByThePixel() {
        // Prepare
        armEnemy();
        tab.paintTile(of(2, 4));
        tab.nudgeSelectedEnemy(1, 0);

        // Execute
        tab.paintTile(of(4, 4));

        // Verify
        assertThat(enemies()).containsExactly(new EnemyPlacement("goomba", 33, 64));
        verify(grid).refusePlacement(List.of(new PixelArea(33, 48, 32, 32)));
    }

    @Test
    @DisplayName("An enemy right next to another one goes in")
    void enemyNextToAnotherEnemyIsPlaced() {
        // Prepare
        armEnemy();
        tab.paintTile(of(2, 4));

        // Execute
        tab.paintTile(of(4, 4));

        // Verify
        assertThat(enemies()).hasSize(2);
        verify(grid).notePlacement(new PixelArea(64, 48, 32, 32));
        verify(grid, never()).refusePlacement(any());
    }

    @Test
    @DisplayName("A tile painted on the NPC layer inside an enemy's box is refused")
    void tileInsideAnEnemyIsRefused() {
        // Prepare
        armEnemy();
        tab.paintTile(of(2, 4));
        armTile(tile(35, ENEMY_PART));

        // Execute
        tab.paintTile(of(3, 3));

        // Verify
        assertThat(layers.get(NON_PLAYABLE_CHARACTERS).getTiles()[3][3]).isEqualTo(NULL_TILE);
        verify(grid).refusePlacement(List.of(BOX_ON_2_4));
    }

    @Test
    @DisplayName("Pressing on an enemy selects it, pressing elsewhere drops the selection, and only on the NPC layer")
    void pressingSelectsTheEnemyUnderThePointer() {
        // Prepare
        armEnemy();
        tab.paintTile(of(2, 4));
        tab.paintTile(of(5, 4));

        // Execute & Verify
        tab.selectEnemyAt(of(40, 60));
        assertThat(tab.getSelectedEnemyPlacement()).isEqualTo(new EnemyPlacement("goomba", 32, 64));
        tab.selectEnemyAt(of(10, 10));
        assertThat(tab.getSelectedEnemyPlacement()).as("Empty pixel").isNull();
        tab.setActiveLayer(layers.get(AIR));
        tab.selectEnemyAt(of(40, 60));
        assertThat(tab.getSelectedEnemyPlacement()).as("Another layer").isNull();
    }

    @Test
    @DisplayName("Nudging moves the selected enemy by the pixel, across cells, and keeps it selected")
    void nudgingMovesTheSelectedEnemy() {
        // Prepare
        armEnemy();
        tab.paintTile(of(2, 4));

        // Execute
        final boolean moved = tab.nudgeSelectedEnemy(-4, 0) && tab.nudgeSelectedEnemy(-1, 7);

        // Verify - 5 px left of cell 2 is 11 px into cell 1
        final EnemyPlacement nudged = new EnemyPlacement("goomba", 27, 71);
        assertThat(moved).isTrue();
        assertThat(enemies()).containsExactly(nudged);
        assertThat(tab.getSelectedEnemyPlacement()).isEqualTo(nudged);
        assertThat(nudged.rendererCell()).isEqualTo(of(1, 4));
    }

    @Test
    @DisplayName("A nudge into another enemy is refused and shows the blocker; one off the scene is refused too")
    void blockedNudgesAreRefused() {
        // Prepare
        armEnemy();
        tab.paintTile(of(2, 4));
        tab.paintTile(of(4, 4));
        clearInvocations(grid);

        // Execute
        final boolean intoTheOther = tab.nudgeSelectedEnemy(-1, 0);
        tab.selectEnemyAt(of(40, 60));
        final boolean offTheScene = tab.nudgeSelectedEnemy(0, 32);

        // Verify
        assertThat(intoTheOther).isFalse();
        assertThat(offTheScene).as("The rendering starter would be below the last row").isFalse();
        assertThat(enemies())
            .containsExactly(new EnemyPlacement("goomba", 32, 64), new EnemyPlacement("goomba", 64, 64));
        verify(grid).refusePlacement(List.of(BOX_ON_2_4));
    }

    @Test
    @DisplayName("With nothing selected, a nudge does nothing")
    void nudgeWithoutSelection() {
        // Execute
        final boolean moved = tab.nudgeSelectedEnemy(1, 0);

        // Verify
        assertThat(moved).isFalse();
    }

    @Test
    @DisplayName("Growing the scene moves the enemies down with the ground; a cut reports their rendering starter")
    void resizingMovesAndReportsEnemies() {
        // Prepare
        armEnemy();
        tab.paintTile(of(6, 1));
        tab.nudgeSelectedEnemy(3, 2);

        // Execute
        final Map<String, List<Coordinates>> cutByNarrowing = tab.findContentCutBy(6, ROWS);
        tab.resizeScene(COLUMNS, ROWS + 2);

        // Verify
        assertThat(cutByNarrowing).containsEntry(NON_PLAYABLE_CHARACTERS.getLabel(), List.of(of(6, 1)));
        assertThat(enemies()).containsExactly(new EnemyPlacement("goomba", 99, 50));
    }

    @Test
    @DisplayName("Clearing the scene removes the enemies and the selection")
    void clearingRemovesTheEnemies() {
        // Prepare
        armEnemy();
        tab.paintTile(of(2, 4));

        // Execute
        tab.clearScene();

        // Verify
        assertThat(enemies()).isEmpty();
        assertThat(tab.getSelectedEnemyPlacement()).isNull();
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
        verify(grid).refusePlacement(List.of(new MultiTileArea(of(1, 1)).toPixelArea()));
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

    private List<EnemyPlacement> enemies() {
        return layers.get(NON_PLAYABLE_CHARACTERS).getEnemyPlacements();
    }

    private void armEnemy() {
        when(paletteSelectionService.getSelectedEnemy()).thenReturn(goomba);
        when(paletteSelectionService.getSelectedTile()).thenReturn(null);
    }

    private void armTile(final Tile tile) {
        when(paletteSelectionService.getSelectedEnemy()).thenReturn(null);
        when(paletteSelectionService.getSelectedTile()).thenReturn(tile);
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
