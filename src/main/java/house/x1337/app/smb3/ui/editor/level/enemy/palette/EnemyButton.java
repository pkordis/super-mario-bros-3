package house.x1337.app.smb3.ui.editor.level.enemy.palette;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.model.game.enemy.Enemy;
import house.x1337.app.smb3.ui.service.SelectedTileService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.swing.JToggleButton;
import java.awt.Insets;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static java.awt.Color.GRAY;
import static javax.swing.BorderFactory.createLineBorder;

@Getter
@Prototype
@RequiredArgsConstructor
public class EnemyButton extends JToggleButton {
    private final EnemyTilesAssembler enemyTilesAssembler = getBean(EnemyTilesAssembler.class);
    private final Enemy enemy;

    public static EnemyButton fromEnemy(final Enemy enemy) {
        final EnemyButton button = getBean(EnemyButton.class, enemy);
        final SelectedTileService selectedTileService = getBean(SelectedTileService.class);
        button.setIcon(button.enemyTilesAssembler.toIcon(enemy));
        button.setMargin(new Insets(2, 2, 2, 2));
        button.setBorder(createLineBorder(GRAY, 1));
        button.setToolTipText(buildTooltip(enemy));
        button.addActionListener(event -> {
            if (button.isSelected()) {
                selectedTileService.select(button);
            } else {
                selectedTileService.clearSelection();
            }
        });
        return button;
    }

    public void markSelected(final boolean selected) {
        setSelected(selected);
        setBorder(createLineBorder(selected ? GRAY.brighter() : GRAY, 1));
    }

    private static String buildTooltip(final Enemy enemy) {
        return "%s — %d x %d tiles, anchored at row %d, column %d".formatted(
            enemy.getEnemyType().getLabel(),
            enemy.getColumns(),
            enemy.getRows(),
            enemy.getRenderingStarterRow(),
            enemy.getRenderingStarterColumn()
        );
    }
}
