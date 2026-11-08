package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyLevelSceneGridStamper;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import house.x1337.app.smb3.ui.editor.level.virtualobject.PipePortalDetails;
import house.x1337.app.smb3.ui.editor.level.virtualobject.PipePortalPrompt;
import house.x1337.app.smb3.ui.editor.level.virtualobject.palette.VirtualObjectButton;
import house.x1337.app.smb3.ui.service.PaletteSelectionService;
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
import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static house.x1337.app.smb3.enumeration.PipePortalType.IN_AND_OUT;
import static house.x1337.app.smb3.enumeration.PipePortalType.OUT;
import static house.x1337.app.smb3.enumeration.TileType.ENEMY_PART;
import static house.x1337.app.smb3.enumeration.TileType.PIPE_BODY_PART;
import static house.x1337.app.smb3.enumeration.TileType.PIPE_TERMINATION_PART;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_HORIZONTAL;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_VERTICAL;
import static house.x1337.app.smb3.game.level.pipe.PipePortalPlacementRules.NOT_OVER_PIPE_TERMINATION;
import static house.x1337.app.smb3.game.level.pipe.PipePortalPlacementRules.OFF_GRID;
import static house.x1337.app.smb3.game.level.pipe.PipePortalPlacementRules.OVERLAPS_ANOTHER_PORTAL;
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
 * Placing and removing pipe portals on the level grid, with the Place Pipe Portal window and the warning stubbed.
 *
 * <p>The scene is 6 rows by 8 columns. Row 3 holds an upright pipe's lip at columns 2 and 3, and a side pipe's lip is
 * stacked at column 6, rows 2 and 3.
 */
class PipePortalPlacementTest {
    private static final int ROWS = 6;
    private static final int COLUMNS = 8;

    private final Map<LevelSceneLayerType, LevelSceneLayer> layers = new EnumMap<>(LevelSceneLayerType.class);

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private PaletteSelectionService paletteSelectionService;
    private PipePortalPrompt prompt;
    private LevelSceneEditorTab tab;

