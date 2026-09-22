package house.x1337.app.smb3.ui.editor.level.tile.palette;

import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.ui.tile.Tile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The out-of-layer state a palette entry wears when its tile belongs to a layer other than the active one.
 *
 * <p>The state exists instead of {@code setEnabled(false)} for one reason: a disabled Swing button gets no
 * mouse events, and clicking one of these has to keep working — it is how the user switches to the layer
 * the tile belongs on.
 */
class TileButtonOutOfLayerStateTest {

    @Test
    @DisplayName("A button reports the layer its tile belongs on")
    void reportsItsOwningLayer() {
        assertThat(buttonFor(TileType.OBJECT_INTERACTIVE_SINGLE).getOwningLayer())
            .isEqualTo(INTERACTIVE_OBJECTS);
        assertThat(buttonFor(TileType.SOLID).getOwningLayer()).isEqualTo(STATIC_ENVIRONMENT);
    }

    @Test
    @DisplayName("A virtual tile belongs to no layer, so it is never gated")
    void virtualTileHasNoOwningLayer() {
        assertThat(buttonFor(TileType.SPAWN_POINT).getOwningLayer()).isNull();
    }

    @Test
    @DisplayName("A tile with no type yet reports no layer rather than failing")
    void unclassifiedTileHasNoOwningLayer() {
        final TileButton button = new TileButton(Tile.builder().id(1).build());

        assertThat(button.getOwningLayer()).isNull();
    }

    @Test
    @DisplayName("Marking a button out of layer stays clickable, which is the whole point")
    void anOutOfLayerButtonRemainsClickable() {
        // Prepare
        final TileButton button = buttonFor(TileType.OBJECT_INTERACTIVE_SINGLE);

        // Execute
        button.markOutOfActiveLayer(true);

        // Verify - dimmed in appearance but still live, so the click that switches layers still arrives
        assertThat(button.isOutOfActiveLayer()).isTrue();
        assertThat(button.isEnabled())
            .as("a disabled button would swallow the click that switches layers")
            .isTrue();
    }

    @Test
    @DisplayName("The state clears again when its layer becomes the active one")
    void theStateClearsWhenTheLayerBecomesActive() {
        // Prepare
        final TileButton button = buttonFor(TileType.OBJECT_INTERACTIVE_SINGLE);
        button.markOutOfActiveLayer(true);

        // Execute
        button.markOutOfActiveLayer(false);

        // Verify
        assertThat(button.isOutOfActiveLayer()).isFalse();
    }

    @Test
    @DisplayName("An out-of-layer button explains where its tile belongs, and drops the note afterwards")
    void tooltipNamesTheOwningLayer() {
        // Prepare
        final TileButton button = buttonFor(TileType.OBJECT_INTERACTIVE_SINGLE).withTooltip("Brick Block");

        // Execute
        button.markOutOfActiveLayer(true);

        // Verify
        assertThat(button.getToolTipText())
            .contains("Brick Block")
            .contains(INTERACTIVE_OBJECTS.getLabel())
            .contains("Click to switch");

        // Execute - and back
        button.markOutOfActiveLayer(false);

        // Verify
        assertThat(button.getToolTipText()).isEqualTo("Brick Block");
    }

    @Test
    @DisplayName("A virtual tile never picks up the out-of-layer note, having no layer to name")
    void virtualTileTooltipIsLeftAlone() {
        // Prepare
        final TileButton button = buttonFor(TileType.SPAWN_POINT).withTooltip("Spawn Point");

        // Execute - even if something marked it, there is no layer to point at
        button.markOutOfActiveLayer(true);

        // Verify
        assertThat(button.getToolTipText()).isEqualTo("Spawn Point");
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /** A bare button, bypassing {@code fromTile} so no Spring context or thumbnail rendering is needed. */
    private static TileButton buttonFor(final TileType type) {
        return new TileButton(Tile.builder().id(type.ordinal()).type(type).build());
    }
}
