package house.x1337.app.smb3.game.player.level.animator;

import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.PlayerMovement;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.game.player.level.asset.NormalAnimatorAssets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.enumeration.PlayerMovement.DUCKING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.POWER_RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.SKIDDING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.STILL;
import static house.x1337.app.smb3.enumeration.PlayerMovement.WALKING;
import static java.lang.Math.abs;

/**
 * Manages "normal" (big, non-powered) Mario's sprite-based rendering and
 * walk/run animation.
 *
 * <p>Behaviourally this is raccoon Mario with the tail removed: it shares the
 * grounded states (still, skid, duck, walk, run) and the raccoon walk/run
 * timing, but has no tail attack, no tail wag and no powered flight. Its
 * airborne handling therefore mirrors small Mario (see {@link ShrunkAnimator}):
 * a single jump/fall frame, swapped for a "fast" jump frame while the full-P
 * launch boost is active ({@code flyTime > 0}). Because
 * {@code LevelScenePlayer.refinePlayerState} gates the FLYING state behind
 * tail-capable modes, big Mario never enters FLYING — only JUMPING/FALLING —
 * exactly like small Mario, so flyTime here is a visual cue only.
 *
 * <p>Every frame shares a single 24×32 canvas, so all states use the base
 * animator's default quad width and right-padding (see
 * {@link BaseLevelScenePlayerAnimator} and the animator specifications). Shared
 * timing, frame bookkeeping and quad rebuilding all live in the base.
 */
@Prototype
@RequiredArgsConstructor
public final class NormalAnimator extends BaseLevelScenePlayerAnimator<NormalAnimatorAssets> {
    @Getter
    private final PlayerMode playerMode = NORMAL;
    @Getter
    private final GameEngine gameEngine;
    @Getter
    private final PlayerIdentity identity;

    @Override
    public void update(final LevelScenePlayer levelScenePlayer) {
        final Node node = levelScenePlayer.getNode();
        final PlayerOrientationHorizontal orientation = levelScenePlayer.getOrientation().getHorizontal();
        final PlayerRuntimeState runtimeState = levelScenePlayer.getRuntimeState();
        final PlayerMovement movement = runtimeState.getMovement();
        final int flyTime = runtimeState.getPlayerFlyTime();
        final double absDx = abs(levelScenePlayer.getPosition().getDX());

        if (runtimeState.isDucking()) {
            // Big Mario ducks exactly like raccoon (dasm prg008 PRG008_A72B),
            // minus the tail. Ducking overrides all grounded/air movement
            // states, so it is checked first (mirrors RaccoonAnimator).
            walkAnimTicks = 0;
            walkFrameIndex = 0;
            if (frameChanged(DUCKING, orientation, -1)) {
                rebuildWithTexture(node, assets.duckTexture(), orientation);
                markRendered(DUCKING, orientation, -1);
            }
            return;
        }

        if (movement == STILL) {
            renderStill(node, orientation, assets.stillTexture());
            return;
        }

        if (movement == SKIDDING) {
            walkAnimTicks = 0;
            walkFrameIndex = 0;
            if (frameChanged(SKIDDING, orientation, -1)) {
                rebuildWithTexture(node, assets.skidTexture(), orientation);
                markRendered(SKIDDING, orientation, -1);
            }
            return;
        }

        if (movement == JUMPING || movement == FALLING) {
            // Air frames mirror small Mario (dasm GndMov_Small): a single
            // jump/fall frame, or the "fast" frame while the full-P launch
            // boost is active. flyTime is a visual cue only — big Mario has no
            // flight/wag Y-effects (those are gated behind hasTail() in the
            // move/refine code).
            final boolean boosted = flyTime > 0;
            final Texture airTexture = boosted ? assets.fastJumpTexture() : assets.jumpTexture();
            final int airFrame = boosted ? 1 : 0;
            if (frameChanged(movement, orientation, airFrame)) {
                rebuildWithTexture(node, airTexture, orientation);
                markRendered(movement, orientation, airFrame);
            }
            return;
        }

        if (movement == POWER_RUNNING || movement == RUNNING || movement == WALKING) {
            renderWalkRun(node, movement, orientation, absDx);
            return;
        }

        // Unhandled state — reset so the next handled state forces a rebuild.
        lastRenderedState = null;
        lastOrientation = null;
        lastWalkFrame = -1;
    }
}
