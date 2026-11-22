package house.x1337.app.smb3.game.object.level.enemy.base;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.enemy.EnemyMode;
import house.x1337.app.smb3.game.object.level.ActiveLevelObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.PixelPosition;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_ENEMY;
import static house.x1337.app.smb3.GameConstants.Z_OFFSET_ENEMY_DETAIL;
import static house.x1337.app.smb3.enumeration.Reward.SCORE_100;
import static house.x1337.app.smb3.enumeration.SoundEffect.KOOPA_SHELL;
import static java.lang.Math.round;

public interface EnemyLevelObject extends ActiveLevelObject {
    EnemyMode getMode();
    void motionUpdate();
    boolean isExpired();
    void setExpired(boolean expired);
    void spawnIntoScene();
    boolean isSpawnedIntoScene();
    void setSpawnedIntoScene(boolean spawnedIntoScene);
    DimensionsPixels getBoundsPixels();
    void hurt(LevelScenePlayer levelScenePlayer);

    default boolean isHittable() {
        return true;
    }

    default boolean canHurtPlayer() {
        return isHittable();
    }

    default boolean isDefeated() {
        return false;
    }

    default boolean hasAdoptedASlot() {
        return false;
    }

    default boolean isSlotless() {
        return false;
    }

    default Reward getPendingScoreReward() {
        return null;
    }

    default void setPendingScoreReward(final Reward reward) {
    }

    default float getBaseDepth() {
        return Z_DEPTH_ENEMY;
    }

    default void setBaseDepth(final float baseDepth) {
    }

    default float depthBehindBody() {
        return getBaseDepth() - Z_OFFSET_ENEMY_DETAIL;
    }

    default float depthInFrontOfBody() {
        return getBaseDepth() + Z_OFFSET_ENEMY_DETAIL;
    }

    default void placePart(
        final Geometry part,
        final float worldX,
        final float worldY,
        final float depth
    ) {
        if (part != null) {
            part.setLocalTranslation(worldX, worldY, depth);
        }
    }

    default Geometry detachPart(final Geometry part) {
        if (part != null) {
            getGameEngine().getRootNode().detachChild(part);
        }
        return null;
    }

    @Override
    default AxisAlignedBoundingBox getBounds() {
        return AxisAlignedBoundingBox.ofSize(getPosition(), getBoundsPixels());
    }

    void setPosition(PixelPosition position);

    /** Moves the enemy by {@code pixels}, in level pixels: the spawner places it within its cell this way. */
    default void shiftPosition(final Coordinates pixels) {
        setPosition(getPosition().plus(pixels.x(), pixels.y()));
    }
    /**
     * An enemy's stomp-depth reference is the Y it tracks, not its collision box top and not its sprite top:
     * the stomp is measured from the tracked Y regardless of how either is offset from it. An enemy that insets
     * its box (a Goomba's box top sits {@code +2} below) must keep this at the tracked Y or it would
     * silently stomp from the wrong depth.
     *
     * <p>Do not read "tracked Y" as "sprite top". For a Goomba the two coincide; for a <b>Koopa
     * Troopa</b> they do not - the tracked Y is the top of its shell and its head is drawn above
     * that at negative offsets. The return value below is still right for it, because the reference is the
     * tracked Y either way.
     *
     * @return the enemy's tracked Y
     */
    @Override
    default double getStompReferenceTop() {
        return getPosition().y();
    }

    @Override
    default void expire() {
        setExpired(true);
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

    default boolean isWinged() {
        return false;
    }

    default boolean isGrounded() {
        return true;
    }

    /**
     * A sliding player kills a stompable enemy outright, knocking it away with half the player's speed for 100
     * points, unless it is a winged enemy off the ground; that one, like an enemy that cannot be stomped, hurts the
     * player instead. See {@code kb/slopes.md}, "Sliding into enemies".
     */
    @Override
    default void onSlidInto(final LevelScenePlayer levelScenePlayer) {
        if (!canBeStomped() || (isWinged() && !isGrounded())) {
            onPlayerOverlap(levelScenePlayer);
            return;
        }
        if (!isHittable()) {
            return;
        }
        final int playerXVelocity = (int) round(levelScenePlayer.getPosition().getDX() * TILE_SPRITE_SIZE);
        onStruckByKickedObject(getVelocity().x() + (playerXVelocity >> 1));
        setPendingScoreReward(SCORE_100);
        getSoundPlayer().play(KOOPA_SHELL);
    }
}
