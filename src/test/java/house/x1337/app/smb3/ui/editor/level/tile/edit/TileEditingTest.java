package house.x1337.app.smb3.ui.editor.level.tile.edit;

import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.repository.LevelObjectRecord;
import house.x1337.app.smb3.model.service.TileMoveSummary;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.LevelObjectService;
import house.x1337.app.smb3.service.LevelSceneService;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneTabs;
import house.x1337.app.smb3.ui.editor.level.tile.edit.LevelObjectAssociation.Copy;
import house.x1337.app.smb3.ui.editor.level.tile.edit.LevelObjectAssociation.Link;
import house.x1337.app.smb3.ui.editor.level.tile.edit.LevelObjectAssociation.Unlink;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.QUESTION_BLOCK;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.INTERACTIVE_OBJECTS;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.STATIC_ENVIRONMENT;
import static house.x1337.app.smb3.enumeration.TileType.OBJECT_INTERACTIVE_SINGLE;
import static house.x1337.app.smb3.enumeration.TileType.SOLID;
import static house.x1337.app.smb3.enumeration.TileType.SOLID_RAMP_STEEP_RISING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Saving a tile from either tile window: the tile store, the palette and the open tabs follow, and a type of another
 * layer moves the tile in every stored scene and open tab once the author confirms.
 */
class TileEditingTest {
    private static final int TILE_ID = 1042;
    private static final int OBJECT_ID = 1500;

    private final TileService tileService = mock(TileService.class);
    private final LevelObjectService levelObjectService = mock(LevelObjectService.class);
    private final LevelSceneService levelSceneService = mock(LevelSceneService.class);
    private final LevelSceneTabs levelSceneTabs = mock(LevelSceneTabs.class);
    private final TilePalettePanel tilePalettePanel = mock(TilePalettePanel.class);
    private final TileWindows tileWindows = mock(TileWindows.class);
    private final LevelSceneEditorTab importedTab = mock(LevelSceneEditorTab.class);
    private final LevelSceneEditorTab storedTab = mock(LevelSceneEditorTab.class);
    private final TileEditing tileEditing = new TileEditing(
        tileService,
        levelObjectService,
        levelSceneService,
        levelSceneTabs,
        tilePalettePanel,
        tileWindows
    );

    @BeforeEach
    void prepare() {
        when(levelSceneTabs.getLevelSceneTabs()).thenReturn(List.of(importedTab, storedTab));
        when(importedTab.importsTile(TILE_ID)).thenReturn(true);
        when(levelSceneService.countTileCellsOutside(anyInt(), any())).thenReturn(new TileMoveSummary(0, 0));
    }

    @Test
    @DisplayName("A save stores the tile, shows it in the palette and refreshes every open tab")
    void aSaveReachesEverything() {
        // Prepare
        final Tile tile = tile(SOLID);

        // Execute
        final boolean saved = tileEditing.save(null, tile, new TileChanges(SOLID_RAMP_STEEP_RISING, "Slope", null));

        // Verify
        assertThat(saved).isTrue();
        assertThat(tile.getType()).isEqualTo(SOLID_RAMP_STEEP_RISING);
        assertThat(tile.getDescription()).isEqualTo("Slope");
        verify(tileService).updateTile(tile);
        verify(tilePalettePanel).showTile(tile);
        verify(importedTab).refreshTile(tile);
        verify(storedTab).refreshTile(tile);
        verify(importedTab).reRouteImportedLayers();
        verify(storedTab, never()).reRouteImportedLayers();
    }

    @Test
    @DisplayName("A type of the same layer moves nothing and asks nothing")
    void aSameLayerTypeMovesNothing() {
        // Execute
        tileEditing.save(null, tile(SOLID), new TileChanges(SOLID_RAMP_STEEP_RISING, null, null));

        // Verify
        verifyNoInteractions(tileWindows);
        verify(levelSceneService, never()).moveTile(anyInt(), any());
        verify(storedTab, never()).moveTile(any(), any());
    }

    @Test
    @DisplayName("A type of another layer asks first, then moves the tile in the stored scenes and every open tab")
    void anotherLayerMovesTheTileOnceConfirmed() {
        // Prepare
        final Tile tile = tile(SOLID);
        when(levelSceneService.countTileCellsOutside(TILE_ID, INTERACTIVE_OBJECTS))
            .thenReturn(new TileMoveSummary(2, 7));
        when(storedTab.countTileCellsOutside(TILE_ID, INTERACTIVE_OBJECTS)).thenReturn(3);
        when(tileWindows.confirmLayerMove(any(), anyString())).thenReturn(true);

        // Execute
        final boolean saved = tileEditing.save(null, tile, new TileChanges(OBJECT_INTERACTIVE_SINGLE, null, null));

        // Verify
        assertThat(saved).isTrue();
        verify(tileWindows).confirmLayerMove(
            null,
            tileEditing.layerMoveMessage(STATIC_ENVIRONMENT, INTERACTIVE_OBJECTS, new TileMoveSummary(2, 7), 3)
        );
        verify(tileService).updateTile(tile);
        verify(levelSceneService).moveTile(TILE_ID, INTERACTIVE_OBJECTS);
        verify(importedTab).moveTile(tile, INTERACTIVE_OBJECTS);
        verify(storedTab).moveTile(tile, INTERACTIVE_OBJECTS);
    }

