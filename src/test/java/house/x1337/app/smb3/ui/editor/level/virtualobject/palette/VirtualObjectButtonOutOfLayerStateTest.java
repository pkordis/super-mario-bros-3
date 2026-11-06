package house.x1337.app.smb3.ui.editor.level.virtualobject.palette;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.ui.editor.level.menu.scene.level.ActiveLayerMenu;
import house.x1337.app.smb3.ui.service.PaletteSelectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import javax.swing.border.LineBorder;
import java.awt.image.BufferedImage;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_HORIZONTAL;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_VERTICAL;
import static house.x1337.app.smb3.ui.editor.level.virtualobject.palette.VirtualObjectButton.BORDER_THICKNESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * A Virtual Objects palette entry: a stamp of the object's real footprint, and the NPC-layer gating every placeable
 * palette entry shares.
 */
class VirtualObjectButtonOutOfLayerStateTest {
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
            .when(() -> StaticBeanFactory.getBean(eq(VirtualObjectButton.class), any()))
            .thenAnswer(invocation -> new VirtualObjectButton(invocation.getArgument(1)));
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A virtual object is placed on the NPC layer")
    void reportsTheNpcLayerAsItsOwner() {
        // Execute & Verify
        assertThat(new VirtualObjectButton(PIPE_PORTAL_HORIZONTAL).getOwningLayer()).isEqualTo(NON_PLAYABLE_CHARACTERS);
    }

    @Test
    @DisplayName("The stamp is one 64 px cell per tile of the footprint")
    void thumbnailHasTheFootprintsSize() {
        // Execute
        final BufferedImage horizontal = VirtualObjectButton.toThumbnail(PIPE_PORTAL_HORIZONTAL);
        final BufferedImage vertical = VirtualObjectButton.toThumbnail(PIPE_PORTAL_VERTICAL);

        // Verify
        assertThat(TILE_SIZE).as("16 x 16 at scale 4").isEqualTo(64);
        assertThat(horizontal.getWidth()).isEqualTo(2 * TILE_SIZE);
        assertThat(horizontal.getHeight()).isEqualTo(TILE_SIZE);
        assertThat(vertical.getWidth()).isEqualTo(TILE_SIZE);
        assertThat(vertical.getHeight()).isEqualTo(2 * TILE_SIZE);
    }

    @Test
    @DisplayName("The stamp has a bold border in the type's colour and a transparent inside")
    void thumbnailHasABoldBorderAndATransparentInside() {
        // Prepare
        final VirtualObjectType type = PIPE_PORTAL_HORIZONTAL;
        final int colour = type.getColour().getRGB();

        // Execute
        final BufferedImage stamp = VirtualObjectButton.toThumbnail(type);

        // Verify
        final int width = stamp.getWidth();
        final int height = stamp.getHeight();
        assertThat(stamp.getRGB(0, 0)).as("Top-left corner").isEqualTo(colour);
        assertThat(stamp.getRGB(width - 1, height - 1)).as("Bottom-right corner").isEqualTo(colour);
        assertThat(stamp.getRGB(BORDER_THICKNESS - 1, height / 2))
            .as("Inner edge of the left border")
            .isEqualTo(colour);
        assertThat(stamp.getRGB(width / 4, BORDER_THICKNESS - 1)).as("Inner edge of the top border").isEqualTo(colour);
        assertThat(stamp.getRGB(BORDER_THICKNESS, height / 2) >>> 24).as("Just inside the border").isZero();
        assertThat(stamp.getRGB(TILE_SIZE / 2, TILE_SIZE / 2) >>> 24).as("Centre of the anchor cell").isZero();
        assertThat(stamp.getRGB(TILE_SIZE + TILE_SIZE / 2, TILE_SIZE / 2) >>> 24)
            .as("Centre of the second cell")
            .isZero();
    }

    @Test
    @DisplayName("The tooltip names the object and its size")
    void tooltipNamesTheObject() {
        // Execute
        final VirtualObjectButton button = VirtualObjectButton.fromType(PIPE_PORTAL_VERTICAL);

        // Verify
        assertThat(button.getToolTipText())
            .contains(PIPE_PORTAL_VERTICAL.getLabel())
            .contains("1 x 2 tiles");
        assertThat(button.getIcon()).isNotNull();
    }

    @Test
    @DisplayName("Clicking an out-of-layer stamp switches to the NPC layer, then arms it")
    void clickingSwitchesTheLayerThenArms() {
        // Prepare
        final VirtualObjectButton button = new VirtualObjectButton(PIPE_PORTAL_HORIZONTAL);
        button.markOutOfActiveLayer(true);
        when(activeLayerMenu.activate(NON_PLAYABLE_CHARACTERS)).thenAnswer(invocation -> {
            button.markOutOfActiveLayer(false);
            return true;
        });

        // Execute
        button.doClick(0);

        // Verify
        final InOrder order = inOrder(activeLayerMenu, paletteSelectionService);
        order.verify(activeLayerMenu).activate(NON_PLAYABLE_CHARACTERS);
        order.verify(paletteSelectionService).select(button);
        assertThat(paletteSelectionService.getSelectedVirtualObject()).isEqualTo(PIPE_PORTAL_HORIZONTAL);
        assertThat(button.getBorder()).isInstanceOf(LineBorder.class);
    }
}
