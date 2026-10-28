package house.x1337.app.smb3.ui.editor.level.enemy.palette;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.enumeration.TileType;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.ui.editor.level.palette.LayerGatedToggleButton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.awt.Insets;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

/**
 * An Enemies palette entry: one whole enemy, stamped as a grid of parts. Every enemy belongs on the layer
 * its parts do, so picking one while another layer is active switches to that layer first — see
 * {@link LayerGatedToggleButton} for the shared click flow.
 */
@Getter
@Prototype
@RequiredArgsConstructor
public class EnemyButton extends LayerGatedToggleButton {
    public static final LevelSceneLayerType OWNING_LAYER = TileType.ENEMY_PART.getLevelSceneLayerOwningType();

    private final EnemyTilesAssembler enemyTilesAssembler = getBean(EnemyTilesAssembler.class);
    private final EnemyStamp enemy;

    public static EnemyButton fromEnemy(final EnemyStamp enemy) {
        final EnemyButton button = getBean(EnemyButton.class, enemy);
        button.setThumbnail(button.enemyTilesAssembler.toIcon(enemy));
        button.setMargin(new Insets(2, 2, 2, 2));
        button.setBaseTooltip(buildTooltip(enemy));
        return button;
    }

    @Override
    public LevelSceneLayerType getOwningLayer() {
        return OWNING_LAYER;
    }

    private static String buildTooltip(final EnemyStamp enemy) {
        return "%s — %d x %d tiles, anchored at row %d, column %d".formatted(
            enemy.getEnemyType().getLabel(),
            enemy.getColumns(),
            enemy.getRows(),
            enemy.getRenderingStarterRow(),
            enemy.getRenderingStarterColumn()
        );
    }
}