    @Test
    @DisplayName("Cancelling the move changes nothing at all")
    void cancellingTheMoveChangesNothing() {
        // Prepare
        final Tile tile = tile(SOLID);
        when(storedTab.countTileCellsOutside(TILE_ID, INTERACTIVE_OBJECTS)).thenReturn(1);
        when(tileWindows.confirmLayerMove(any(), anyString())).thenReturn(false);

        // Execute
        final boolean saved = tileEditing.save(null, tile, new TileChanges(OBJECT_INTERACTIVE_SINGLE, "Brick", null));

        // Verify
        assertThat(saved).isFalse();
        assertThat(tile.getType()).isEqualTo(SOLID);
        assertThat(tile.getDescription()).isNull();
        verifyNoInteractions(tileService, tilePalettePanel);
        verify(levelSceneService, never()).moveTile(anyInt(), any());
        verify(storedTab, never()).moveTile(any(), any());
    }

    @Test
    @DisplayName("A tile placed nowhere changes layer without a confirmation")
    void aTilePlacedNowhereNeedsNoConfirmation() {
        // Execute
        final TileChanges changes = new TileChanges(OBJECT_INTERACTIVE_SINGLE, null, null);
        final boolean saved = tileEditing.save(null, tile(SOLID), changes);

        // Verify
        assertThat(saved).isTrue();
        verifyNoInteractions(tileWindows);
    }

    @Test
    @DisplayName("A tile getting its first type moves nothing: it was never stored in a layer")
    void aFirstTypeMovesNothing() {
        // Prepare
        final Tile tile = tile(null);

        // Execute
        tileEditing.save(null, tile, new TileChanges(OBJECT_INTERACTIVE_SINGLE, null, null));

        // Verify
        assertThat(tile.getType()).isEqualTo(OBJECT_INTERACTIVE_SINGLE);
        verifyNoInteractions(tileWindows);
        verify(levelSceneService, never()).moveTile(anyInt(), any());
        verify(tilePalettePanel).showTile(tile);
    }

    @Test
    @DisplayName("The confirmation names both layers and the cells it changes")
    void theConfirmationExplainsTheMove() {
        // Execute
        final String message = tileEditing.layerMoveMessage(
            STATIC_ENVIRONMENT,
            INTERACTIVE_OBJECTS,
            new TileMoveSummary(2, 7),
            3
        );

        // Verify
        assertThat(message)
            .contains(STATIC_ENVIRONMENT.getLabel())
            .contains(INTERACTIVE_OBJECTS.getLabel())
            .contains("7 cell(s) in 2 saved scene(s)")
            .contains("3 cell(s) in the open tabs");
    }

    @Test
    @DisplayName("Linking takes the type of the level object's tile, keeps the description and sets the link")
    void aLinkTakesTheTypeOfTheLevelObjectTile() {
        // Prepare
        final Tile tile = tile(null);
        tile.setDescription("Blue ground");
        final int[] pixels = {1, 2, 3};
        when(tileService.findById(OBJECT_ID)).thenReturn(Optional.of(Tile.builder().id(OBJECT_ID).type(SOLID).build()));

        // Execute
        final boolean saved = tileEditing.associate(null, tile, pixels, new Link(levelObject()));

        // Verify
        assertThat(saved).isTrue();
        assertThat(tile.getLevelObjectId()).isEqualTo(OBJECT_ID);
        assertThat(tile.getType()).isEqualTo(SOLID);
        assertThat(tile.getDescription()).isEqualTo("Blue ground");
        assertThat(tile.getArgbData()).isSameAs(pixels);
        verify(tileService).updateTile(tile);
        verify(tilePalettePanel).showTile(tile);
        verify(levelObjectService, never()).upsert(any());
    }

    @Test
    @DisplayName("A level object without a typed tile of its own makes the linked tile an interactive single object")
    void aLinkDefaultsToAnInteractiveSingleObject() {
        // Prepare
        final Tile tile = tile(null);
        when(tileService.findById(OBJECT_ID)).thenReturn(Optional.empty());

        // Execute
        tileEditing.associate(null, tile, null, new Link(levelObject()));

        // Verify
        assertThat(tile.getType()).isEqualTo(OBJECT_INTERACTIVE_SINGLE);
        assertThat(tile.getLevelObjectId()).isEqualTo(OBJECT_ID);
    }

