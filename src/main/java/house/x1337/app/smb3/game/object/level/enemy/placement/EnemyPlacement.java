package house.x1337.app.smb3.game.object.level.enemy.placement;

import house.x1337.app.smb3.enumeration.enemy.EnemyType;
import house.x1337.app.smb3.model.game.Offset;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
final class EnemyPlacement {
    private final EnemyType enemyType;
    private final Offset anchor;
    private boolean spawned;

    int column() {
        return anchor.x();
    }

    void markSpawned() {
        spawned = true;
    }

    void release() {
        spawned = false;
    }
}
