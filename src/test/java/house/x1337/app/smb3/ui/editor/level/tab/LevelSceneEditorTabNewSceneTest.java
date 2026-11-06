package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.portal.PipePortal;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyLevelSceneGridStamper;
import house.x1337.app.smb3.ui.editor.level.tile.palette.TilePalettePanel;
import house.x1337.app.smb3.ui.service.PaletteSelectionService;
import house.x1337.app.smb3.util.factory.LevelSceneImportRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Comparator;

import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.AIR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * A blank scene starts with every layer, so enemies and virtual objects can be placed in it straight away, and with
 * no pipe portals left over from whatever the tab held before.
 */
class LevelSceneEditorTabNewSceneTest {
    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private LevelSceneEditorTab tab;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(eq(LevelSceneEditorGrid.class), any()))
            .thenReturn(mock(LevelSceneEditorGrid.class));
        tab = new LevelSceneEditorTab(
            mock(LevelSceneImportRouter.class),
            mock(TilePalettePanel.class),
            new PaletteSelectionService(),
            mock(EnemyLevelSceneGridStamper.class)
        );
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A blank scene has all six layers bottom to top, with Air active")
    void blankSceneHasEveryLayer() {
        // Execute
        tab.newScene(5, 7);

        // Verify
        final LevelSceneLayerType[] expected = Arrays.stream(LevelSceneLayerType.values())
            .sorted(Comparator.comparingInt(LevelSceneLayerType::getOrder))
            .toArray(LevelSceneLayerType[]::new);
        assertThat(tab.getLayers()).extracting(LevelSceneLayer::getType).containsExactly(expected);
        assertThat(tab.getLayers()).allSatisfy(layer -> {
            assertThat(layer.isVisible()).isTrue();
            assertThat(layer.getTiles()).hasDimensions(5, 7);
        });
        assertThat(tab.getActiveLayer().getType()).isEqualTo(AIR);
    }

    @Test
    @DisplayName("A new or cleared scene has no pipe portals")
    void newAndClearedScenesHaveNoPortals() {
        // Prepare
        tab.newScene(5, 7);
        tab.getPipePortals().add(PipePortal.builder().build());

        // Execute & Verify
        tab.clearScene();
        assertThat(tab.getPipePortals()).isEmpty();
        tab.getPipePortals().add(PipePortal.builder().build());
        tab.newScene(5, 7);
        assertThat(tab.getPipePortals()).isEmpty();
    }
}
