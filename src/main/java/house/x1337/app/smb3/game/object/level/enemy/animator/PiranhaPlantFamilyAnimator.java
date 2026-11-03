package house.x1337.app.smb3.game.object.level.enemy.animator;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.game.object.level.enemy.family.PiranhaPlantFamilyEnemy;
import house.x1337.app.smb3.model.game.enemy.EnemyAnimatorAssets;

public interface PiranhaPlantFamilyAnimator<A extends EnemyAnimatorAssets, E extends PiranhaPlantFamilyEnemy>
    extends
        EnemyAnimator<A, E> {
    Geometry buildStemSprite(final E e);
}
