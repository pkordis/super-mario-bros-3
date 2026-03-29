package house.x1337.app.smb3.ui.editor.level.scene.menu.scene.level;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.ui.editor.level.scene.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.scene.tab.LevelSceneEditorTabSystem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static java.util.Comparator.comparingInt;

@Singleton
@RequiredArgsConstructor
public class LayerVisibilityMenu extends JMenu {
    private final LevelSceneEditorTabSystem tabSystem;
    private final Map<LevelSceneLayerType, JCheckBoxMenuItem> checkboxes = new EnumMap<>(LevelSceneLayerType.class);

    @PostConstruct
    void init() {
        setText("Layer Visibility");

        final List<LevelSceneLayerType> sorted = Arrays.stream(LevelSceneLayerType.values())
            .sorted(comparingInt(LevelSceneLayerType::getOrder))
            .toList();

        for (final LevelSceneLayerType type : sorted) {
            final JCheckBoxMenuItem item = new JCheckBoxMenuItem(type.getLabel(), true);
            item.addActionListener(e -> applyVisibility(type, item.isSelected()));
            checkboxes.put(type, item);
            add(item);
        }

        tabSystem.addChangeListener(e -> syncFromActiveTab());
        addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(final MenuEvent e) {
                syncFromActiveTab();
            }

            @Override
            public void menuDeselected(final MenuEvent e) {
                // no-op
            }

            @Override
            public void menuCanceled(final MenuEvent e) {
                // no-op
            }
        });
    }

    private void applyVisibility(final LevelSceneLayerType type, final boolean visible) {
        final LevelSceneEditorTab tab = tabSystem.getActiveTab();
        if (tab == null) {
            return;
        }
        final List<LevelSceneLayer> layers = tab.getLayers();
        if (layers == null) {
            return;
        }
        for (final LevelSceneLayer layer : layers) {
            if (layer.getType() == type) {
                layer.setVisible(visible);
                break;
            }
        }
        tab.getLevelSceneEditorGrid().repaint();
    }

    private void syncFromActiveTab() {
        final LevelSceneEditorTab tab = tabSystem.getActiveTab();
        final List<LevelSceneLayer> layers = (tab != null) ? tab.getLayers() : null;

        for (final Map.Entry<LevelSceneLayerType, JCheckBoxMenuItem> entry : checkboxes.entrySet()) {
            final LevelSceneLayerType type = entry.getKey();
            final JCheckBoxMenuItem item = entry.getValue();
            if (layers != null) {
                boolean found = false;
                for (final LevelSceneLayer layer : layers) {
                    if (layer.getType() == type) {
                        item.setSelected(layer.isVisible());
                        item.setEnabled(true);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    item.setSelected(true);
                    item.setEnabled(false);
                }
            } else {
                item.setSelected(true);
                item.setEnabled(false);
            }
        }
    }
}
