package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.enumeration.VirtualObjectCategory;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyLevelSceneGridStamper;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
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

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static house.x1337.app.smb3.enumeration.PipePortalType.OUT;
import static house.x1337.app.smb3.enumeration.TileType.ENEMY_PART;
import static house.x1337.app.smb3.enumeration.VirtualObjectCategory.PIPE_PORTAL;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_HORIZONTAL;
import static house.x1337.app.smb3.game.level.pipe.PipePortalPlacementRules.NOT_OVER_PIPE_TERMINATION;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.awt.Color.YELLOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Placing, moving and removing the scene's level completion card from the Virtual Objects palette.
 *
 * <p>The scene is 6 rows by 8 columns with no pipes at all: the card needs none.
 */
class LevelCompletionCardPlacementTest {
    private static final int ROWS = 6;
    private static final int COLUMNS = 8;
    private static final VirtualObjectType CARD = VirtualObjectType.LEVEL_COMPLETION_CARD;

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
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("The card is its own kind of virtual object, one yellow tile, and the portals stay pipe portals")
    void theCardIsItsOwnKind() {
        // Execute & Verify
        assertThat(CARD.getCategory()).isEqualTo(VirtualObjectCategory.CARD);
        assertThat(CARD.getColour()).isEqualTo(YELLOW);
        assertThat(CARD.getRows()).isEqualTo(1);
        assertThat(CARD.getColumns()).isEqualTo(1);
        assertThat(CARD.getLabel()).isEqualTo("Level Completion Card");
        assertThat(PIPE_PORTAL_HORIZONTAL.getCategory()).isEqualTo(PIPE_PORTAL);
        assertThat(VirtualObjectType.PIPE_PORTAL_VERTICAL.getCategory()).isEqualTo(PIPE_PORTAL);
    }

    @Test
    @DisplayName("A click on the NPC layer puts the card on that cell, with no prompt and no pipe rules")
    void clickPlacesTheCard() {
        // Prepare
        arm(CARD);

        // Execute
        tab.paintTile(of(5, 2));

        // Verify
        assertThat(tab.hasLevelCompletionCard()).isTrue();
        assertThat(tab.getLevelCompletionCard()).isEqualTo(of(5, 2));
        assertThat(tab.getPipePortals()).as("The card is not a pipe portal").isEmpty();
        assertThat(layers.get(NON_PLAYABLE_CHARACTERS).getTiles()[2][5])
            .as("The card is not a tile")
            .isSameAs(NULL_TILE);
        verifyNoInteractions(prompt);
    }

    @Test
    @DisplayName("Placing the card again moves it, so a scene only ever has one")
    void placingAgainMovesTheCard() {
        // Prepare
        arm(CARD);
        tab.paintTile(of(5, 2));

        // Execute
        tab.paintTile(of(1, 4));

        // Verify
        assertThat(tab.getLevelCompletionCard()).isEqualTo(of(1, 4));
    }

    @Test
    @DisplayName("Nothing is placed while another layer is active")
    void refusedOffTheNpcLayer() {
        // Prepare
        arm(CARD);
        tab.setActiveLayer(layers.get(AIR));

        // Execute
        tab.paintTile(of(5, 2));

        // Verify
        assertThat(tab.hasLevelCompletionCard()).isFalse();
        assertThat(layers.get(AIR).getTiles()[2][5]).isSameAs(NULL_TILE);
    }

    @Test
    @DisplayName("Right-clicking the card's cell removes the card and leaves the NPC tile under it alone")
    void rightClickRemovesTheCard() {
        // Prepare
        arm(CARD);
        tab.paintTile(of(3, 3));
        final Tile enemyPart = Tile.builder().id(500).type(ENEMY_PART).build();
        layers.get(NON_PLAYABLE_CHARACTERS).getTiles()[3][3] = enemyPart;

        // Execute
        tab.eraseTile(of(3, 3));

        // Verify
        assertThat(tab.hasLevelCompletionCard()).isFalse();
        assertThat(tab.getLevelCompletionCard()).isNull();
        assertThat(layers.get(NON_PLAYABLE_CHARACTERS).getTiles()[3][3])
            .as("The card is removed instead of the tile under it")
            .isSameAs(enemyPart);

        // Execute - the cell has no card any more, so it erases as before
        tab.eraseTile(of(3, 3));

        // Verify
        assertThat(layers.get(NON_PLAYABLE_CHARACTERS).getTiles()[3][3]).isSameAs(NULL_TILE);
    }

