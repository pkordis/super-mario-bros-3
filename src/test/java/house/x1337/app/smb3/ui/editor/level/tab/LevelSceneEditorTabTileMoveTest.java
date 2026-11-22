package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.EnemyStampService;
import house.x1337.app.smb3.ui.editor.level.service.PaletteSelectionService;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import house.x1337.app.smb3.util.factory.LevelSceneImportRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static house.x1337.app.smb3.enumeration.TileType.OBJECT_INTERACTIVE_SINGLE;
import static house.x1337.app.smb3.enumeration.TileType.SOLID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/** An open tab's share of a tile's move to another layer, and how it tells whether its import holds a tile. */
class LevelSceneEditorTabTileMoveTest {
    private final Tile tile = Tile.builder().id(1042).type(OBJECT_INTERACTIVE_SINGLE).build();
    private final Tile ground = Tile.builder().id(1043).type(SOLID).build();
    private final Tile brick = Tile.builder().id(1044).type(OBJECT_INTERACTIVE_SINGLE).build();
    private final Map<LevelSceneLayerType, LevelSceneLayer> layers = new EnumMap<>(LevelSceneLayerType.class);

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private LevelSceneEditorTab tab;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(LevelSceneEditorGrid.class), any(), any(), any()))
            .thenReturn(mock(LevelSceneEditorGrid.class));
        tab = new LevelSceneEditorTab(
            mock(LevelSceneImportRouter.class),
            mock(TilePalettePanel.class),
            new PaletteSelectionService(),
            mock(EnemyStampService.class)
        );
        // One row of four cells: the tile twice on the terrain, once over a brick
        final List<LevelSceneLayer> ordered = new ArrayList<>();
        ordered.add(layer(AIR, NULL_TILE, NULL_TILE, NULL_TILE, NULL_TILE));
        ordered.add(layer(STATIC_ENVIRONMENT, tile, ground, tile, NULL_TILE));
        ordered.add(layer(INTERACTIVE_OBJECTS, NULL_TILE, NULL_TILE, brick, tile));
        tab.setLayers(ordered);
        tab.setLevelSceneDimensions(new LevelSceneDimensions(4, 1));
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Only the tile's cells outside the target layer count")
    void countsTheCellsOutsideTheTarget() {
        // Execute & Verify
        assertThat(tab.countTileCellsOutside(tile.getId(), INTERACTIVE_OBJECTS)).isEqualTo(2);
        assertThat(tab.countTileCellsOutside(tile.getId(), STATIC_ENVIRONMENT)).isEqualTo(1);
        assertThat(tab.countTileCellsOutside(ground.getId(), STATIC_ENVIRONMENT)).isZero();
    }

    @Test
    @DisplayName("Moving clears the tile's old cells and puts it in the same cells of the target, over what was there")
    void movingRewritesTheLayers() {
        // Execute
        tab.moveTile(tile, INTERACTIVE_OBJECTS);

        // Verify
        assertThat(layers.get(STATIC_ENVIRONMENT).getTiles()[0])
            .containsExactly(NULL_TILE, ground, NULL_TILE, NULL_TILE);
        assertThat(layers.get(INTERACTIVE_OBJECTS).getTiles()[0]).containsExactly(tile, NULL_TILE, tile, tile);
        assertThat(layers.get(AIR).getTiles()[0]).containsOnly(NULL_TILE);
    }

    @Test
    @DisplayName("A tab tells whether its PNG import holds a tile")
    void knowsWhatItsImportHolds() {
        // Prepare
        final boolean beforeAnyImport = tab.importsTile(tile.getId());
        tab.setImportGrid(new Tile[][] {{tile, ground}});

        // Execute & Verify
        assertThat(beforeAnyImport).isFalse();
        assertThat(tab.importsTile(tile.getId())).isTrue();
        assertThat(tab.importsTile(brick.getId())).isFalse();
    }

    private LevelSceneLayer layer(final LevelSceneLayerType type, final Tile... row) {
        final LevelSceneLayer layer = LevelSceneLayer.builder()
            .type(type)
            .visible(true)
            .tiles(new Tile[][] {row})
            .build();
        layers.put(type, layer);
        return layer;
    }
}
