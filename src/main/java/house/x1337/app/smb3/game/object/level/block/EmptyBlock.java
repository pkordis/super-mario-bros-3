package house.x1337.app.smb3.game.object.level.block;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.block.animation.management.EmptyBlockBounceAnimationManager;
import house.x1337.app.smb3.game.object.level.brick.BrickBlockWithoutReward;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.Offset;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.EMPTY_BLOCK;

/**
 * A spent (used) solid block that replaced a question block once the latter was hit.
 *
 * <p>On its very first collision from below it plays the same 10-frame bounce animation
 * as a {@link BrickBlockWithoutReward} hit by small Mario (dasm {@code prg001.asm
 * ObjNorm_BounceDU / Bouncer_PUpVel}). That one-shot bounce is self-contained inside
 * {@link EmptyBlockBounceAnimationManager} and uses the correct empty-block sprite.
 * After it fires, any subsequent hit from below is silently ignored.
 */
@Getter
@Prototype
@RequiredArgsConstructor
public class EmptyBlock implements LevelObject {
    private final EmptyBlockBounceAnimationManager animationManager = getBean(EmptyBlockBounceAnimationManager.class);
    private final LevelObjectType type = EMPTY_BLOCK;
    private final ImageResource imageResource;
    private final Offset offset;

    /**
     * {@code true} once the one-shot bounce animation has been triggered.
     * Guards against re-triggering on any subsequent collision.
     */
    private boolean bounced;

    /**
     * Triggers the one-shot bounce animation, identical in physics to a
     * {@link BrickBlockWithoutReward} hit by small Mario.
     *
     * <p>Called by {@link house.x1337.app.smb3.game.object.level.block.QuestionBlock}
     * immediately after it places this block in the collision grid, so the bounce
     * fires regardless of the player's size at the moment of the original hit.
     *
     * @param gameEngine the game engine
     */
    public void triggerBounce(final GameEngine gameEngine) {
        animationManager.spawnBounce(gameEngine, offset);
        bounced = true;
    }

    /**
     * Subsequent collisions from below are ignored — the one-shot bounce
     * (triggered once via {@link #triggerBounce}) never repeats.
     */
    @Override
    public void onCollisionFromBelow(final LevelScenePlayer levelScenePlayer) {
        // Bounce already played (or about to play); this block never bounces again.
    }
}
