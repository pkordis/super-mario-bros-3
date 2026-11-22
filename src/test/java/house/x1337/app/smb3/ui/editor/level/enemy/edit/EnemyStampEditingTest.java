package house.x1337.app.smb3.ui.editor.level.enemy.edit;

import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.EnemyStampService;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemiesPalettePanel;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorGrid;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneTabs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.awt.Component;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.enemy.EnemyType.KOOPA_TROOPA_GREEN;
import static house.x1337.app.smb3.enumeration.enemy.EnemyType.KOOPA_TROOPA_RED;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Saving an edited enemy. The red Koopa Troopa is a head over a body, anchored on the body, with an empty part on
 * its right; the green one shares its head tile. Every part is red.
 */
class EnemyStampEditingTest {
    private static final int RED = 0xFFFF0000;

    private final Tile head = tile(1104);
    private final Tile redBody = tile(1105);
    private final Tile greenBody = tile(1106);
    private final EnemyStamp redTroopa = EnemyStamp
        .builder()
        .id("red")
        .enemyType(KOOPA_TROOPA_RED)
        .tiles(new Tile[][] {{head, NULL_TILE}, {redBody, NULL_TILE}})
        .renderingStarter(of(0, 1))
        .data(Map.of("speed", "slow"))
        .build();
    private final EnemyStamp greenTroopa = EnemyStamp
        .builder()
        .id("green")
        .enemyType(KOOPA_TROOPA_GREEN)
        .tiles(new Tile[][] {{head}, {greenBody}})
        .renderingStarter(of(0, 1))
        .build();
    private final EnemyStampService enemyStampService = mock(EnemyStampService.class);
    private final TileService tileService = mock(TileService.class);
    private final LevelSceneTabs levelSceneTabs = mock(LevelSceneTabs.class);
    private final EnemiesPalettePanel enemiesPalettePanel = mock(EnemiesPalettePanel.class);
    private final EnemyWindows enemyWindows = mock(EnemyWindows.class);
    private final LevelSceneEditorTab tab = mock(LevelSceneEditorTab.class);
    private final LevelSceneEditorGrid grid = mock(LevelSceneEditorGrid.class);
    private final Component parent = mock(Component.class);
    private final EnemyStampEditing editing = new EnemyStampEditing(
        enemyStampService,
        tileService,
        levelSceneTabs,
        enemiesPalettePanel,
        enemyWindows
    );

    @BeforeEach
    void prepare() {
        when(enemyStampService.findAll()).thenReturn(List.of(greenTroopa, redTroopa));
        when(levelSceneTabs.getLevelSceneTabs()).thenReturn(List.of(tab));
        when(tab.getLevelSceneEditorGrid()).thenReturn(grid);
    }

    @Test
    @DisplayName("The parts are copies to edit, one per tile, and an empty part is all transparent")
    void editablePixelsAreCopies() {
        // Execute
        final int[][][] pixels = editing.editablePixelsOf(redTroopa);
        pixels[0][0][0] = 0;

        // Verify
        assertThat(head.getArgbData()[0]).as("The stored tile is untouched").isEqualTo(RED);
        assertThat(pixels[1][0]).containsOnly(RED);
        assertThat(pixels[0][1]).containsOnly(0);
    }

    @Test
    @DisplayName("A new rendering starter is saved as a new copy of the stamp, shown in the palette and every tab")
    void aNewRenderingStarterIsSaved() {
        // Prepare
        final int[][][] pixels = editing.editablePixelsOf(redTroopa);

        // Execute
        final boolean saved = editing.save(parent, redTroopa, of(0, 0), pixels);

        // Verify
        assertThat(saved).isTrue();
        final EnemyStamp edited = upserted();
        assertThat(edited).isNotSameAs(redTroopa);
        assertThat(edited.getRenderingStarter()).isEqualTo(of(0, 0));
        assertThat(edited.getId()).isEqualTo("red");
        assertThat(edited.getData()).containsEntry("speed", "slow");
        verify(enemiesPalettePanel).addEnemy(edited);
        verify(grid).repaint();
        verify(tileService, never()).updateTile(any());
        verify(enemyWindows, never()).confirmSharedParts(any(), anyString());
    }

    @Test
    @DisplayName("A cleared pixel of a part used by no other enemy is saved to its tile, and every tab redraws it")
    void aClearedPixelIsSavedToItsTile() {
        // Prepare
        final int[][][] pixels = editing.editablePixelsOf(redTroopa);
        pixels[1][0][5] = 0;

        // Execute
        final boolean saved = editing.save(parent, redTroopa, of(0, 1), pixels);

        // Verify
        assertThat(saved).isTrue();
        assertThat(redBody.getArgbData()[5]).isZero();
        assertThat(redBody.getArgbData()).isNotSameAs(pixels[1][0]);
        assertThat(redBody.getOriginalArgbData()[5]).as("The imported pixels are kept").isEqualTo(RED);
        verify(tileService).updateTile(redBody);
        verify(tab).refreshTile(redBody);
        verify(enemyWindows, never()).confirmSharedParts(any(), anyString());
    }

    @Test
    @DisplayName("Clearing a part another enemy shares asks first; declined, nothing changes")
    void aSharedPartAsksFirst() {
        // Prepare
        final int[][][] pixels = editing.editablePixelsOf(redTroopa);
        pixels[0][0][0] = 0;
        when(enemyWindows.confirmSharedParts(any(), anyString())).thenReturn(false);

        // Execute
        final boolean saved = editing.save(parent, redTroopa, of(0, 0), pixels);

        // Verify
        assertThat(saved).isFalse();
        assertThat(head.getArgbData()[0]).isEqualTo(RED);
        verify(enemyWindows).confirmSharedParts(parent, editing.sharedPartsMessage(List.of(greenTroopa)));
        verify(tileService, never()).updateTile(any());
        verify(enemyStampService, never()).upsert(any());
    }

    @Test
    @DisplayName("Clearing a shared part once confirmed changes it, and the other enemy's palette entry is redrawn")
    void aConfirmedSharedPartIsSaved() {
        // Prepare
        final int[][][] pixels = editing.editablePixelsOf(redTroopa);
        pixels[0][0][0] = 0;
        when(enemyWindows.confirmSharedParts(any(), anyString())).thenReturn(true);

        // Execute
        final boolean saved = editing.save(parent, redTroopa, of(0, 1), pixels);

        // Verify
        assertThat(saved).isTrue();
        assertThat(head.getArgbData()[0]).isZero();
        verify(tileService).updateTile(head);
        verify(enemiesPalettePanel).addEnemy(greenTroopa);
    }

    @Test
    @DisplayName("The warning names every other enemy sharing a changed part")
    void theWarningNamesTheOtherEnemies() {
        // Execute
        final String message = editing.sharedPartsMessage(List.of(greenTroopa));

        // Verify
        assertThat(message).contains(KOOPA_TROOPA_GREEN.getLabel()).contains("changes those enemies too");
    }

    private EnemyStamp upserted() {
        final ArgumentCaptor<EnemyStamp> captor = ArgumentCaptor.forClass(EnemyStamp.class);
        verify(enemyStampService).upsert(captor.capture());
        return captor.getValue();
    }

    private Tile tile(final int id) {
        final int[] pixels = new int[TILE_SPRITE_SIZE * TILE_SPRITE_SIZE];
        Arrays.fill(pixels, RED);
        return Tile
            .builder()
            .id(id)
            .type(TileType.ENEMY_PART)
            .originalArgbData(pixels.clone())
            .argbData(pixels)
            .build();
    }
}
