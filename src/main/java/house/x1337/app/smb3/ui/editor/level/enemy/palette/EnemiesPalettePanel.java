package house.x1337.app.smb3.ui.editor.level.enemy.palette;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.level.scene.LevelScene.LevelSceneLayer;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.service.EnemyStampService;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.ui.editor.level.tab.LevelSceneEditorTabSystem;
import house.x1337.app.smb3.ui.editor.level.tile.palette.core.ComponentsBuilder;
import house.x1337.app.smb3.ui.service.SelectedTileService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.Map;

import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
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
 * <p>An enemy only belongs on the {@code NON_PLAYABLE_CHARACTERS} layer, so every entry here is disabled
 * unless that layer is the active one — and an armed enemy is released the moment the active layer moves
 * away, which stops a stale selection from stamping parts into, say, the static-environment layer. A
 * brand-new blank scene has no NPC layer at all (it starts with Air and Land Decorations), so the palette
 * stays disabled until the scene has one.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class EnemiesPalettePanel extends JPanel implements ComponentsBuilder {
    private final EnemyStampService enemyStampService;
    private final SelectedTileService selectedTileService;
    private final LevelSceneEditorTabSystem tabSystem;
    private final EnemyTilesAssembler enemyTilesAssembler;

    private final JPanel enemiesPanel = buildTilesPanel();
    private final JScrollPane scrollPane = buildScrollPane(enemiesPanel);
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
            existing.setIcon(enemyTilesAssembler.toIcon(enemy));
            existing.repaint();
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
     * Enables the palette only while the NPC layer is active, and releases an armed enemy when it is not.
     */
    public void syncEnabledState() {
        final boolean placeable = isNonPlayableCharactersLayerActive();
        buttonsByEnemyId.values().forEach(button -> button.setEnabled(placeable));
        if (!placeable && selectedTileService.getSelectedEnemy() != null) {
            selectedTileService.clearSelection();
        }
        hintLabel.setText(resolveHint(placeable));
        hintLabel.setVisible(!placeable || buttonsByEnemyId.isEmpty());
        enemiesPanel.revalidate();
        enemiesPanel.repaint();
    }

    private String resolveHint(final boolean placeable) {
        if (!placeable) {
            return "<html>Enemies can only be placed on the<br><b>"
                + NON_PLAYABLE_CHARACTERS.getLabel()
                + "</b> layer.<br>Switch to it from Level Scene &gt; Active Layer.</html>";
        }
        if (buttonsByEnemyId.isEmpty()) {
            return "<html>No enemies yet. Build one from<br>"
                + "Level Objects &gt; Enemies &gt; Create from Image...</html>";
        }
        return " ";
    }

    private boolean isNonPlayableCharactersLayerActive() {
        final LevelSceneEditorTab tab = tabSystem.getActiveTab();
        if (tab == null) {
            return false;
        }
        final LevelSceneLayer activeLayer = tab.getActiveLayer();
        return activeLayer != null && activeLayer.getType() == NON_PLAYABLE_CHARACTERS;
    }
}
