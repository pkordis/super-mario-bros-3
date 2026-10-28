package house.x1337.app.smb3.ui.editor.level.enemy.palette;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.level.menu.scene.level.ActiveLayerMenu;
import house.x1337.app.smb3.ui.service.PaletteSelectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import javax.swing.border.LineBorder;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.TileType.ENEMY_PART;
import static house.x1337.app.smb3.enumeration.enemy.EnemyType.GOOMBA_NORMAL;
import static java.awt.Color.GRAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The out-of-layer state an Enemies palette entry wears while a layer other than the NPCs one is active,
 * and the click that switches to it — the enemy-side twin of {@code TileButtonOutOfLayerStateTest}.
 *
 * <p>The state exists instead of {@code setEnabled(false)} for one reason: a disabled Swing button gets no
 * mouse events, and clicking one of these has to keep working — it is how the user switches to the NPCs
 * layer without a detour through Level Scene &gt; Active Layer.
 */
class EnemyButtonOutOfLayerStateTest {
    private PaletteSelectionService paletteSelectionService;
    private ActiveLayerMenu activeLayerMenu;
    private MockedStatic<StaticBeanFactory> staticBeanFactory;

    @BeforeEach
    void prepare() {
        paletteSelectionService = spy(new PaletteSelectionService());
        activeLayerMenu = mock(ActiveLayerMenu.class);
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(PaletteSelectionService.class))
            .thenReturn(paletteSelectionService);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(ActiveLayerMenu.class))
            .thenReturn(activeLayerMenu);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(EnemyTilesAssembler.class))
            .thenReturn(mock(EnemyTilesAssembler.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(EnemyButton.class), any()))
            .thenAnswer(invocation -> new EnemyButton(invocation.getArgument(1)));
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("An enemy belongs on the layer its parts are classified for, which is the NPCs layer")
    void reportsTheNpcLayerAsItsOwner() {
        // Prepare
        final EnemyButton button = button();

        // Execute & Verify
        assertThat(button.getOwningLayer())
            .as("The binding comes from the enemy part tile type, not a second constant")
            .isEqualTo(ENEMY_PART.getLevelSceneLayerOwningType())
            .isEqualTo(NON_PLAYABLE_CHARACTERS);
    }

    @Test
    @DisplayName("Marking an enemy out of layer stays clickable, which is the whole point")
    void anOutOfLayerButtonRemainsClickable() {
        // Prepare
        final EnemyButton button = button();

        // Execute
        button.markOutOfActiveLayer(true);

        // Verify
        assertThat(button.isOutOfActiveLayer()).isTrue();
        assertThat(button.isEnabled())
            .as("A disabled button would swallow the click that switches layers")
            .isTrue();
        assertThat(button.getBorder())
            .as("The dashed border is what reads as unavailable")
            .isNotInstanceOf(LineBorder.class);
    }

    @Test
    @DisplayName("The state clears again when the NPCs layer becomes the active one")
    void theStateClearsWhenTheLayerBecomesActive() {
        // Prepare
        final EnemyButton button = button();
        button.markOutOfActiveLayer(true);

        // Execute
        button.markOutOfActiveLayer(false);

        // Verify
        assertThat(button.isOutOfActiveLayer()).isFalse();
        assertThat(button.getBorder()).isInstanceOf(LineBorder.class);
    }

    @Test
    @DisplayName("An out-of-layer enemy names the NPCs layer and says a click switches to it")
    void tooltipNamesTheNpcLayer() {
        // Prepare
        final EnemyButton button = EnemyButton.fromEnemy(enemy());

        // Execute
        button.markOutOfActiveLayer(true);

        // Verify
        assertThat(button.getToolTipText())
            .contains(GOOMBA_NORMAL.getLabel())
            .contains(NON_PLAYABLE_CHARACTERS.getLabel())
            .contains("Click to switch");

        // Execute - and back
        button.markOutOfActiveLayer(false);

        // Verify
        assertThat(button.getToolTipText())
            .startsWith(GOOMBA_NORMAL.getLabel())
            .doesNotContain("Click to switch");
    }

    @Test
    @DisplayName("Selection and the out-of-layer look share one border rather than overwriting each other")
    void selectionAndOutOfLayerComposeIntoOneBorder() {
        // Prepare
        final EnemyButton button = button();
        button.markOutOfActiveLayer(true);

        // Execute
        button.markSelected(true);

        // Verify - still dashed, so marking it selected did not wipe the out-of-layer look
        assertThat(button.getBorder()).isNotInstanceOf(LineBorder.class);

        // Execute - the layer becomes active while it is selected
        button.markOutOfActiveLayer(false);

        // Verify - and the selected colour survives the out-of-layer state clearing
        assertThat(((LineBorder) button.getBorder()).getLineColor()).isEqualTo(GRAY.brighter());
    }

    @Test
    @DisplayName("Clicking an out-of-layer enemy switches to the NPCs layer, then arms the enemy")
    void clickingSwitchesTheLayerThenArms() {
        // Prepare
        final EnemyButton button = button();
        button.markOutOfActiveLayer(true);
        when(activeLayerMenu.activate(NON_PLAYABLE_CHARACTERS)).thenAnswer(invocation -> {
            // What the palette re-sync does once the NPCs layer is active
            button.markOutOfActiveLayer(false);
            return true;
        });

        // Execute
        button.doClick(0);

        // Verify - activation first, since its palette re-sync would release an enemy armed before it
        final InOrder order = inOrder(activeLayerMenu, paletteSelectionService);
        order.verify(activeLayerMenu).activate(NON_PLAYABLE_CHARACTERS);
        order.verify(paletteSelectionService).select(button);
        assertThat(paletteSelectionService.getSelectedButton()).isSameAs(button);
        assertThat(button.isSelected()).isTrue();
        assertThat(((LineBorder) button.getBorder()).getLineColor())
            .as("The selected border shows the enemy is armed")
            .isEqualTo(GRAY.brighter());
    }

    @Test
    @DisplayName("A scene without an NPCs layer leaves the enemy unarmed and the button untoggled")
    void aMissingLayerArmsNothing() {
        // Prepare
        final EnemyButton button = button();
        button.markOutOfActiveLayer(true);
        when(activeLayerMenu.activate(NON_PLAYABLE_CHARACTERS)).thenReturn(false);

        // Execute
        button.doClick(0);

        // Verify
        assertThat(paletteSelectionService.hasSelection())
            .as("Arming it would let it be stamped onto whichever layer happens to be active")
            .isFalse();
        assertThat(button.isSelected()).isFalse();
    }

    @Test
    @DisplayName("Clicking an enemy while the NPCs layer is active arms it without touching the active layer")
    void anInLayerClickOnlyArms() {
        // Prepare
        final EnemyButton button = button();

        // Execute
        button.doClick(0);

        // Verify
        verify(activeLayerMenu, never()).activate(any());
        assertThat(paletteSelectionService.getSelectedButton()).isSameAs(button);
    }

    @Test
    @DisplayName("Clicking the armed enemy again clears the selection")
    void clickingTheArmedEnemyAgainClears() {
        // Prepare
        final EnemyButton button = button();
        button.doClick(0);

        // Execute
        button.doClick(0);

        // Verify
        assertThat(paletteSelectionService.hasSelection()).isFalse();
        assertThat(button.isSelected()).isFalse();
    }

    @Test
    @DisplayName("A disabled enemy ignores the click altogether")
    void aDisabledButtonIgnoresTheClick() {
        // Prepare - how the palette leaves its entries in a scene with no NPCs layer
        final EnemyButton button = button();
        button.setEnabled(false);

        // Execute
        button.doClick(0);

        // Verify
        verify(activeLayerMenu, never()).activate(any());
        assertThat(paletteSelectionService.hasSelection()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** A bare button, bypassing {@code fromEnemy} so no icon is assembled. */
    private static EnemyButton button() {
        return new EnemyButton(enemy());
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
