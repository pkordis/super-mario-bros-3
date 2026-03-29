package house.x1337.app.smb3.ui.editor.level.scene.menu.scene.level;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.ui.editor.level.scene.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.scene.tab.LevelSceneEditorTabSystem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static java.util.Comparator.comparingInt;

@Singleton
@RequiredArgsConstructor
public class ActiveLayerMenu extends JMenu {
    private final LevelSceneEditorTabSystem tabSystem;
    private final ButtonGroup buttonGroup = new ButtonGroup();
    private final Map<LevelSceneLayerType, JRadioButtonMenuItem> radioButtons =
        new EnumMap<>(LevelSceneLayerType.class);

    @PostConstruct
    void init() {
        setText("Active Layer");

        final List<LevelSceneLayerType> sorted = Arrays.stream(LevelSceneLayerType.values())
            .sorted(comparingInt(LevelSceneLayerType::getOrder))
            .toList();

        for (final LevelSceneLayerType type : sorted) {
            final JRadioButtonMenuItem item = new JRadioButtonMenuItem(type.getLabel());
            item.addActionListener(e -> setActiveLayer(type));
            buttonGroup.add(item);
            radioButtons.put(type, item);
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

    private void setActiveLayer(final LevelSceneLayerType type) {
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
                tab.setActiveLayer(layer);
                break;
            }
        }
    }

    private void syncFromActiveTab() {
        final LevelSceneEditorTab tab = tabSystem.getActiveTab();
        final List<LevelSceneLayer> layers = (tab != null) ? tab.getLayers() : null;
        final LevelSceneLayer activeLayer = (tab != null) ? tab.getActiveLayer() : null;

        for (final Map.Entry<LevelSceneLayerType, JRadioButtonMenuItem> entry : radioButtons.entrySet()) {
            final LevelSceneLayerType type = entry.getKey();
            final JRadioButtonMenuItem item = entry.getValue();
            if (layers != null) {
                boolean found = false;
                for (final LevelSceneLayer layer : layers) {
                    if (layer.getType() == type) {
                        found = true;
                        break;
                    }
                }
                item.setEnabled(found);
                item.setSelected(activeLayer != null && activeLayer.getType() == type);
            } else {
                item.setEnabled(false);
                item.setSelected(false);
            }
        }
    }
}
