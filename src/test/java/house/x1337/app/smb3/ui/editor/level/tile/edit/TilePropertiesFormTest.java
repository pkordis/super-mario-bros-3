package house.x1337.app.smb3.ui.editor.level.tile.edit;

import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.repository.LevelObjectRecord;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.LevelObjectService;
import house.x1337.app.smb3.ui.editor.component.CustomDataTablePanel;
import house.x1337.app.smb3.ui.editor.level.tile.edit.LevelObjectAssociation.Link;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.QUESTION_BLOCK;
import static house.x1337.app.smb3.enumeration.TileType.OBJECT_INTERACTIVE_SINGLE;
import static house.x1337.app.smb3.enumeration.TileType.SOLID;
import static house.x1337.app.smb3.ui.editor.level.tile.edit.TilePropertiesForm.NO_TYPE_LABEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The tile form both tile windows share. */
class TilePropertiesFormTest {
    private static final int RED = Color.RED.getRGB();

    private static final int OBJECT_ID = 1500;

    private final LevelObjectService levelObjectService = mock(LevelObjectService.class);
    private final TileEditing tileEditing = mock(TileEditing.class);
    private final TileProcessingPanel processingPanel = new TileProcessingPanel();
    private final LevelObjectAssociationPanel associationPanel = new LevelObjectAssociationPanel(
        levelObjectService,
        new CustomDataTablePanel()
    );
    private final TilePropertiesForm form = new TilePropertiesForm(processingPanel, associationPanel, tileEditing);
    private final LevelObjectRecord questionBlock = LevelObjectRecord.builder()
        .id(OBJECT_ID)
        .type(QUESTION_BLOCK.name())
        .build();

    @BeforeEach
    void prepare() {
        when(levelObjectService.getSingleTiledLevelObjects()).thenReturn(List.of(questionBlock));
        processingPanel.init();
        associationPanel.init();
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
    @DisplayName("Save in the association section runs the same action as Set")
    void saveRunsTheAction() {
        // Prepare
        final AtomicInteger runs = new AtomicInteger();
        form.onSet(runs::incrementAndGet);
        form.load(tile(null, null));
        associationPanel.getRadio().doClick();
        associationPanel.getLevelObjectCombo().setSelectedIndex(1);

        // Execute
        associationPanel.getSaveButton().doClick();

        // Verify
        assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("Applying the changes sets the tile's type, description, pixels and link")
    void changesApplyToTheTile() {
        // Prepare
        final Tile tile = tile(SOLID, "Ground");
        tile.setLevelObjectId(OBJECT_ID);
        final int[] pixels = new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE];

        // Execute
        new TileChanges(OBJECT_INTERACTIVE_SINGLE, null, pixels).applyTo(tile);

        // Verify
        assertThat(tile.getType()).isEqualTo(OBJECT_INTERACTIVE_SINGLE);
        assertThat(tile.getDescription()).isNull();
        assertThat(tile.getArgbData()).isSameAs(pixels);
        assertThat(tile.getLevelObjectId()).as("Properties alone clear the link").isNull();
    }

    @Test
    @DisplayName("An unlinked tile opens on the tile properties, with the association section disabled")
    void anUnlinkedTileOpensOnTheProperties() {
        // Execute
        form.load(tile(SOLID, null));

        // Verify
        assertThat(form.getPropertiesRadio().isSelected()).isTrue();
        assertThat(form.getTypeCombo().isEnabled()).isTrue();
        assertThat(associationPanel.getLevelObjectCombo().isEnabled()).isFalse();
        assertThat(associationPanel.isSavable()).isFalse();
    }

    @Test
    @DisplayName("A linked tile opens on the association section, with the tile properties disabled")
    void aLinkedTileOpensOnTheAssociation() {
        // Prepare
        final Tile tile = tile(OBJECT_INTERACTIVE_SINGLE, null);
        tile.setLevelObjectId(OBJECT_ID);

        // Execute
        form.load(tile);

        // Verify
        assertThat(form.isAssociating()).isTrue();
        assertThat(form.getTypeCombo().isEnabled()).isFalse();
        assertThat(form.getDescriptionField().isEnabled()).isFalse();
        assertThat(form.isSettable()).isFalse();
        assertThat(associationPanel.getLevelObjectCombo().isEnabled()).isTrue();
        assertThat(associationPanel.isSavable()).isTrue();
    }

    @Test
    @DisplayName("The radio buttons switch which section is active")
    void theRadioButtonsSwitchSections() {
        // Prepare
        form.load(tile(SOLID, null));

        // Execute
        associationPanel.getRadio().doClick();
        final boolean typeEnabledWhileAssociating = form.getTypeCombo().isEnabled();
        final boolean comboEnabledWhileAssociating = associationPanel.getLevelObjectCombo().isEnabled();
        form.getPropertiesRadio().doClick();

        // Verify
        assertThat(typeEnabledWhileAssociating).isFalse();
        assertThat(comboEnabledWhileAssociating).isTrue();
        assertThat(form.getTypeCombo().isEnabled()).isTrue();
        assertThat(associationPanel.getLevelObjectCombo().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Saving from the tile properties hands the changes to the tile editing")
    void savingThePropertiesSavesTheChanges() {
        // Prepare
        final Tile tile = tile(SOLID, "Ground");
        form.load(tile);
        when(tileEditing.save(any(), any(), any())).thenReturn(true);

        // Execute
        final boolean saved = form.save(null, tile);

        // Verify
        final ArgumentCaptor<TileChanges> changes = ArgumentCaptor.forClass(TileChanges.class);
        verify(tileEditing).save(eq(null), eq(tile), changes.capture());
        assertThat(saved).isTrue();
        assertThat(changes.getValue().type()).isEqualTo(SOLID);
        assertThat(changes.getValue().levelObjectId()).isNull();
    }

    @Test
    @DisplayName("Saving from the association section links the tile with its pixels")
    void savingTheAssociationLinksTheTile() {
        // Prepare
        final Tile tile = tile(null, null);
        form.load(tile);
        associationPanel.getRadio().doClick();
        associationPanel.getLevelObjectCombo().setSelectedIndex(1);
        when(tileEditing.associate(any(), any(), any(), any())).thenReturn(true);

        // Execute
        final boolean saved = form.save(null, tile);

        // Verify
        final ArgumentCaptor<int[]> pixels = ArgumentCaptor.forClass(int[].class);
        verify(tileEditing).associate(eq(null), eq(tile), pixels.capture(), eq(new Link(questionBlock)));
        assertThat(saved).isTrue();
        assertThat(pixels.getValue()).containsOnly(RED);
        verify(tileEditing, never()).save(any(), any(), any());
    }

    private Tile tile(final TileType type, final String description) {
        final int[] pixels = new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE];
        Arrays.fill(pixels, RED);
        return Tile.builder().id(1001).type(type).description(description).argbData(pixels).build();
    }
}
