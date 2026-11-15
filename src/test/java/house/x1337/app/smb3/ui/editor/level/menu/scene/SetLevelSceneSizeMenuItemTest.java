package house.x1337.app.smb3.ui.editor.level.menu.scene;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.ui.editor.GameEditorWindow;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneTabs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static house.x1337.app.smb3.model.game.Coordinates.of;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A chosen size is applied only when it cuts nothing; otherwise the author is shown what is in the way and the
 * scene keeps its size.
 */
class SetLevelSceneSizeMenuItemTest {
    private final LevelSceneResizeRefusal refusal = mock(LevelSceneResizeRefusal.class);
    private final LevelSceneEditorTab tab = mock(LevelSceneEditorTab.class);
    private final GameEditorWindow window = mock(GameEditorWindow.class);
    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private SetLevelSceneSizeMenuItem menuItem;

    @BeforeEach
    void prepare() {
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory.when(() -> StaticBeanFactory.getBean(GameEditorWindow.class)).thenReturn(window);
        when(tab.getColumns()).thenReturn(16);
        when(tab.getRows()).thenReturn(15);
        menuItem = new SetLevelSceneSizeMenuItem(mock(LevelSceneTabs.class), new LevelSceneSizeRules(), refusal);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("A size that cuts nothing resizes the scene")
    void resizesWhenNothingIsCut() {
        // Prepare
        when(tab.findContentCutBy(16, 20)).thenReturn(Map.of());

        // Execute
        menuItem.resize(tab, new LevelSceneDimensions(16, 20));

        // Verify
        verify(tab).resizeScene(16, 20);
        verify(refusal, never()).show(any(), anyMap());
    }

    @Test
    @DisplayName("A size that would cut content is refused, listing what is in the way, and the size stays")
    void refusesWhenContentIsCut() {
        // Prepare
        final Map<String, List<Coordinates>> cut = Map.of("Static Environment", List.of(of(20, 3)));
        when(tab.getColumns()).thenReturn(24);
        when(tab.findContentCutBy(16, 15)).thenReturn(cut);

        // Execute
        menuItem.resize(tab, new LevelSceneDimensions(16, 15));

        // Verify
        verify(refusal).show(window, cut);
        verify(tab, never()).resizeScene(anyInt(), anyInt());
    }

    @Test
    @DisplayName("Keeping the current size changes nothing")
    void sameSizeDoesNothing() {
        // Execute
        menuItem.resize(tab, new LevelSceneDimensions(16, 15));

        // Verify
        verify(tab, never()).findContentCutBy(anyInt(), anyInt());
        verify(tab, never()).resizeScene(anyInt(), anyInt());
    }
}
