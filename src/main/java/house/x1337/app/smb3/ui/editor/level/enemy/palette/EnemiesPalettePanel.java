package house.x1337.app.smb3.ui.editor.level.enemy.palette;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.service.EnemyStampService;
import house.x1337.app.smb3.ui.editor.level.palette.LayerGatedToggleButton;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTabSystem;
import house.x1337.app.smb3.ui.editor.level.tile.palette.core.ComponentsBuilder;
import house.x1337.app.smb3.ui.service.PaletteSelectionService;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.Map;

import static house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyButton.OWNING_LAYER;
import static java.awt.BorderLayout.CENTER;
import static java.awt.Font.ITALIC;
import static java.lang.Integer.MAX_VALUE;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.UIManager.getColor;

/**
 * The Enemies palette: one section per enemy, headed by the enemy's name, holding that enemy's parts
 * assembled back into the single picture it was imported as. Selecting one arms the level grid to stamp
 * the whole enemy at the clicked cell.
 *
 * <p>An enemy only belongs on the {@code NON_PLAYABLE_CHARACTERS} layer. While another layer is active
 * every entry here is marked out of the active layer — greyed, but still clickable, and a click switches
 * to the NPC layer before arming the enemy (see {@link LayerGatedToggleButton}). An armed enemy is released
 * the moment the active layer moves away, which stops a stale selection from stamping parts into, say, the
 * static-environment layer. A brand-new blank scene has no NPC layer at all (it starts with Air and Land
 * Decorations): there is nothing to switch to, so there the entries are disabled outright, with a hint
 * saying why.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class EnemiesPalettePanel extends JPanel implements ComponentsBuilder {
    private final EnemyStampService enemyStampService;
    private final PaletteSelectionService paletteSelectionService;
    private final LevelSceneEditorTabSystem tabSystem;
    private final EnemyTilesAssembler enemyTilesAssembler;

    private final JPanel enemiesPanel = buildTilesPanel();
    private final JScrollPane scrollPane = buildScrollPane(enemiesPanel);
    /** Package-visible so the gating hint can be asserted without building the panel's Swing context. */
    @Getter(AccessLevel.PACKAGE)
    private final JLabel hintLabel = new JLabel();
    private final Map<String, EnemyButton> buttonsByEnemyId = new LinkedHashMap<>();

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
        enemiesPanel.add(hintLabel);

        enemyStampService.findAll().forEach(this::addEnemy);

        // The active layer can change with the tab, and a new tab starts on Air.
        tabSystem.addChangeListener(event -> syncEnabledState());
        syncEnabledState();
    }

    /**
     * Adds an enemy's section, or refreshes its image if it is already listed. Called both when the
     * palette is first built and straight after an enemy is created, so a new enemy is placeable without
     * restarting the editor.
     */
    public void addEnemy(final EnemyStamp enemy) {
        if (!enemy.isWellFormed()) {
            // One unusable document must not stop the editor from opening.
            log.warn("Skipping enemy {} in the palette: it is not well formed ({}).", enemy.getId(), enemy);
            return;
        }
        final EnemyButton existing = buttonsByEnemyId.get(enemy.getId());
        if (existing != null) {
            existing.setThumbnail(enemyTilesAssembler.toIcon(enemy));
            return;
        }

        final EnemyButton button = EnemyButton.fromEnemy(enemy);
        buttonsByEnemyId.put(enemy.getId(), button);

        final JPanel grid = buildGridPanel();
        grid.add(button);
        enemiesPanel.add(buildGroupHeader(enemy.getName()));
        enemiesPanel.add(grid);
        enemiesPanel.revalidate();
        enemiesPanel.repaint();
        syncEnabledState();
    }

    /**
     * Re-gates every entry against the active tab: placeable while the NPC layer is active, out of the
     * active layer (greyed but clickable) while another layer is, and disabled outright when the scene has
     * no NPC layer to switch to. An armed enemy is released whenever the NPC layer is not the active one.
     */
    public void syncEnabledState() {
        final LevelSceneEditorTab tab = tabSystem.getActiveTab();
        final boolean layerExists = hasLayer(tab, OWNING_LAYER);
        final boolean placeable = isLayerActive(tab, OWNING_LAYER);
        for (final EnemyButton button : buttonsByEnemyId.values()) {
            button.setEnabled(layerExists);
            button.markOutOfActiveLayer(layerExists && !placeable);
        }
        if (!placeable && paletteSelectionService.getSelectedEnemy() != null) {
            paletteSelectionService.clearSelection();
        }
        hintLabel.setText(resolveHint(tab, layerExists, placeable));
        hintLabel.setVisible(!placeable || buttonsByEnemyId.isEmpty());
        enemiesPanel.revalidate();
        enemiesPanel.repaint();
    }

    private String resolveHint(final LevelSceneEditorTab tab, final boolean layerExists, final boolean placeable) {
        if (tab == null) {
            return "<html>Open a level scene to place enemies.</html>";
        }
        if (!layerExists) {
            return "<html>This scene has no<br><b>"
                + OWNING_LAYER.getLabel()
                + "</b> layer,<br>so enemies cannot be placed in it.</html>";
        }
        if (!placeable) {
            return "<html>Enemies are placed on the<br><b>"
                + OWNING_LAYER.getLabel()
                + "</b> layer.<br>Clicking one switches to it.</html>";
        }
        if (buttonsByEnemyId.isEmpty()) {
            return "<html>No enemies yet. Build one from<br>"
                + "Level Objects &gt; Enemies &gt; Create from Image...</html>";
        }
        return " ";
    }

    private static boolean hasLayer(final LevelSceneEditorTab tab, final LevelSceneLayerType type) {
        if (tab == null || tab.getLayers() == null) {
            return false;
        }
        return tab.getLayers().stream().anyMatch(layer -> layer.getType() == type);
    }

    private static boolean isLayerActive(final LevelSceneEditorTab tab, final LevelSceneLayerType type) {
        if (tab == null) {
            return false;
        }
        final LevelSceneLayer activeLayer = tab.getActiveLayer();
        return activeLayer != null && activeLayer.getType() == type;
    }
}
