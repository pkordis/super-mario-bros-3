package house.x1337.app.smb3.util.factory;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.camera.LevelSceneVibration;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyLevelSceneGridStamper;
import house.x1337.app.smb3.ui.editor.level.service.PaletteSelectionService;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorGrid;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Opening a stored scene in the editor keeps its spawn point only when both of the player's cells fit in the scene;
 * one stored on the bottom row is dropped, so Test Level Scene asks for a new one.
 */
class SpawnPointLoadingTest {
    private static final int ROWS = 6;
    private static final int COLUMNS = 8;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private LevelSceneEditorTab target;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(LevelSceneVibration.class))
            .thenReturn(mock(LevelSceneVibration.class));
        target = mock(LevelSceneEditorTab.class);
        when(target.getLevelSceneEditorGrid()).thenReturn(mock(LevelSceneEditorGrid.class));
        when(target.spawnPointFits(of(3, 4))).thenReturn(true);
        when(target.spawnPointFits(of(3, 5))).thenReturn(false);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(LevelSceneImportRouter.class))
            .thenReturn(mock(LevelSceneImportRouter.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(TilePalettePanel.class))
            .thenReturn(mock(TilePalettePanel.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(PaletteSelectionService.class))
            .thenReturn(mock(PaletteSelectionService.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(EnemyLevelSceneGridStamper.class))
            .thenReturn(mock(EnemyLevelSceneGridStamper.class));
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(LevelSceneEditorTab.class), any(), any(), any(), any(), any()))
            .thenReturn(target);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A spawn point with room for both cells opens where it was stored")
    void aFittingSpawnPointIsKept() {
        // Prepare
        when(target.getLevelSceneDimensions()).thenReturn(new LevelSceneDimensions(3, 4));

        // Execute
        LevelSceneEditorTabFactory.fromScene(scene(3, 4));

        // Verify
        verify(target).setSpawnPoint(of(3, 4));
    }

    @Test
    @DisplayName("A spawn point stored on the bottom row is dropped")
    void aBottomRowSpawnPointIsDropped() {
        // Prepare
        when(target.getLevelSceneDimensions()).thenReturn(new LevelSceneDimensions(3, ROWS - 1));

        // Execute
        LevelSceneEditorTabFactory.fromScene(scene(3, ROWS - 1));

        // Verify
        verify(target).setSpawnPoint(null);
    }

    private LevelScene scene(final int column, final int row) {
        final LevelScene scene = new LevelScene();
        scene.setDimensions(new LevelSceneDimensions(COLUMNS, ROWS));
        scene.setSpawnPoint(of(column, row));
        return scene;
    }
}
