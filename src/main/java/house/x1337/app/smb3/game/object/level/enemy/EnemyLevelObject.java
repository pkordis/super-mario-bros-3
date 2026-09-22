package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.enemy.EnemyMode;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.object.level.BakedLayerPainter;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;

public interface EnemyLevelObject extends ActiveLevelObject, BakedLayerPainter {
    EnemyMode getMode();
    void motionUpdate();
    boolean isExpired();
    void setExpired(boolean expired);
    void spawnIntoScene();
    boolean isSpawnedIntoScene();
    DimensionsPixels getBoundsPixels();
    void hurt(LevelScenePlayer levelScenePlayer);

    /**
     * Whether this enemy still takes part in collision at all. An enemy in a dying or defeated state —
     * a flattened Goomba seeing out its squish timer — is intangible: its manager leaves it out of the
     * broadphase, so nothing can reach it and it can reach nothing.
     *
     * <p>This is one flag rather than several because the ROM gets the same effect structurally: a
     * defeated object's state routine never calls {@code Player_HitEnemy}, is skipped by
     * {@code Object_BumpOffOthers}' {@code OBJSTATE_NORMAL} test, and is not a tail-attack target. Every
     * one of those follows from simply not being in the broadphase.
     *
     * @return {@code true} while the enemy is a live participant in collision
     */
    default boolean isHittable() {
        return true;
    }

    /**
     * The score caption this enemy owes the player, or {@code null} when it owes none. Set when the enemy
     * is defeated and taken exactly once by its manager's {@code postCollision}, which spawns the caption
     * and clears it.
     *
     * <p>The value lives on the enemy rather than in the manager because the ROM's award is per-object
     * and per-kill-tally ({@code Score_Get100PlusPts} adds the tally to a base of 100), so a shared
     * constant in the manager would be wrong as soon as a second enemy type arrives.
     */
    default Reward getPendingScoreReward() {
        return null;
    }

    default void setPendingScoreReward(final Reward reward) {
    }

    @Override
    default AxisAlignedBoundingBox getBounds() {
        return AxisAlignedBoundingBox.ofSize(getPixelX(), getPixelY(), getBoundsPixels());
    }

    @Override
    default boolean isCollidable() {
        return false;
    }

    @Override
    default boolean resolvesDirectionalPlayerCollision() {
        return true;
    }

    @Override
    default void onCollisionWith(final LevelScenePlayer levelScenePlayer) {
        // Deliberately empty - directional response arrives via onCollisionFrom* (see interface Javadoc).
    }
}
