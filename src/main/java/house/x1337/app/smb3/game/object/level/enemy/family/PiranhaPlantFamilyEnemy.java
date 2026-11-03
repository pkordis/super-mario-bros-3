package house.x1337.app.smb3.game.object.level.enemy.family;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.enumeration.enemy.PiranhaPlantState;
import house.x1337.app.smb3.game.object.level.effect.PoofMotionManager;
import house.x1337.app.smb3.game.object.level.effect.TailAttackFlashMotionManager;
import house.x1337.app.smb3.game.object.level.enemy.animator.EnemyAnimator;
import house.x1337.app.smb3.game.object.level.enemy.animator.PiranhaPlantFamilyAnimator;
import house.x1337.app.smb3.game.object.level.enemy.base.EnemyLevelObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import house.x1337.app.smb3.util.CastCapable;

import static com.jme3.scene.Spatial.CullHint.Always;
import static com.jme3.scene.Spatial.CullHint.Inherit;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_PIPE_PLANT;
import static house.x1337.app.smb3.GameConstants.Z_DEPTH_POOF;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantState.HIDDEN;
import static java.lang.Math.abs;
import static java.lang.Math.floor;

public interface PiranhaPlantFamilyEnemy extends EnemyLevelObject, CastCapable {
    int ANIMATION_COUNTER_MASK = 255;
    int BOUND_BOX_LEFT_OFFSET = 2;
    int BOUND_BOX_TOP_OFFSET = 2;
    int BOUND_BOX_WIDTH = 12;
    int CENTRE_ON_PIPE_PIXELS = 8;
    int HEAD_FRAME_SHIFT = 3;
    int PART_SIZE_PIXELS = TILE_SPRITE_SIZE;
    int PIPE_TOP_BELOW_STARTER_PIXELS = TILE_SPRITE_SIZE;
    int PLAYER_TOO_CLOSE_PIXELS = 27;
    int RISE_SPEED_FIXED_POINT = 16;
    int STATE_WAIT_TICKS = 48;
    double WHAM_TAIL_OFFSET_PIXELS = 16;

    PiranhaPlantState getState();
    void setState(PiranhaPlantState state);
    int getStateTimer();
    void setStateTimer(int stateTimer);
    int getAnimationCounter();
    void setAnimationCounter(int animationCounter);
    int getHeadFrameIndex();
    void setHeadFrameIndex(int headFrameIndex);
    double getOriginY();
    int getRiseDistancePixels();
    boolean isKilled();
    void setKilled(boolean killed);
    void setPosition(PixelPosition position);
    void setVelocity(FixedPointVelocity velocity);
    Geometry getStemGeometry();
    void setStemGeometry(Geometry stemGeometry);
    void refreshHeadFrame();

    default int getStateWaitTicks() {
        return STATE_WAIT_TICKS;
    }

    default void faceThePlayer() {
        // Do nothing by default
    }

    default void attackWhileOut() {
        // Do nothing by default
    }

    @Override
    default void spawnIntoScene() {
        if (isSpawnedIntoScene()) {
            return;
        }
        final EnemyAnimator<?, ? extends PiranhaPlantFamilyEnemy> enemyAnimator = getAnimator();
        if (enemyAnimator instanceof PiranhaPlantFamilyAnimator<?, ? extends PiranhaPlantFamilyEnemy> animator) {
            final PiranhaPlantFamilyAnimator<?, PiranhaPlantFamilyEnemy> castedAnimator = checkedCast(animator);
            setSpawnedIntoScene(true);

            setSpriteGeometry(castedAnimator.buildSprite(this));
            setStemGeometry(castedAnimator.buildStemSprite(this));
            getGameEngine().getRootNode().attachChild(getSpriteGeometry());
            getGameEngine().getRootNode().attachChild(getStemGeometry());

            erasePlacementFromBakedTexture();
            positionPlantSprites();
        } else {
            throw new IllegalStateException(
                "Instances of this interface must have animators for the PiranhaPlantFamilyEnemy"
            );
        }
    }

    <T extends EnemyAnimator<?, ? extends PiranhaPlantFamilyEnemy>> T getAnimator();

    @Override
    default void motionUpdate() {
        if (isExpired()) {
            return;
        }
        if (isKilled()) {
            setExpired(true);
            return;
        }
        tickPipePlant();
        positionPlantSprites();
    }

    default void tickPipePlant() {
        if (getStateTimer() > 0) {
            setStateTimer(getStateTimer() - 1);
        }
        faceThePlayer();
        final int headFrame = (getAnimationCounter() >> HEAD_FRAME_SHIFT) & 1;
        if (headFrame != getHeadFrameIndex()) {
            setHeadFrameIndex(headFrame);
            refreshHeadFrame();
        }
        setAnimationCounter((getAnimationCounter() + 1) & ANIMATION_COUNTER_MASK);

        switch (getState()) {
            case HIDDEN -> hideInPipe();
            case EMERGING -> emerge();
            case OUT -> stayOut();
            case RETRACTING -> retract();
        }
    }

    default void hideInPipe() {
        if (getStateTimer() != 0 || isPlayerTooClose()) {
            return;
        }
        advanceState();
    }

    default void emerge() {
        if (getPosition().y() <= getOriginY() - getRiseDistancePixels()) {
            advanceState();
            return;
        }
        moveVertically(-RISE_SPEED_FIXED_POINT);
    }

