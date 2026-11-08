package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.enumeration.PipePortalType;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.model.ui.tile.Tile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static house.x1337.app.smb3.enumeration.PipePortalType.IN;
import static house.x1337.app.smb3.enumeration.PipePortalType.IN_AND_OUT;
import static house.x1337.app.smb3.enumeration.PipePortalType.OUT;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_HORIZONTAL;
import static house.x1337.app.smb3.enumeration.VirtualObjectType.PIPE_PORTAL_VERTICAL;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorGrid.PIPE_PORTAL_LABEL_FONT;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LevelSceneEditorGridPipePortalLabelTest {
    private static final int GRID_SIZE = 2;

    /** Inside the 4 px border stroke (drawn 2 px in) plus antialiasing slack: only the label can paint here. */
    private static final int BORDER_CLEARANCE = 8;

    /** The widest a label may be: a one-tile-wide portal, less its 4 px border on both sides. */
    private static final int ONE_TILE_INNER_WIDTH = TILE_SIZE - 8;

    private final LevelSceneEditorFontCapabilities fontCapabilities = new LevelSceneEditorFontCapabilities() {};

    @Test
    @DisplayName("The baseline centres the text horizontally and vertically in a tall area")
    void baselineCentresTextInATallArea() {
        // Prepare
        final FontMetrics metrics = metrics();
        final Rectangle area = new Rectangle(64, 0, 64, 128);

        // Execute
        final Point baseline = fontCapabilities.centredBaseline(metrics, "OUT", area);

        // Verify
        assertThat(baseline.x).isEqualTo(64 + (64 - metrics.stringWidth("OUT")) / 2);
        assertThat(baseline.y)
            .isEqualTo((128 - (metrics.getAscent() + metrics.getDescent())) / 2 + metrics.getAscent());
    }

    @Test
    @DisplayName("The baseline centres the text horizontally and vertically in a wide area")
    void baselineCentresTextInAWideArea() {
        // Prepare
        final FontMetrics metrics = metrics();
        final Rectangle area = new Rectangle(0, 64, 128, 64);

        // Execute
        final Point baseline = fontCapabilities.centredBaseline(metrics, "I/O", area);

        // Verify
        assertThat(baseline.x).isEqualTo((128 - metrics.stringWidth("I/O")) / 2);
        assertThat(baseline.y)
            .isEqualTo(64 + (64 - (metrics.getAscent() + metrics.getDescent())) / 2 + metrics.getAscent());
    }

    @ParameterizedTest
    @EnumSource(PipePortalType.class)
    @DisplayName("Every short label fits inside a one-tile-wide portal")
    void everyShortLabelFitsInsideAVerticalPortal(final PipePortalType type) {
        // Execute
        final int width = metrics().stringWidth(type.getShortLabel());

        // Verify
        assertThat(width).as("Width of %s", type.getShortLabel()).isLessThanOrEqualTo(ONE_TILE_INNER_WIDTH);
    }

    @Test
    @DisplayName("A horizontal portal's label is painted centred inside its border")
    void horizontalPortalLabelIsCentred() {
        // Prepare
        final PipePortal portal = portal(PIPE_PORTAL_HORIZONTAL, IN);
        final Rectangle area = new Rectangle(0, TILE_SIZE, 2 * TILE_SIZE, TILE_SIZE);

        // Execute
        final BufferedImage image = paint(true, portal);

        // Verify
        assertLabelCentredIn(image, area);
    }

    @Test
    @DisplayName("A vertical portal's label is painted centred inside its border")
    void verticalPortalLabelIsCentred() {
        // Prepare
        final PipePortal portal = portal(PIPE_PORTAL_VERTICAL, IN_AND_OUT);
        final Rectangle area = new Rectangle(0, 0, TILE_SIZE, 2 * TILE_SIZE);

        // Execute
        final BufferedImage image = paint(true, portal);

        // Verify
        assertLabelCentredIn(image, area);
    }

    @Test
    @DisplayName("A portal without a type shows only its border")
    void untypedPortalShowsOnlyItsBorder() {
        // Prepare
        final PipePortal portal = portal(PIPE_PORTAL_HORIZONTAL, null);
        final Rectangle area = new Rectangle(0, TILE_SIZE, 2 * TILE_SIZE, TILE_SIZE);

        // Execute
        final BufferedImage image = paint(true, portal);

        // Verify
        assertThat(labelBounds(image, inner(area))).as("Label pixels inside the border").isNull();
        assertThat(isRed(image.getRGB(area.x + 3, area.y + area.height / 2))).as("The border").isTrue();
    }

    @Test
    @DisplayName("Hiding the NPC layer hides the labels along with the borders")
    void hiddenNpcLayerHidesTheLabels() {
        // Prepare
        final PipePortal portal = portal(PIPE_PORTAL_HORIZONTAL, OUT);

        // Execute
        final BufferedImage image = paint(false, portal);

        // Verify
        assertThat(labelBounds(image, new Rectangle(0, 0, image.getWidth(), image.getHeight())))
            .as("Any portal pixel")
            .isNull();
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private static PipePortal portal(final VirtualObjectType virtualObjectType, final PipePortalType type) {
        // Anchored on the lowest, leftmost cell of a 2 x 2 grid
        return PipePortal.builder()
            .virtualObjectType(virtualObjectType)
            .type(type)
            .coordinates(of(0, 1))
            .build();
    }

    private static BufferedImage paint(final boolean npcLayerVisible, final PipePortal portal) {
        final LevelSceneEditorTab tab = mock(LevelSceneEditorTab.class);
        when(tab.getRows()).thenReturn(GRID_SIZE);
        when(tab.getColumns()).thenReturn(GRID_SIZE);
        when(tab.getRenderingStarter()).thenReturn(null);
        when(tab.getSpawnPoint()).thenReturn(null);
        when(tab.getLayers()).thenReturn(List.of(layer(AIR, true), layer(NON_PLAYABLE_CHARACTERS, npcLayerVisible)));
        when(tab.getPipePortals()).thenReturn(List.of(portal));

        final BufferedImage image = new BufferedImage(GRID_SIZE * TILE_SIZE, GRID_SIZE * TILE_SIZE, TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        new LevelSceneEditorGrid(tab).paintComponent(graphics);
        graphics.dispose();
        return image;
    }

    private static LevelSceneLayer layer(final LevelSceneLayerType type, final boolean visible) {
        return LevelSceneLayer.builder().type(type).visible(visible).tiles(new Tile[GRID_SIZE][GRID_SIZE]).build();
    }

    private static FontMetrics metrics() {
        final Graphics2D graphics = new BufferedImage(1, 1, TYPE_INT_ARGB).createGraphics();
        try {
            return graphics.getFontMetrics(PIPE_PORTAL_LABEL_FONT);
        } finally {
            graphics.dispose();
        }
    }

    private static void assertLabelCentredIn(final BufferedImage image, final Rectangle area) {
        final Rectangle label = labelBounds(image, inner(area));
        assertThat(label).as("Label pixels inside the border").isNotNull();
        // The black shadow sits one pixel right and down, so the red glyphs alone are centred to within a couple of
        // pixels: font side bearings and the ascent/descent split are not symmetric.
        assertThat(label.getCenterX()).as("Label centre X").isCloseTo(area.getCenterX(), within(3.0));
        assertThat(label.getCenterY()).as("Label centre Y").isCloseTo(area.getCenterY(), within(3.0));
    }

    private static Rectangle inner(final Rectangle area) {
        return new Rectangle(
            area.x + BORDER_CLEARANCE,
            area.y + BORDER_CLEARANCE,
            area.width - 2 * BORDER_CLEARANCE,
            area.height - 2 * BORDER_CLEARANCE
        );
    }

    /** The bounding box of the red pixels inside {@code region}, or {@code null} when there are none. */
    private static Rectangle labelBounds(final BufferedImage image, final Rectangle region) {
        Rectangle bounds = null;
        for (int y = region.y; y < region.y + region.height; y++) {
            for (int x = region.x; x < region.x + region.width; x++) {
                if (isRed(image.getRGB(x, y))) {
                    final Rectangle pixel = new Rectangle(x, y, 1, 1);
                    bounds = bounds == null ? pixel : bounds.union(pixel);
                }
            }
        }
        return bounds;
    }

    private static boolean isRed(final int argb) {
        final Color colour = new Color(argb, true);
        return colour.getAlpha() > 128 && colour.getRed() > 200 && colour.getGreen() < 80 && colour.getBlue() < 80;
    }
}