    @Test
    @DisplayName("Right-clicking another cell leaves the card in place")
    void rightClickElsewhereKeepsTheCard() {
        // Prepare
        arm(CARD);
        tab.paintTile(of(3, 3));

        // Execute
        tab.eraseTile(of(4, 3));

        // Verify
        assertThat(tab.getLevelCompletionCard()).isEqualTo(of(3, 3));
    }

    @Test
    @DisplayName("Right-clicking the card's cell with another layer active keeps the card and erases that layer")
    void rightClickOffTheNpcLayerKeepsTheCard() {
        // Prepare
        arm(CARD);
        tab.paintTile(of(3, 3));
        final Tile sky = Tile.builder().id(600).build();
        layers.get(AIR).getTiles()[3][3] = sky;
        tab.setActiveLayer(layers.get(AIR));

        // Execute
        tab.eraseTile(of(3, 3));

        // Verify
        assertThat(tab.hasLevelCompletionCard()).isTrue();
        assertThat(layers.get(AIR).getTiles()[3][3]).isSameAs(NULL_TILE);
    }

    @Test
    @DisplayName("On a cell holding both, right-click removes the card first, then the pipe portal")
    void cardIsRemovedBeforeAPortalOnTheSameCell() {
        // Prepare
        final PipePortal portal = PipePortal.builder()
            .type(OUT)
            .virtualObjectType(PIPE_PORTAL_HORIZONTAL)
            .coordinates(of(2, 3))
            .build();
        tab.getPipePortals().add(portal);
        arm(CARD);
        tab.paintTile(of(3, 3));

        // Execute
        tab.eraseTile(of(3, 3));

        // Verify
        assertThat(tab.hasLevelCompletionCard()).isFalse();
        assertThat(tab.getPipePortals()).containsExactly(portal);

        // Execute
        tab.eraseTile(of(3, 3));

        // Verify
        assertThat(tab.getPipePortals()).isEmpty();
    }

    @Test
    @DisplayName("A pipe portal still goes through the pipe rules, which the card skips")
    void pipePortalStillFollowsThePipeRules() {
        // Prepare
        arm(PIPE_PORTAL_HORIZONTAL);

        // Execute - no pipe anywhere in this scene
        tab.paintTile(of(2, 3));

        // Verify
        verify(prompt).warn(tab, NOT_OVER_PIPE_TERMINATION);
        verify(prompt, never()).ask(any(), any(), any());
        assertThat(tab.getPipePortals()).isEmpty();
        assertThat(tab.hasLevelCompletionCard()).isFalse();
    }

    @Test
    @DisplayName("Arming the card places on a press only, as for every virtual object")
    void armedCardReportsPressOnlyPlacement() {
        // Prepare
        arm(CARD);

        // Execute & Verify
        assertThat(tab.isPlacingVirtualObject()).isTrue();
        assertThat(tab.hasPlaceableSelection()).isTrue();
    }

    @Test
    @DisplayName("Clearing the scene removes the card")
    void clearingTheSceneRemovesTheCard() {
        // Prepare
        arm(CARD);
        tab.paintTile(of(3, 3));

        // Execute
        tab.clearScene();

        // Verify
        assertThat(tab.hasLevelCompletionCard()).isFalse();
    }

    private void arm(final VirtualObjectType type) {
        paletteSelectionService.select(VirtualObjectButton.fromType(type));
    }

    private static Tile[][] empty() {
        final Tile[][] tiles = new Tile[ROWS][COLUMNS];
        for (final Tile[] row : tiles) {
            Arrays.fill(row, NULL_TILE);
        }
        return tiles;
    }
}
