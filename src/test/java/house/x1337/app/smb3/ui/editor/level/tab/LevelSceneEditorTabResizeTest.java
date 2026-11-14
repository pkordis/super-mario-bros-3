package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.enumeration.MusicTrack;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.ui.tile.Tile;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static house.x1337.app.smb3.enumeration.TileType.RENDERING_STARTER;
import static house.x1337.app.smb3.enumeration.TileType.SPAWN_POINT;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_VERTICAL;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * A resize keeps the scene's bottom-left corner in place, so what is placed keeps its distance from the ground. The
 * check before a shrink names every cell the new size would cut. Clearing empties the scene but keeps what it is.
 */
class LevelSceneEditorTabResizeTest {
    private final Tile tile = mock(Tile.class);
    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private LevelSceneEditorTab tab;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(LevelSceneEditorGrid.class), any()))
            .thenReturn(mock(LevelSceneEditorGrid.class));
        tab = new LevelSceneEditorTab(
            mock(LevelSceneImportRouter.class),
            mock(TilePalettePanel.class),
            new PaletteSelectionService(),
            mock(EnemyLevelSceneGridStamper.class)
        );
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Adding rows moves the tiles, markers, card and pipe portals down by the rows added")
    void addingRowsMovesEverythingDown() {
        // Prepare
        tab.newScene(15, 16);
        place(STATIC_ENVIRONMENT, of(2, 14));
        tab.setSpawnPoint(of(3, 13));
        tab.setRenderingStarter(of(0, 0));
        tab.setLevelCompletionCard(of(5, 12));
        final PipePortal portal = portalAt(of(7, 14));

        // Execute
        tab.resize(16, 20);

        // Verify
        assertThat(tab.getRows()).isEqualTo(20);
        assertThat(tab.getColumns()).isEqualTo(16);
        assertThat(tab.getLayers()).allSatisfy(layer -> assertThat(layer.getTiles()).hasDimensions(20, 16));
        assertThat(layer(STATIC_ENVIRONMENT).getTiles()[19][2]).isSameAs(tile);
        assertThat(layer(STATIC_ENVIRONMENT).getTiles()[14][2]).isSameAs(NULL_TILE);
        assertThat(tab.getSpawnPoint()).isEqualTo(of(3, 18));
        assertThat(tab.getRenderingStarter()).isEqualTo(of(0, 5));
        assertThat(tab.getLevelCompletionCard()).isEqualTo(of(5, 17));
        assertThat(portal.getCoordinates()).isEqualTo(of(7, 19));
    }

    @Test
    @DisplayName("Adding columns keeps every position and leaves the new columns empty")
    void addingColumnsKeepsPositions() {
        // Prepare
        tab.newScene(15, 16);
        place(STATIC_ENVIRONMENT, of(15, 14));
        tab.setSpawnPoint(of(3, 13));

        // Execute
        tab.resize(30, 15);

        // Verify
        assertThat(layer(STATIC_ENVIRONMENT).getTiles()).hasDimensions(15, 30);
        assertThat(layer(STATIC_ENVIRONMENT).getTiles()[14][15]).isSameAs(tile);
        assertThat(layer(STATIC_ENVIRONMENT).getTiles()[14][16]).isSameAs(NULL_TILE);
        assertThat(tab.getSpawnPoint()).isEqualTo(of(3, 13));
    }

    @Test
    @DisplayName("Shrinking away an empty area cuts nothing and moves what stays up by the rows removed")
    void shrinkingAnEmptyArea() {
        // Prepare
        tab.newScene(20, 30);
        place(STATIC_ENVIRONMENT, of(1, 19));
        tab.setSpawnPoint(of(2, 18));

        // Execute
        final Map<String, List<Coordinates>> cut = tab.findContentCutBy(16, 15);
        tab.resize(16, 15);

        // Verify
        assertThat(cut).isEmpty();
        assertThat(layer(STATIC_ENVIRONMENT).getTiles()).hasDimensions(15, 16);
        assertThat(layer(STATIC_ENVIRONMENT).getTiles()[14][1]).isSameAs(tile);
        assertThat(tab.getSpawnPoint()).isEqualTo(of(2, 13));
    }

    @Test
    @DisplayName("A shrink over content lists the cut cells per layer, the card and portals under NPCs, then markers")
    void shrinkingOverContentListsTheCells() {
        // Prepare
        tab.newScene(20, 30);
        place(AIR, of(0, 2));
        place(STATIC_ENVIRONMENT, of(25, 10));
        place(STATIC_ENVIRONMENT, of(17, 3));
        place(STATIC_ENVIRONMENT, of(4, 19));
        tab.setLevelCompletionCard(of(26, 18));
        final PipePortal portal = portalAt(of(28, 19));
        tab.setSpawnPoint(of(1, 1));
        tab.setRenderingStarter(of(29, 0));

        // Execute
        final Map<String, List<Coordinates>> cut = tab.findContentCutBy(16, 15);

        // Verify
        final List<Coordinates> npcCells = new ArrayList<>(portal.cells());
        npcCells.add(of(26, 18));
        npcCells.sort(Comparator.comparingInt(Coordinates::y).thenComparingInt(Coordinates::x));
        assertThat(cut).containsExactly(
            Map.entry(AIR.getLabel(), List.of(of(0, 2))),
            Map.entry(STATIC_ENVIRONMENT.getLabel(), List.of(of(17, 3), of(25, 10))),
            Map.entry(NON_PLAYABLE_CHARACTERS.getLabel(), npcCells),
            Map.entry(SPAWN_POINT.getLabel(), List.of(of(1, 1))),
            Map.entry(RENDERING_STARTER.getLabel(), List.of(of(29, 0)))
        );
    }

    @Test
    @DisplayName("Clearing keeps the scene's id, title, description, music and size and empties everything else")
    void clearingKeepsTheIdentity() {
        // Prepare
        final MusicTrack music = MusicTrack.values()[0];
        tab.newScene(15, 16);
        tab.setSceneId("scene-1");
        tab.setSceneTitle("World 1-1");
        tab.setSceneDescription("The first level");
        tab.setBackgroundMusic(music);
        place(STATIC_ENVIRONMENT, of(2, 14));
        tab.setSpawnPoint(of(3, 13));
        tab.setRenderingStarter(of(0, 0));
        tab.setLevelCompletionCard(of(5, 12));
        portalAt(of(7, 14));

        // Execute
        tab.clearScene();

        // Verify
        assertThat(tab.getSceneId()).isEqualTo("scene-1");
        assertThat(tab.getSceneTitle()).isEqualTo("World 1-1");
        assertThat(tab.getSceneDescription()).isEqualTo("The first level");
        assertThat(tab.getBackgroundMusic()).isEqualTo(music);
        assertThat(tab.getRows()).isEqualTo(15);
        assertThat(tab.getColumns()).isEqualTo(16);
        assertThat(tab.getLayers()).allSatisfy(layer -> assertThat(
            Arrays.stream(layer.getTiles()).flatMap(Arrays::stream)
        ).as("%s tiles", layer.getType().getLabel()).containsOnly(NULL_TILE));
        assertThat(tab.getSpawnPoint()).isNull();
        assertThat(tab.getRenderingStarter()).isNull();
        assertThat(tab.getLevelCompletionCard()).isNull();
        assertThat(tab.getPipePortals()).isEmpty();
    }

    private void place(final LevelSceneLayerType type, final Coordinates cell) {
        layer(type).getTiles()[cell.y()][cell.x()] = tile;
    }

    private LevelSceneLayer layer(final LevelSceneLayerType type) {
        return tab.getLayers().stream().filter(layer -> layer.getType() == type).findFirst().orElseThrow();
    }

    private PipePortal portalAt(final Coordinates anchor) {
        final PipePortal portal = PipePortal.builder()
            .virtualObjectType(PIPE_PORTAL_VERTICAL)
            .coordinates(anchor)
            .build();
        tab.getPipePortals().add(portal);
        return portal;
    }
}