    default void stayOut() {
        attackWhileOut();
        if (getStateTimer() != 0) {
            return;
        }
        advanceState();
    }

    default void retract() {
        if (getPosition().y() + 1 >= getOriginY()) {
            advanceState();
            return;
        }
        moveVertically(RISE_SPEED_FIXED_POINT);
    }

    default void advanceState() {
        setState(getState().next());
        setStateTimer(getStateWaitTicks());
    }

    default boolean isPlayerTooClose() {
        final PixelPosition position = getPosition();
        return getCollisionGrid()
            .findClosestPlayerTo(position.x(), position.y())
            .map(player -> abs(floor(player.getPosition().getX()) - floor(position.x())) <= PLAYER_TOO_CLOSE_PIXELS)
            .orElse(false);
    }

    default boolean isExposed() {
        return !isKilled() && getState() != HIDDEN;
    }

    @Override
    default AxisAlignedBoundingBox getBounds() {
        return AxisAlignedBoundingBox.ofSize(
            getPosition().plus(BOUND_BOX_LEFT_OFFSET, BOUND_BOX_TOP_OFFSET),
            getBoundsPixels()
        );
    }

    @Override
    default boolean canBeStomped() {
        return false;
    }

    @Override
    default boolean isHittable() {
        return !isKilled();
    }

    @Override
    default boolean isDefeated() {
        return isKilled();
    }

    @Override
    default boolean isKilledByRammedObject() {
        return isExposed();
    }

    @Override
    default void hurt(final LevelScenePlayer levelScenePlayer) {
        if (!canHurtPlayer()) {
            return;
        }
        levelScenePlayer.getHurtBy(this);
    }

    @Override
    default void onCollisionFromAbove(final LevelScenePlayer levelScenePlayer) {
        hurt(levelScenePlayer);
    }

    @Override
    default void onCollisionFromBelow(final LevelScenePlayer levelScenePlayer) {
        hurt(levelScenePlayer);
    }

    @Override
    default void onPlayerOverlap(final LevelScenePlayer levelScenePlayer) {
        hurt(levelScenePlayer);
    }

    @Override
    default void onTailAttack(final LevelScenePlayer levelScenePlayer) {
        if (!isExposed()) {
            return;
        }
        final double playerX = levelScenePlayer.getPosition().getX();
        final boolean playerOnTheLeft = playerX < getPosition().x();
        final double tailSideOffset = playerOnTheLeft ? WHAM_TAIL_OFFSET_PIXELS : -WHAM_TAIL_OFFSET_PIXELS;
        getBean(TailAttackFlashMotionManager.class).spawn(
            getGameEngine(),
            Offset.of(
                playerX + tailSideOffset,
                levelScenePlayer.getPosition().getY() + WHAM_TAIL_OFFSET_PIXELS
            )
        );
        setPendingScoreReward(levelScenePlayer.currentKillChain());
        killInAPoof();
    }

    @Override
    default void onStruckByKickedObject(final int knockXVelocityFixedPoint) {
        if (!isExposed()) {
            return;
        }
        killInAPoof();
    }

    default void killInAPoof() {
        setKilled(true);
        getBean(PoofMotionManager.class).spawnPoofDeath(
            getGameEngine(),
            partWorldOffset(getPosition().y(), Z_DEPTH_POOF)
        );
    }

    /**
     * Places the head over the stem, both at the plant's X, in the baked layers' bucket behind the pipe.
     */
    default void positionPlantSprites() {
        final Geometry head = getSpriteGeometry();
        final Geometry stem = getStemGeometry();
        if (head == null || stem == null) {
            return;
        }
        final boolean drawn = isExposed();
        head.setCullHint(drawn ? Inherit : Always);
        stem.setCullHint(drawn ? Inherit : Always);
        if (!drawn) {
            return;
        }
        final double top = getPosition().y();
        final WorldOffset headOffset = partWorldOffset(top, Z_DEPTH_PIPE_PLANT);
        final WorldOffset stemOffset = partWorldOffset(top + PART_SIZE_PIXELS, Z_DEPTH_PIPE_PLANT);
        placeHead(head, headOffset);
        placePart(stem, stemOffset.x(), stemOffset.y(), stemOffset.z());
    }

    /**
     * Puts the head quad at its offset. The fire plant overrides it to flip the head alone; the stem is
     * never flipped.
     */
    default void placeHead(final Geometry head, final WorldOffset offset) {
        placePart(head, offset.x(), offset.y(), offset.z());
    }

    default WorldOffset partWorldOffset(final double pixelTop, final float depth) {
        final int rows = getGameEngine().getLevelScene().getDimensions().rows();
        return WorldOffset.of(
            (float) (getPosition().x() / TILE_SPRITE_SIZE),
            rows - (float) ((pixelTop + PART_SIZE_PIXELS) / TILE_SPRITE_SIZE) - 1f / TILE_SPRITE_SIZE,
            depth
        );
    }

    @Override
    default void detach() {
        EnemyLevelObject.super.detach();
        detachPart(getStemGeometry());
    }

    private void moveVertically(final int yVelocityFixedPoint) {
        final FixedPointVelocity velocity = getVelocity().withY(yVelocityFixedPoint);
        setVelocity(velocity);
        setPosition(getPosition().steppedY(velocity));
    }
}
