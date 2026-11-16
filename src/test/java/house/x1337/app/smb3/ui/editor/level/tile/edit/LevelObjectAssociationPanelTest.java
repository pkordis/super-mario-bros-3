package house.x1337.app.smb3.ui.editor.level.tile.edit;

import house.x1337.app.smb3.model.repository.LevelObjectRecord;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.LevelObjectService;
import house.x1337.app.smb3.ui.editor.component.CustomDataTablePanel;
import house.x1337.app.smb3.ui.editor.level.tile.edit.LevelObjectAssociation.Copy;
import house.x1337.app.smb3.ui.editor.level.tile.edit.LevelObjectAssociation.Link;
import house.x1337.app.smb3.ui.editor.level.tile.edit.LevelObjectAssociation.Unlink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.COIN_FLIPPING;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.QUESTION_BLOCK;
import static house.x1337.app.smb3.ui.editor.level.tile.edit.LevelObjectAssociationPanel.CREATE_AND_LINK_LABEL;
import static house.x1337.app.smb3.ui.editor.level.tile.edit.LevelObjectAssociationPanel.LINK_LABEL;
import static house.x1337.app.smb3.ui.editor.level.tile.edit.LevelObjectAssociationPanel.UNLINK_LABEL;
import static house.x1337.app.smb3.ui.editor.level.tile.edit.LevelObjectChoice.NONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The Associate with Existing Level Object section of the tile form: an untouched level object is linked, a changed
 * one becomes a new level object.
 */
class LevelObjectAssociationPanelTest {
    private static final int TILE_ID = 1042;
    private static final int QUESTION_BLOCK_ID = 1500;
    private static final int COIN_ID = 1600;

    private final LevelObjectService levelObjectService = mock(LevelObjectService.class);
    private final CustomDataTablePanel dataPanel = new CustomDataTablePanel();
    private final LevelObjectAssociationPanel panel = new LevelObjectAssociationPanel(levelObjectService, dataPanel);
    private final LevelObjectRecord questionBlock = LevelObjectRecord.builder()
        .id(QUESTION_BLOCK_ID)
        .type(QUESTION_BLOCK.name())
        .description("Question block")
        .data(Map.of("reward", "COIN"))
        .build();
    private final LevelObjectRecord coin = LevelObjectRecord.builder()
        .id(COIN_ID)
        .type(COIN_FLIPPING.name())
        .data(Map.of("value", 1))
        .build();

    @BeforeEach
    void prepare() {
        when(levelObjectService.getSingleTiledLevelObjects()).thenReturn(List.of(questionBlock, coin));
        panel.init();
        panel.setActive(true);
    }

    @Test
    @DisplayName("The dropdown offers None, then every single-tiled level object once")
    void theDropdownOffersEachObjectOnce() {
        // Execute
        panel.load(tile(null));

        // Verify
        assertThat(choices()).containsExactly(
            NONE,
            new LevelObjectChoice(questionBlock),
            new LevelObjectChoice(coin)
        );
        assertThat(panel.getLevelObjectCombo().getSelectedItem()).isEqualTo(NONE);
    }

    @Test
    @DisplayName("A linked tile opens with its level object chosen")
    void aLinkedTileShowsItsLevelObject() {
        // Execute
        panel.load(tile(COIN_ID));

        // Verify
        assertThat(panel.getLevelObjectCombo().getSelectedItem()).isEqualTo(new LevelObjectChoice(coin));
    }

    @Test
    @DisplayName("An untouched level object shows its description and data, editable, and is linked")
    void anUntouchedLevelObjectIsLinked() {
        // Prepare
        panel.load(tile(null));

        // Execute
        choose(questionBlock);

        // Verify
        assertThat(panel.getDescriptionField().getText()).isEqualTo("Question block");
        assertThat(panel.getDescriptionField().isEnabled()).isTrue();
        assertThat(dataPanel.getData()).containsExactly(entry("reward", "COIN"));
        assertThat(dataPanel.isEditable()).isTrue();
        assertThat(panel.isModified()).isFalse();
        assertThat(panel.getSaveButton().getText()).isEqualTo(LINK_LABEL);
        assertThat(panel.association()).isEqualTo(new Link(questionBlock));
    }

