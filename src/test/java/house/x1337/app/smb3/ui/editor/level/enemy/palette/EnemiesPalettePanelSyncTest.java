package house.x1337.app.smb3.ui.editor.level.enemy.palette;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.EnemyStampService;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTabSystem;
import house.x1337.app.smb3.ui.editor.level.tile.palette.core.ScrollableFlowPanel;
import house.x1337.app.smb3.ui.editor.level.tile.palette.core.WrapLayout;
import house.x1337.app.smb3.ui.service.PaletteSelectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.DECORATIONS_LAND;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.enemy.EnemyType.GOOMBA_NORMAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * How the Enemies palette gates its entries against the active tab: placeable on the NPCs layer, greyed but
 * clickable on any other layer of a scene that has one, and disabled outright in a scene that has none —
 * since there the click would have nothing to switch to.
 */
class EnemiesPalettePanelSyncTest {
    private final List<EnemyButton> createdButtons = new ArrayList<>();

    private LevelSceneEditorTabSystem tabSystem;
    private PaletteSelectionService paletteSelectionService;
    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        tabSystem = mock(LevelSceneEditorTabSystem.class);
        paletteSelectionService = new PaletteSelectionService();
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(PaletteSelectionService.class))
            .thenReturn(paletteSelectionService);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(EnemyTilesAssembler.class))
            .thenReturn(mock(EnemyTilesAssembler.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(ScrollableFlowPanel.class))
            .thenAnswer(invocation -> new ScrollableFlowPanel());
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(WrapLayout.class))
            .thenAnswer(invocation -> new WrapLayout());
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(EnemyButton.class), any()))
            .thenAnswer(invocation -> {
                final EnemyButton button = new EnemyButton(invocation.getArgument(1));
                createdButtons.add(button);
                return button;
            });
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Off the NPCs layer an enemy is greyed out but stays enabled, and the hint says a click switches")
    void offTheNpcLayerEntriesAreOutOfLayerButEnabled() {
        // Prepare
        final LevelSceneLayer air = layer(AIR);
        tabWith(air, air, layer(NON_PLAYABLE_CHARACTERS));
        final EnemiesPalettePanel panel = panel();

        // Execute
        final EnemyButton button = addEnemyTo(panel);

        // Verify
        assertThat(button.isOutOfActiveLayer()).isTrue();
        assertThat(button.isEnabled())
            .as("Disabling it would swallow the click that switches to the NPCs layer")
            .isTrue();
        assertThat(panel.getHintLabel().getText())
            .contains(NON_PLAYABLE_CHARACTERS.getLabel())
            .contains("switches to it");
        assertThat(panel.getHintLabel().isVisible()).isTrue();
    }

    @Test
    @DisplayName("In a scene with no NPCs layer every enemy is disabled and the hint explains why")
    void withoutAnNpcLayerEntriesAreDisabled() {
        // Prepare - a brand-new blank scene starts with Air and Land Decorations only
        final LevelSceneLayer air = layer(AIR);
        tabWith(air, air, layer(DECORATIONS_LAND));
        final EnemiesPalettePanel panel = panel();

        // Execute
        final EnemyButton button = addEnemyTo(panel);

        // Verify
        assertThat(button.isEnabled()).as("There is no layer for a click to switch to").isFalse();
        assertThat(button.isOutOfActiveLayer())
            .as("The out-of-layer look promises a switch that cannot happen here")
            .isFalse();
        assertThat(panel.getHintLabel().getText())
            .contains("has no")
            .contains(NON_PLAYABLE_CHARACTERS.getLabel());
    }

    @Test
    @DisplayName("On the NPCs layer an enemy is plainly placeable and the hint is out of the way")
    void onTheNpcLayerEntriesArePlaceable() {
        // Prepare
        final LevelSceneLayer npc = layer(NON_PLAYABLE_CHARACTERS);
        tabWith(npc, layer(AIR), npc);
        final EnemiesPalettePanel panel = panel();

        // Execute
        final EnemyButton button = addEnemyTo(panel);

        // Verify
        assertThat(button.isEnabled()).isTrue();
        assertThat(button.isOutOfActiveLayer()).isFalse();
        assertThat(panel.getHintLabel().isVisible()).isFalse();
    }

    @Test
    @DisplayName("Moving the active layer away from NPCs releases an armed enemy")
    void leavingTheNpcLayerReleasesTheArmedEnemy() {
        // Prepare
        final LevelSceneLayer npc = layer(NON_PLAYABLE_CHARACTERS);
        final LevelSceneLayer air = layer(AIR);
        final LevelSceneEditorTab tab = tabWith(npc, air, npc);
        final EnemiesPalettePanel panel = panel();
        final EnemyButton button = addEnemyTo(panel);
        paletteSelectionService.select(button);
        when(tab.getActiveLayer()).thenReturn(air);

        // Execute - as ActiveLayerMenu.activate does after a menu pick or a tile click
        panel.syncEnabledState();

        // Verify
        assertThat(paletteSelectionService.hasSelection()).isFalse();
        assertThat(button.isSelected()).isFalse();
        assertThat(button.isOutOfActiveLayer()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** The panel without its {@code @PostConstruct} build-out, which would query the database. */
    private EnemiesPalettePanel panel() {
        return new EnemiesPalettePanel(
            mock(EnemyStampService.class),
            paletteSelectionService,
            tabSystem,
            mock(EnemyTilesAssembler.class)
        );
    }

    /** Adds an enemy the way the palette does, through {@code EnemyButton.fromEnemy}, and returns its entry. */
    private EnemyButton addEnemyTo(final EnemiesPalettePanel panel) {
        panel.addEnemy(enemy());
        return createdButtons.getLast();
    }

    private LevelSceneEditorTab tabWith(final LevelSceneLayer activeLayer, final LevelSceneLayer... layers) {
        final LevelSceneEditorTab tab = mock(LevelSceneEditorTab.class);
        when(tab.getLayers()).thenReturn(List.of(layers));
        when(tab.getActiveLayer()).thenReturn(activeLayer);
        when(tabSystem.getActiveTab()).thenReturn(tab);
        return tab;
    }

    private static LevelSceneLayer layer(final LevelSceneLayerType type) {
        return LevelSceneLayer.builder().type(type).visible(true).build();
    }

    private static EnemyStamp enemy() {
        return EnemyStamp
            .builder()
            .id("goomba")
            .enemyType(GOOMBA_NORMAL)
            .tiles(new Tile[][] {{NULL_TILE}})
            .build();
    }
}
