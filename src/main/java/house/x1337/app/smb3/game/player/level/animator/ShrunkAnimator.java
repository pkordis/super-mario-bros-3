package house.x1337.app.smb3.game.player.level.animator;

import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.enumeration.PlayerMovement;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import house.x1337.app.smb3.model.game.player.level.asset.ShrunkAnimatorAssets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.POWER_RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.SKIDDING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.STILL;
import static house.x1337.app.smb3.enumeration.PlayerMovement.WALKING;

/**
 * Manages small Mario's sprite-based rendering and walk/run animation.
 *
 * <p>Key differences from all large suits:
 *
 * <ul>
 *   <li>Only 2 walk frames, not 3.</li>
 *   <li>Only 2 run frames that cycle as 0→1→0→1 (no third sprite).</li>
 *   <li>Two distinct air frames: one for normal jumps and a "fast" one
 *       when the full P-meter launch boost is active (flyTime &gt; 0).</li>
 *   <li>No ducking — forcefully disabled every frame.</li>
 *   <li>No tail wag, no flight, no tail attack — small Mario has none of
 *       the suit abilities.</li>
 * </ul>
 *
 * <p>All sprites are 16×16 px. Only the bottom row of the 3×2 sprite grid
 * is ever drawn for a small-Mario frame; the upper sprites are always blank.
 * The quad is therefore 16×16 game-units and is anchored at the foot
 * origin — it occupies the lower half of the 32px render slot, 16 px below
 * the top of the player's sprite.
 *
 * <p>Shared timing, frame bookkeeping and quad rebuilding live in
 * {@link BaseLevelScenePlayerAnimator}.
 */
@Getter
@Prototype
@RequiredArgsConstructor
public final class ShrunkAnimator extends BaseLevelScenePlayerAnimator<ShrunkAnimatorAssets> {
    private final PlayerMode mode = SHRUNK;
    private final GameEngine gameEngine;
    private final PlayerIdentity identity;

    @Override
    public void update(final LevelScenePlayer levelScenePlayer) {
        final Node node = levelScenePlayer.getNode();
        final ShrunkAnimatorAssets assets = getAssets();
        final PlayerOrientationHorizontal orientation = levelScenePlayer.getOrientation().getHorizontal();
        final PlayerMovement movement = levelScenePlayer.getRuntimeState().getMovement();
        final int flyTime = levelScenePlayer.getRuntimeState().getPlayerFlyTime();

        if (renderPipePose(node, levelScenePlayer, orientation)) {
            return;
        }

        if (renderKickPose(node, levelScenePlayer, orientation)) {
            return;
        }

        if (renderCarryPose(node, levelScenePlayer, orientation)) {
            return;
        }

        if (movement == STILL) {
            renderStill(node, orientation, assets.still());
            return;
        }

        if (movement == SKIDDING) {
            setWalkAnimTicks(0);
            setWalkFrameIndex(0);
            if (frameChanged(SKIDDING, orientation, -1)) {
                rebuildWithTexture(node, assets.skid(), orientation);
                markRendered(SKIDDING, orientation, -1);
            }
            return;
        }

        if (movement == JUMPING || movement == FALLING) {
            // Small Mario has two distinct air frames:
            //   jump — standard jump/fall
            //   fast jump — active when flyTime > 0
            //
            // Note: small Mario receives flyTime at full-P launch (all suits
            // do) but the wag/flight Y-velocity effects are gated behind
            // the suit's flight ability, which small Mario lacks. So flyTime here
            // acts only as a visual cue — the faster-looking jump frame —
            // without altering physics.
            final Texture airTexture = (flyTime > 0) ? assets.fastJump() : assets.jump();
            final int airFrame = (flyTime > 0) ? 1 : 0;
            if (frameChanged(movement, orientation, airFrame)) {
                rebuildWithTexture(node, airTexture, orientation);
                markRendered(movement, orientation, airFrame);
            }
            return;
        }

        // Walking, running (non-power), and power-running all use the walk
        // animation tick system. RUNNING uses the walk sprite set (faster
        // cycle, not spread-eagle). POWER_RUNNING selects the run sprite set.
        if (movement == POWER_RUNNING || movement == RUNNING || movement == WALKING) {
            renderWalkRun(node, levelScenePlayer, movement, orientation);
            return;
        }

        // Unhandled state — reset so the next handled state forces a rebuild.
        setLastRenderedState(null);
        setLastOrientation(null);
        setLastWalkFrame(-1);
    }
}
