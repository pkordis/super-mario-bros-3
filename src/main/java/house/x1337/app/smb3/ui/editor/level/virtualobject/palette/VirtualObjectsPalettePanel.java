package house.x1337.app.smb3.ui.editor.level.virtualobject.palette;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTabSystem;
import house.x1337.app.smb3.ui.editor.level.tile.palette.core.ComponentsBuilder;
import house.x1337.app.smb3.ui.service.PaletteSelectionService;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.EnumMap;
import java.util.Map;

import static house.x1337.app.smb3.ui.editor.level.virtualobject.palette.VirtualObjectButton.OWNING_LAYER;
import static java.awt.BorderLayout.CENTER;
import static java.awt.Font.ITALIC;
import static java.lang.Integer.MAX_VALUE;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.UIManager.getColor;
import static lombok.AccessLevel.PACKAGE;

@Getter(PACKAGE)
@Singleton
@RequiredArgsConstructor
public class VirtualObjectsPalettePanel extends JPanel implements ComponentsBuilder {
    private final JPanel objectsPanel = buildTilesPanel();
    private final JScrollPane scrollPane = buildScrollPane(objectsPanel);
    private final JLabel hintLabel = new JLabel();
    private final Map<VirtualObjectType, VirtualObjectButton> buttonsByType = new EnumMap<>(VirtualObjectType.class);
    private final PaletteSelectionService paletteSelectionService;
    private final LevelSceneEditorTabSystem tabSystem;

    @PostConstruct
    void init() {
        setLayout(new BorderLayout());
        add(scrollPane, CENTER);
        setMinimumSize(new Dimension(200, 100));
        setPreferredSize(new Dimension(220, 400));

        hintLabel.setFont(hintLabel.getFont().deriveFont(ITALIC, 11f));
        hintLabel.setForeground(getColor("Label.disabledForeground"));
        hintLabel.setAlignmentX(LEFT_ALIGNMENT);
        hintLabel.setBorder(createEmptyBorder(4, 2, 8, 2));
        hintLabel.setMaximumSize(new Dimension(MAX_VALUE, 40));
        objectsPanel.add(hintLabel);

        for (final VirtualObjectType type : VirtualObjectType.values()) {
            final VirtualObjectButton button = VirtualObjectButton.fromType(type);
            buttonsByType.put(type, button);
            final JPanel grid = buildGridPanel();
            grid.add(button);
            objectsPanel.add(buildGroupHeader(type.getLabel()));
            objectsPanel.add(grid);
        }

        // The active layer can change with the tab, and a new tab starts on Air.
        tabSystem.addChangeListener(event -> syncEnabledState());
        syncEnabledState();
    }

    /**
     * Re-gates every entry against the active tab: placeable while the NPC layer is active, out of the active layer
     * (greyed but clickable) while another layer is, and disabled when the scene has no NPC layer to switch to. An
     * armed stamp is released whenever the NPC layer is not the active one.
     */
    public void syncEnabledState() {
        final LevelSceneEditorTab tab = tabSystem.getActiveTab();
        final boolean layerExists = hasNpcLayer(tab);
        final boolean placeable = isNpcLayerActive(tab);
        for (final VirtualObjectButton button : buttonsByType.values()) {
            button.setEnabled(layerExists);
            button.markOutOfActiveLayer(layerExists && !placeable);
        }
        if (!placeable && paletteSelectionService.getSelectedVirtualObject() != null) {
            paletteSelectionService.clearSelection();
        }
        hintLabel.setText(resolveHint(tab, layerExists));
        hintLabel.setVisible(!placeable);
        objectsPanel.revalidate();
        objectsPanel.repaint();
    }

    private static String resolveHint(final LevelSceneEditorTab tab, final boolean layerExists) {
        if (tab == null) {
            return "<html>Open a level scene to place virtual objects.</html>";
        }
        if (!layerExists) {
            return "<html>This scene has no<br><b>"
                + OWNING_LAYER.getLabel()
                + "</b> layer,<br>so virtual objects cannot be placed in it.</html>";
        }
        return "<html>Virtual objects are placed on the<br><b>"
            + OWNING_LAYER.getLabel()
            + "</b> layer.<br>Clicking one switches to it.</html>";
    }

    private static boolean hasNpcLayer(final LevelSceneEditorTab tab) {
        if (tab == null || tab.getLayers() == null) {
            return false;
        }
        return tab.getLayers().stream().anyMatch(layer -> layer.getType() == OWNING_LAYER);
    }

    private static boolean isNpcLayerActive(final LevelSceneEditorTab tab) {
        if (tab == null) {
            return false;
        }
        final LevelSceneLayer activeLayer = tab.getActiveLayer();
        return activeLayer != null && activeLayer.getType() == OWNING_LAYER;
    }
}
