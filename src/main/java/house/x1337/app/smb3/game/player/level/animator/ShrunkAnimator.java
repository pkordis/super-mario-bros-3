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
import static java.lang.Math.abs;

/**
 * Manages small Mario's sprite-based rendering and walk/run animation.
 *
 * <p>Small Mario's animation rules are derived from the SMB3 disassembly
 * (prg008.asm, prg029.asm). Key differences from all large suits:
 *
 * <ul>
 *   <li>Only 2 walk frames ({@code PF_WALKSMALL_BASE+0/+1}, $3E/$3F),
 *       not 3.</li>
 *   <li>Only 2 run frames ({@code PF_RUNSMALL_BASE+0/+1}, $4C/$4D)
 *       that cycle as 0→1→0→1 (no third sprite).</li>
 *   <li>Two distinct air frames: {@code PF_JUMPFALLSMALL} ($40) for
 *       normal jumps and {@code PF_FASTJUMPFALLSMALL} ($4E) when the
 *       full P-meter launch boost is active (flyTime &gt; 0).</li>
 *   <li>No ducking — forcefully disabled every frame
 *       (dasm prg008 PRG008_A70E).</li>
 *   <li>No tail wag, no flight, no tail attack — PowerUp_Ability=$00
 *       (dasm prg000 PowerUp_Ability table row 0).</li>
 * </ul>
 *
 * <p>All sprites are 16×16 px. The NES upper three OAM slots are all tile
 * $F1 (invisible) for every small-Mario frame, so only the bottom row of the
 * 3×2 sprite grid is ever drawn. The quad is therefore 16×16 game-units and
 * is anchored at the foot origin — it occupies the lower half of the 32px
 * render slot, exactly mirroring {@code Player_SpriteY+16} from prg029
 * {@code Player_Draw}.
 *
 * <p>Shared timing, frame bookkeeping and quad rebuilding live in
 * {@link BaseLevelScenePlayerAnimator}.
 */
@Prototype
@RequiredArgsConstructor
public final class ShrunkAnimator extends BaseLevelScenePlayerAnimator<ShrunkAnimatorAssets> {
    @Getter
    private final PlayerMode playerMode = SHRUNK;
    @Getter
    private final GameEngine gameEngine;
    @Getter
    private final PlayerIdentity identity;

    @Override
    public void update(final LevelScenePlayer levelScenePlayer) {
        final Node node = levelScenePlayer.getNode();

        final PlayerOrientationHorizontal orientation = levelScenePlayer.getOrientation().getHorizontal();
        final PlayerMovement movement = levelScenePlayer.getRuntimeState().getMovement();
        final int flyTime = levelScenePlayer.getRuntimeState().getPlayerFlyTime();
        final double absDx = abs(levelScenePlayer.getPosition().getDX());

        // -----------------------------------------------------------------------
        // Small Mario CANNOT duck (dasm prg008 PRG008_A70E). The flag is
        // forcefully cleared each frame in LevelScenePlayer.handleDucking(),
        // so we never check isDucking() here — it will always be false.
        // -----------------------------------------------------------------------

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
            // Small Mario has two distinct air frames (dasm GndMov_Small):
            //   PF_JUMPFALLSMALL ($40) — standard jump/fall
            //   PF_FASTJUMPFALLSMALL ($4E) — active when flyTime > 0
            //
            // Note: small Mario receives flyTime at full-P launch (all suits
            // do) but the wag/flight Y-velocity effects are gated behind
            // PowerUp_Ability bit 0, which is 0 for small. So flyTime here
            // acts only as a visual cue — the faster-looking jump frame —
            // without altering physics. This precisely mirrors the dasm.
            final Texture airTexture = (flyTime > 0) ? assets.fastJumpTexture() : assets.jumpTexture();
            final int airFrame = (flyTime > 0) ? 1 : 0;
            if (frameChanged(movement, orientation, airFrame)) {
                rebuildWithTexture(node, airTexture, orientation);
                markRendered(movement, orientation, airFrame);
            }
            return;
        }

        // Walking, running (non-power), and power-running all use the walk
        // animation tick system. RUNNING uses the walk sprite set (faster
        // cycle, not spread-eagle). POWER_RUNNING selects the run sprite set
        // (PF_4C/4D — dasm Player_SpreadEagleFrames last row).
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