    @BeforeEach
    void prepare() {
        paletteSelectionService = new PaletteSelectionService();
        prompt = mock(PipePortalPrompt.class);
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(LevelSceneEditorGrid.class), any()))
            .thenReturn(mock(LevelSceneEditorGrid.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(PaletteSelectionService.class))
            .thenReturn(paletteSelectionService);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(PipePortalPrompt.class))
            .thenReturn(prompt);
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
        for (final LevelSceneLayerType type : List.of(AIR, STATIC_ENVIRONMENT, NON_PLAYABLE_CHARACTERS)) {
            final LevelSceneLayer layer = LevelSceneLayer.builder().type(type).visible(true).tiles(empty()).build();
            layers.put(type, layer);
            ordered.add(layer);
        }
        tab.setLayers(ordered);
        tab.setActiveLayer(layers.get(NON_PLAYABLE_CHARACTERS));
        tab.setSceneId("scene-1");

        paintStatic(3, 2, PIPE_TERMINATION_PART);
        paintStatic(3, 3, PIPE_TERMINATION_PART);
        paintStatic(4, 2, PIPE_BODY_PART);
        paintStatic(2, 6, PIPE_TERMINATION_PART);
        paintStatic(3, 6, PIPE_TERMINATION_PART);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A click on a pipe lip places a portal anchored on the clicked cell, with what the window returned")
    void validClickPlacesAPortal() {
        // Prepare
        arm(PIPE_PORTAL_HORIZONTAL);
        when(prompt.ask(tab, PIPE_PORTAL_HORIZONTAL, of(2, 3)))
            .thenReturn(Optional.of(new PipePortalDetails(IN_AND_OUT, "To the bonus room")));

        // Execute
        tab.paintTile(of(2, 3));

        // Verify
        assertThat(tab.getPipePortals()).hasSize(1);
        final PipePortal portal = tab.getPipePortals().getFirst();
        assertThat(portal.getVirtualObjectType()).isEqualTo(PIPE_PORTAL_HORIZONTAL);
        assertThat(portal.getCoordinates().y()).as("The clicked cell is the lowest row").isEqualTo(3);
        assertThat(portal.getCoordinates().x()).as("The clicked cell is the leftmost column").isEqualTo(2);
        assertThat(portal.getType()).isEqualTo(IN_AND_OUT);
        assertThat(portal.getDescription()).isEqualTo("To the bonus room");
        assertThat(portal.getLevelSceneId()).isEqualTo("scene-1");
        verify(prompt, never()).warn(any(), any());
    }

    @Test
    @DisplayName("A vertical portal is anchored on the lower of its two cells")
    void verticalPortalIsAnchoredOnItsLowerCell() {
        // Prepare
        arm(PIPE_PORTAL_VERTICAL);
        when(prompt.ask(tab, PIPE_PORTAL_VERTICAL, of(6, 3)))
            .thenReturn(Optional.of(new PipePortalDetails(OUT, null)));

        // Execute
        tab.paintTile(of(6, 3));

        // Verify
        final PipePortal portal = tab.getPipePortals().getFirst();
        assertThat(portal.occupies(of(6, 2))).isTrue();
        assertThat(portal.occupies(of(6, 3))).isTrue();
        assertThat(portal.getDescription()).isNull();
    }

    @Test
    @DisplayName("Nothing is placed, and nothing is asked, while the NPC layer is not active")
    void refusedOffTheNpcLayer() {
        // Prepare
        arm(PIPE_PORTAL_HORIZONTAL);
        tab.setActiveLayer(layers.get(AIR));

        // Execute
        tab.paintTile(of(2, 3));

        // Verify
        assertThat(tab.getPipePortals()).isEmpty();
        verify(prompt, never()).ask(any(), any(), any());
        verify(prompt, never()).warn(any(), any());
    }

    @Test
    @DisplayName("A portal that would leave the grid is refused with a warning")
    void refusedOffTheGrid() {
        // Prepare - a vertical portal anchored on row 0 pokes above the top
        arm(PIPE_PORTAL_VERTICAL);

        // Execute
        tab.paintTile(of(6, 0));

        // Verify
        assertRefusedWith(OFF_GRID);
    }

    @Test
    @DisplayName("A portal over anything but pipe termination tiles is refused with a warning")
    void refusedOffPipeTermination() {
        // Prepare - the upper cell is a lip tile, but the anchor below it is pipe body
        arm(PIPE_PORTAL_VERTICAL);

        // Execute
        tab.paintTile(of(2, 4));

        // Verify
        assertRefusedWith(NOT_OVER_PIPE_TERMINATION);
    }

    @Test
    @DisplayName("A portal over another one is refused with a warning")
    void refusedOverAnotherPortal() {
        // Prepare
        tab.getPipePortals().add(portal(PIPE_PORTAL_VERTICAL, 3, 6));
        paintStatic(3, 5, PIPE_TERMINATION_PART);
        arm(PIPE_PORTAL_HORIZONTAL);

        // Execute
        tab.paintTile(of(5, 3));

        // Verify
        verify(prompt).warn(tab, OVERLAPS_ANOTHER_PORTAL);
        verify(prompt, never()).ask(any(), any(), any());
        assertThat(tab.getPipePortals()).hasSize(1);
    }

    @Test
    @DisplayName("Cancelling the window places nothing")
    void cancellingPlacesNothing() {
        // Prepare
        arm(PIPE_PORTAL_HORIZONTAL);
        when(prompt.ask(tab, PIPE_PORTAL_HORIZONTAL, of(2, 3))).thenReturn(Optional.empty());

        // Execute
        tab.paintTile(of(2, 3));

        // Verify
        assertThat(tab.getPipePortals()).isEmpty();
    }

    @Test
    @DisplayName("Right-clicking a portal's cell removes only that portal and leaves the NPC tiles alone")
    void rightClickRemovesOnlyThePortalUnderTheCursor() {
        // Prepare
        final PipePortal horizontal = portal(PIPE_PORTAL_HORIZONTAL, 3, 2);
        final PipePortal vertical = portal(PIPE_PORTAL_VERTICAL, 3, 6);
        tab.getPipePortals().addAll(List.of(horizontal, vertical));
        final Tile enemyPart = Tile.builder().id(500).type(ENEMY_PART).build();
        layers.get(NON_PLAYABLE_CHARACTERS).getTiles()[3][3] = enemyPart;

        // Execute - the horizontal portal's right cell
        tab.eraseTile(of(3, 3));

        // Verify
        assertThat(tab.getPipePortals()).containsExactly(vertical);
        assertThat(layers.get(NON_PLAYABLE_CHARACTERS).getTiles()[3][3])
            .as("The portal is removed instead of the tile under it")
            .isSameAs(enemyPart);

        // Execute - a cell with no portal erases as before
        tab.eraseTile(of(3, 3));

        // Verify
        assertThat(layers.get(NON_PLAYABLE_CHARACTERS).getTiles()[3][3]).isSameAs(NULL_TILE);
    }

    @Test
    @DisplayName("While a virtual object is armed the grid places on a press only, never on a drag")
    void armedVirtualObjectReportsPressOnlyPlacement() {
        // Prepare
        arm(PIPE_PORTAL_HORIZONTAL);

        // Execute & Verify
        assertThat(tab.isPlacingVirtualObject()).isTrue();
        assertThat(tab.hasPlaceableSelection()).isTrue();
        paletteSelectionService.clearSelection();
        assertThat(tab.isPlacingVirtualObject()).isFalse();
    }

    private void assertRefusedWith(final String reason) {
        verify(prompt).warn(tab, reason);
        verify(prompt, never()).ask(any(), any(), any());
        assertThat(tab.getPipePortals()).isEmpty();
    }

    private void arm(final VirtualObjectType type) {
        paletteSelectionService.select(VirtualObjectButton.fromType(type));
    }

    private void paintStatic(final int row, final int column, final TileType type) {
        layers.get(STATIC_ENVIRONMENT).getTiles()[row][column] = Tile.builder()
            .id(100 + row * COLUMNS + column)
            .type(type)
            .build();
    }

    private static PipePortal portal(final VirtualObjectType type, final int row, final int column) {
        return PipePortal.builder()
            .type(OUT)
            .virtualObjectType(type)
            .coordinates(of(column, row))
            .build();
    }

    private static Tile[][] empty() {
        final Tile[][] tiles = new Tile[ROWS][COLUMNS];
        for (final Tile[] row : tiles) {
            Arrays.fill(row, NULL_TILE);
        }
        return tiles;
    }
}
