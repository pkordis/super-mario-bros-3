package house.x1337.app.smb3.ui.editor.level.tile.edit;

import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.ui.tile.Tile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.TileType.OBJECT_INTERACTIVE_SINGLE;
import static house.x1337.app.smb3.enumeration.TileType.SOLID;
import static house.x1337.app.smb3.ui.editor.level.tile.edit.TilePropertiesForm.NO_TYPE_LABEL;
import static org.assertj.core.api.Assertions.assertThat;

/** The Tile Properties form both tile windows share. */
class TilePropertiesFormTest {
    private static final int RED = Color.RED.getRGB();

    private final TileProcessingPanel processingPanel = new TileProcessingPanel();
    private final TilePropertiesForm form = new TilePropertiesForm(processingPanel);

    @BeforeEach
    void prepare() {
        processingPanel.init();
        form.init();
    }

    @Test
    @DisplayName("With no tile the form is disabled and Set cannot be pressed")
    void emptyFormIsDisabled() {
        // Verify
        assertThat(form.getTypeCombo().isEnabled()).isFalse();
        assertThat(form.getDescriptionField().isEnabled()).isFalse();
        assertThat(form.isSettable()).isFalse();
    }

    @Test
    @DisplayName("A tile without a type can only be set once a type is chosen")
    void setNeedsAType() {
        // Prepare
        form.load(tile(null, null));
        final boolean settableWithoutAType = form.isSettable();

        // Execute
        form.getTypeCombo().setSelectedItem(SOLID.getLabel());
        final boolean settableWithAType = form.isSettable();
        form.getTypeCombo().setSelectedItem(NO_TYPE_LABEL);

        // Verify
        assertThat(settableWithoutAType).isFalse();
        assertThat(settableWithAType).isTrue();
        assertThat(form.isSettable()).as("Back to no type").isFalse();
    }

    @Test
    @DisplayName("A classified tile loads with its type and description, ready to set")
    void aClassifiedTileLoads() {
        // Execute
        form.load(tile(SOLID, "Ground"));

        // Verify
        assertThat(form.getTypeCombo().getSelectedItem()).isEqualTo(SOLID.getLabel());
        assertThat(form.getDescriptionField().getText()).isEqualTo("Ground");
        assertThat(form.isSettable()).isTrue();
    }

    @Test
    @DisplayName("The changes hold the chosen type, the trimmed description and the pixels")
    void changesHoldTheForm() {
        // Prepare
        form.load(tile(SOLID, "Ground"));
        form.getTypeCombo().setSelectedItem(OBJECT_INTERACTIVE_SINGLE.getLabel());
        form.getDescriptionField().setText("  Brick  ");

        // Execute
        final TileChanges changes = form.changes();

        // Verify
        assertThat(changes.type()).isEqualTo(OBJECT_INTERACTIVE_SINGLE);
        assertThat(changes.description()).isEqualTo("Brick");
        assertThat(changes.argbData()).containsOnly(RED);
    }

    @Test
    @DisplayName("A blank description becomes no description")
    void aBlankDescriptionIsNone() {
        // Prepare
        form.load(tile(SOLID, "Ground"));
        form.getDescriptionField().setText("   ");

        // Execute & Verify
        assertThat(form.changes().description()).isNull();
    }

    @Test
    @DisplayName("Set runs the action it was given")
    void setRunsTheAction() {
        // Prepare
        final AtomicInteger runs = new AtomicInteger();
        form.onSet(runs::incrementAndGet);
        form.load(tile(SOLID, null));

        // Execute
        form.getSetButton().doClick();

        // Verify
        assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("Applying the changes sets the tile's type, description and pixels")
    void changesApplyToTheTile() {
        // Prepare
        final Tile tile = tile(SOLID, "Ground");
        final int[] pixels = new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE];

        // Execute
        new TileChanges(OBJECT_INTERACTIVE_SINGLE, null, pixels).applyTo(tile);

        // Verify
        assertThat(tile.getType()).isEqualTo(OBJECT_INTERACTIVE_SINGLE);
        assertThat(tile.getDescription()).isNull();
        assertThat(tile.getArgbData()).isSameAs(pixels);
    }

    private Tile tile(final TileType type, final String description) {
        final int[] pixels = new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE];
        Arrays.fill(pixels, RED);
        return Tile.builder().id(1001).type(type).description(description).argbData(pixels).build();
    }
}
