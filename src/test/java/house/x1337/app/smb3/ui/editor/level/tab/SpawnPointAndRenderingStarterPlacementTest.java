package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.enumeration.VirtualObjectCategory;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyLevelSceneGridStamper;
import house.x1337.app.smb3.ui.editor.level.service.PaletteSelectionService;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import house.x1337.app.smb3.ui.editor.level.virtualobject.palette.VirtualObjectButton;
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
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.TileType.ENEMY_PART;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.LEVEL_COMPLETION_CARD;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.RENDERING_STARTER;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.SPAWN_POINT;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.awt.Color.BLUE;
import static java.awt.Color.GREEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Placing, moving and removing the scene's rendering starter and spawn point from the Virtual Objects palette.
 *
 * <p>The scene is 6 rows by 8 columns. The spawn point is stored by its upper cell, where the player's head is at
 * the start, and placed by its lower one.
 */
class SpawnPointAndRenderingStarterPlacementTest {
    private static final int ROWS = 6;
    private static final int COLUMNS = 8;

    private final Map<LevelSceneLayerType, LevelSceneLayer> layers = new EnumMap<>(LevelSceneLayerType.class);

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private PaletteSelectionService paletteSelectionService;
    private LevelSceneEditorTab tab;

    @BeforeEach
    void prepare() {
        paletteSelectionService = new PaletteSelectionService();
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(LevelSceneEditorGrid.class), any(), any()))
            .thenReturn(mock(LevelSceneEditorGrid.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(VirtualObjectButton.class))
            .thenAnswer(invocation -> new VirtualObjectButton());

        tab = new LevelSceneEditorTab(
            mock(LevelSceneImportRouter.class),
            mock(TilePalettePanel.class),
            paletteSelectionService,
            mock(EnemyLevelSceneGridStamper.class)
        );
        tab.setRows(ROWS);
        tab.setColumns(COLUMNS);
        final List<LevelSceneLayer> ordered = new ArrayList<>();
        for (final LevelSceneLayerType type : List.of(AIR, NON_PLAYABLE_CHARACTERS)) {
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
    @DisplayName("The spawn point is one green column two tiles tall, the rendering starter one blue tile")
    void theirShapesAndColours() {
        // Execute & Verify
        assertThat(SPAWN_POINT.getCategory()).isEqualTo(VirtualObjectCategory.SPAWN_POINT);
        assertThat(SPAWN_POINT.getColour()).isEqualTo(GREEN);
        assertThat(SPAWN_POINT.getRows()).isEqualTo(2);
        assertThat(SPAWN_POINT.getColumns()).isEqualTo(1);
        assertThat(SPAWN_POINT.getLabel()).isEqualTo("Spawn Point");
        assertThat(RENDERING_STARTER.getCategory()).isEqualTo(VirtualObjectCategory.RENDERING_STARTER);
        assertThat(RENDERING_STARTER.getColour()).isEqualTo(BLUE);
        assertThat(RENDERING_STARTER.getRows()).isEqualTo(1);
        assertThat(RENDERING_STARTER.getColumns()).isEqualTo(1);
        assertThat(RENDERING_STARTER.getLabel()).isEqualTo("Rendering Starter");
    }

    @Test
    @DisplayName("A click puts the rendering starter on that cell without touching the NPC tiles")
    void clickPlacesTheRenderingStarter() {
        // Prepare
        arm(RENDERING_STARTER);

        // Execute
        tab.paintTile(of(5, 2));

        // Verify
        assertThat(tab.getRenderingStarter()).isEqualTo(of(5, 2));
        assertThat(layers.get(NON_PLAYABLE_CHARACTERS).getTiles()[2][5]).isSameAs(NULL_TILE);
    }

    @Test
    @DisplayName("A click places the spawn point's lower cell, so the stored cell is the one above it")
    void clickPlacesTheSpawnPointsLowerCell() {
        // Prepare
        arm(SPAWN_POINT);

        // Execute
        tab.paintTile(of(3, 4));

        // Verify
        assertThat(tab.getSpawnPoint()).isEqualTo(of(3, 3));
        assertThat(tab.spawnPointCells()).containsExactly(of(3, 3), of(3, 4));
        assertThat(layers.get(NON_PLAYABLE_CHARACTERS).getTiles()[4][3]).isSameAs(NULL_TILE);
    }

    @Test
    @DisplayName("A click on the top row places no spawn point, as its upper cell would leave the scene")
    void topRowRefusesTheSpawnPoint() {
        // Prepare
        arm(SPAWN_POINT);

        // Execute
        tab.paintTile(of(3, 0));

        // Verify
        assertThat(tab.hasSpawnPoint()).isFalse();
    }

    @Test
    @DisplayName("Placing either again moves it, so a scene only ever has one of each")
    void placingAgainMovesIt() {
        // Prepare
        arm(RENDERING_STARTER);
        tab.paintTile(of(1, 1));
        arm(SPAWN_POINT);
        tab.paintTile(of(2, 5));

        // Execute
        arm(RENDERING_STARTER);
        tab.paintTile(of(6, 2));
        arm(SPAWN_POINT);
        tab.paintTile(of(7, 3));

        // Verify
        assertThat(tab.getRenderingStarter()).isEqualTo(of(6, 2));
        assertThat(tab.getSpawnPoint()).isEqualTo(of(7, 2));
    }

    @Test
    @DisplayName("Nothing is placed while another layer is active")
    void refusedOffTheNpcLayer() {
        // Prepare
        tab.setActiveLayer(layers.get(AIR));

        // Execute
        arm(RENDERING_STARTER);
        tab.paintTile(of(5, 2));
        arm(SPAWN_POINT);
        tab.paintTile(of(5, 4));

        // Verify
        assertThat(tab.hasRenderingStarter()).isFalse();
        assertThat(tab.hasSpawnPoint()).isFalse();
        assertThat(layers.get(AIR).getTiles()[2][5]).isSameAs(NULL_TILE);
    }

    @Test
    @DisplayName("Right-clicking either of the spawn point's cells removes it and leaves the NPC tile alone")
    void rightClickOnEitherCellRemovesTheSpawnPoint() {
        // Prepare
        arm(SPAWN_POINT);
        tab.paintTile(of(3, 4));
        final Tile enemyPart = Tile.builder().id(500).type(ENEMY_PART).build();
        layers.get(NON_PLAYABLE_CHARACTERS).getTiles()[3][3] = enemyPart;

        // Execute - the upper cell
        tab.eraseTile(of(3, 3));

        // Verify
        assertThat(tab.hasSpawnPoint()).isFalse();
        assertThat(layers.get(NON_PLAYABLE_CHARACTERS).getTiles()[3][3]).isSameAs(enemyPart);

        // Prepare
        tab.paintTile(of(3, 4));

        // Execute - the lower cell
        tab.eraseTile(of(3, 4));

        // Verify
        assertThat(tab.hasSpawnPoint()).isFalse();
    }

    @Test
    @DisplayName("Right-clicking the rendering starter's cell removes it; another cell leaves it in place")
    void rightClickRemovesTheRenderingStarter() {
        // Prepare
        arm(RENDERING_STARTER);
        tab.paintTile(of(2, 2));

        // Execute
        tab.eraseTile(of(3, 2));

        // Verify
        assertThat(tab.getRenderingStarter()).isEqualTo(of(2, 2));

        // Execute
        tab.eraseTile(of(2, 2));

        // Verify
        assertThat(tab.hasRenderingStarter()).isFalse();
    }

    @Test
    @DisplayName("Right-clicking with another layer active keeps both and erases that layer")
    void rightClickOffTheNpcLayerKeepsThem() {
        // Prepare
        arm(RENDERING_STARTER);
        tab.paintTile(of(2, 2));
        arm(SPAWN_POINT);
        tab.paintTile(of(4, 4));
        final Tile sky = Tile.builder().id(600).build();
        layers.get(AIR).getTiles()[2][2] = sky;
        tab.setActiveLayer(layers.get(AIR));

        // Execute
        tab.eraseTile(of(2, 2));
        tab.eraseTile(of(4, 4));

        // Verify
        assertThat(tab.getRenderingStarter()).isEqualTo(of(2, 2));
        assertThat(tab.getSpawnPoint()).isEqualTo(of(4, 3));
        assertThat(layers.get(AIR).getTiles()[2][2]).isSameAs(NULL_TILE);
    }

    @Test
    @DisplayName("Right-click on a cell holding the card, starter and spawn point removes them top down")
    void rightClickRemovesTheTopmostFirst() {
        // Prepare
        arm(SPAWN_POINT);
        tab.paintTile(of(3, 3));
        arm(RENDERING_STARTER);
        tab.paintTile(of(3, 3));
        arm(LEVEL_COMPLETION_CARD);
        tab.paintTile(of(3, 3));

        // Execute
        tab.eraseTile(of(3, 3));

        // Verify
        assertThat(tab.hasLevelCompletionCard()).isFalse();
        assertThat(tab.hasRenderingStarter()).isTrue();

        // Execute
        tab.eraseTile(of(3, 3));

        // Verify
        assertThat(tab.hasRenderingStarter()).isFalse();
        assertThat(tab.hasSpawnPoint()).isTrue();

        // Execute
        tab.eraseTile(of(3, 3));

        // Verify
        assertThat(tab.hasSpawnPoint()).isFalse();
    }

    @Test
    @DisplayName("A spawn point fits only when the cell below it is inside the scene too")
    void spawnPointFitsOnlyAboveTheBottomRow() {
        // Execute & Verify
        assertThat(tab.spawnPointFits(of(0, 0))).as("Top row").isTrue();
        assertThat(tab.spawnPointFits(of(7, ROWS - 2))).as("One above the bottom row").isTrue();
        assertThat(tab.spawnPointFits(of(7, ROWS - 1))).as("Bottom row").isFalse();
        assertThat(tab.spawnPointFits(of(COLUMNS, 2))).as("Right of the scene").isFalse();
    }

    @Test
    @DisplayName("Clearing the scene removes both")
    void clearingTheSceneRemovesBoth() {
        // Prepare
        arm(RENDERING_STARTER);
        tab.paintTile(of(2, 2));
        arm(SPAWN_POINT);
        tab.paintTile(of(4, 4));

        // Execute
        tab.clearScene();

        // Verify
        assertThat(tab.hasRenderingStarter()).isFalse();
        assertThat(tab.hasSpawnPoint()).isFalse();
        assertThat(tab.spawnPointCells()).isEmpty();
    }

    private void arm(final VirtualObjectType type) {
        paletteSelectionService.select(VirtualObjectButton.fromType(type));
    }

    private Tile[][] empty() {
        final Tile[][] tiles = new Tile[ROWS][COLUMNS];
        for (final Tile[] row : tiles) {
            Arrays.fill(row, NULL_TILE);
        }
        return tiles;
    }
}