    @Test
    @DisplayName("Data shown in its text form is not a change")
    void dataInTextFormIsUnchanged() {
        // Prepare
        panel.load(tile(null));

        // Execute
        choose(coin);

        // Verify
        assertThat(panel.isModified()).isFalse();
        assertThat(panel.association()).isEqualTo(new Link(coin));
    }

    @Test
    @DisplayName("A changed description creates a new level object with it")
    void aChangedDescriptionCreatesANewObject() {
        // Prepare
        panel.load(tile(null));
        choose(questionBlock);

        // Execute
        panel.getDescriptionField().setText("  Blue question block ");

        // Verify
        assertThat(panel.getSaveButton().getText()).isEqualTo(CREATE_AND_LINK_LABEL);
        final Copy copy = (Copy) panel.association();
        assertThat(copy.source()).isSameAs(questionBlock);
        assertThat(copy.description()).isEqualTo("Blue question block");
        assertThat(copy.data()).containsExactly(entry("reward", "COIN"));
    }

    @Test
    @DisplayName("Changed data creates a new level object with it")
    void changedDataCreatesANewObject() {
        // Prepare
        panel.load(tile(null));
        choose(questionBlock);

        // Execute - the author's edit, standing in for typing into the table
        dataPanel.show(Map.of("reward", "MUSHROOM"));

        // Verify
        assertThat(panel.getSaveButton().getText()).isEqualTo(CREATE_AND_LINK_LABEL);
        final Copy copy = (Copy) panel.association();
        assertThat(copy.description()).isEqualTo("Question block");
        assertThat(copy.data()).containsExactly(entry("reward", "MUSHROOM"));
    }

    @Test
    @DisplayName("Changing a value back links the level object again")
    void aChangeUndoneLinksAgain() {
        // Prepare
        panel.load(tile(null));
        choose(questionBlock);
        panel.getDescriptionField().setText("Blue question block");

        // Execute
        panel.getDescriptionField().setText("Question block");

        // Verify
        assertThat(panel.getSaveButton().getText()).isEqualTo(LINK_LABEL);
        assertThat(panel.association()).isEqualTo(new Link(questionBlock));
    }

    @Test
    @DisplayName("None can only be saved for a linked tile, and then unlinks it")
    void noneUnlinksALinkedTile() {
        // Prepare
        panel.load(tile(null));
        final boolean savableUnlinked = panel.isSavable();

        // Execute
        panel.load(tile(COIN_ID));
        choose(null);

        // Verify
        assertThat(savableUnlinked).isFalse();
        assertThat(panel.isSavable()).isTrue();
        assertThat(panel.getSaveButton().getText()).isEqualTo(UNLINK_LABEL);
        assertThat(panel.getDescriptionField().isEnabled()).isFalse();
        assertThat(panel.association()).isEqualTo(new Unlink());
    }

    @Test
    @DisplayName("An inactive section cannot be used")
    void anInactiveSectionIsDisabled() {
        // Prepare
        panel.load(tile(null));
        choose(questionBlock);

        // Execute
        panel.setActive(false);

        // Verify
        assertThat(panel.getLevelObjectCombo().isEnabled()).isFalse();
        assertThat(panel.getDescriptionField().isEnabled()).isFalse();
        assertThat(dataPanel.isEditable()).isFalse();
        assertThat(panel.isSavable()).isFalse();
    }

    @Test
    @DisplayName("A choice names the level object's type, id and description")
    void aChoiceNamesItsLevelObject() {
        // Execute & Verify
        assertThat(new LevelObjectChoice(questionBlock))
            .hasToString(QUESTION_BLOCK.getLabel() + " #1500 - Question block");
        assertThat(new LevelObjectChoice(coin)).hasToString(COIN_FLIPPING.getLabel() + " #1600");
        assertThat(NONE).hasToString(LevelObjectChoice.NONE_LABEL);
    }

    private void choose(final LevelObjectRecord levelObject) {
        panel.getLevelObjectCombo().setSelectedItem(levelObject == null ? NONE : new LevelObjectChoice(levelObject));
    }

    private List<LevelObjectChoice> choices() {
        final List<LevelObjectChoice> choices = new ArrayList<>();
        for (int index = 0; index < panel.getLevelObjectCombo().getItemCount(); index++) {
            choices.add(panel.getLevelObjectCombo().getItemAt(index));
        }
        return choices;
    }

    private Tile tile(final Integer levelObjectId) {
        return Tile.builder().id(TILE_ID).levelObjectId(levelObjectId).build();
    }
}
