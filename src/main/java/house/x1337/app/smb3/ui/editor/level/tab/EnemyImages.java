package house.x1337.app.smb3.ui.editor.level.tab;

import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.ui.editor.level.enemy.palette.EnemyTilesAssembler;
import lombok.RequiredArgsConstructor;

import java.awt.image.BufferedImage;
import java.util.IdentityHashMap;
import java.util.Map;

/** Each enemy stamp assembled into one image, once per stamp instance: a re-saved stamp is a new instance. */
@RequiredArgsConstructor
final class EnemyImages {
    private final Map<EnemyStamp, BufferedImage> images = new IdentityHashMap<>();
    private final EnemyTilesAssembler enemyTilesAssembler;

    BufferedImage of(final EnemyStamp stamp) {
        return images.computeIfAbsent(stamp, enemyTilesAssembler::assemble);
    }

    void clear() {
        images.clear();
    }
}