    @Test
    @DisplayName("A copy is stored under the tile's id with the source's type and the edited description and data")
    void aCopyIsStoredUnderTheTileId() {
        // Prepare
        final Tile tile = tile(null);
        final LevelObjectRecord source = levelObject();
        final Copy copy = new Copy(source, "Blue question block", Map.of("reward", "MUSHROOM"));

        // Execute
        final boolean saved = tileEditing.associate(null, tile, null, copy);

        // Verify
        final ArgumentCaptor<LevelObjectRecord> stored = ArgumentCaptor.forClass(LevelObjectRecord.class);
        verify(levelObjectService).upsert(stored.capture());
        assertThat(saved).isTrue();
        assertThat(stored.getValue().getId()).isEqualTo(TILE_ID);
        assertThat(stored.getValue().getType()).isEqualTo(QUESTION_BLOCK.name());
        assertThat(stored.getValue().getDescription()).isEqualTo("Blue question block");
        assertThat(stored.getValue().getData()).containsExactly(entry("reward", "MUSHROOM"));
        assertThat(tile.getLevelObjectId()).isEqualTo(TILE_ID);
        assertThat(source.getDescription()).as("The source is left as it was").isEqualTo("Question block");
        assertThat(source.getData()).containsExactly(entry("reward", "COIN"));
    }

    @Test
    @DisplayName("A copy replacing a level object other tiles are linked to asks first, and a cancel changes nothing")
    void aCopyOverALinkedLevelObjectAsksFirst() {
        // Prepare
        final Tile tile = tile(OBJECT_INTERACTIVE_SINGLE);
        final Tile otherTile = Tile.builder().id(2000).levelObjectId(TILE_ID).build();
        when(tileService.getTilesLinkedTo(TILE_ID)).thenReturn(List.of(otherTile));
        when(tileWindows.confirmLevelObjectReplacement(any(), anyString())).thenReturn(false);

        // Execute
        final boolean saved = tileEditing.associate(null, tile, null, new Copy(levelObject(), null, Map.of()));

        // Verify
        assertThat(saved).isFalse();
        verify(tileWindows).confirmLevelObjectReplacement(null, tileEditing.replacementMessage(1));
        verifyNoInteractions(levelObjectService, tilePalettePanel);
        verify(tileService, never()).updateTile(any());
        assertThat(tile.getLevelObjectId()).isNull();
    }

    @Test
    @DisplayName("A copy whose layer move is cancelled stores no level object")
    void aCancelledMoveStoresNoCopy() {
        // Prepare
        final Tile tile = tile(SOLID);
        when(storedTab.countTileCellsOutside(TILE_ID, INTERACTIVE_OBJECTS)).thenReturn(1);
        when(tileWindows.confirmLayerMove(any(), anyString())).thenReturn(false);

        // Execute
        final boolean saved = tileEditing.associate(null, tile, null, new Copy(levelObject(), null, Map.of()));

        // Verify
        assertThat(saved).isFalse();
        verifyNoInteractions(levelObjectService);
        assertThat(tile.getType()).isEqualTo(SOLID);
    }

    @Test
    @DisplayName("Unlinking clears the link and keeps the tile's type and description")
    void unlinkingKeepsTheTypeAndDescription() {
        // Prepare
        final Tile tile = linkedTile();

        // Execute
        final boolean saved = tileEditing.associate(null, tile, null, new Unlink());

        // Verify
        assertThat(saved).isTrue();
        assertThat(tile.getLevelObjectId()).isNull();
        assertThat(tile.getType()).isEqualTo(OBJECT_INTERACTIVE_SINGLE);
        assertThat(tile.getDescription()).isEqualTo("Brick");
        verify(tileService).updateTile(tile);
    }

    @Test
    @DisplayName("Setting a linked tile's properties clears its link")
    void settingPropertiesClearsTheLink() {
        // Prepare
        final Tile tile = linkedTile();

        // Execute
        tileEditing.save(null, tile, new TileChanges(OBJECT_INTERACTIVE_SINGLE, "Brick", null));

        // Verify
        assertThat(tile.getLevelObjectId()).isNull();
    }

    private Tile tile(final TileType type) {
        return Tile.builder().id(TILE_ID).type(type).build();
    }

    private Tile linkedTile() {
        return Tile.builder().id(TILE_ID).type(OBJECT_INTERACTIVE_SINGLE).description("Brick").levelObjectId(OBJECT_ID)
            .build();
    }

    private LevelObjectRecord levelObject() {
        return LevelObjectRecord.builder()
            .id(OBJECT_ID)
            .type(QUESTION_BLOCK.name())
            .description("Question block")
            .data(new HashMap<>(Map.of("reward", "COIN")))
            .build();
    }
}
