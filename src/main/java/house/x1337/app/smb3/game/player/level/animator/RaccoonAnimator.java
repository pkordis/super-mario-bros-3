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
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.game.player.level.asset.RaccoonAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.dimension.RaccoonDimensions;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.GameConstants.PIXELS_TO_GAME_UNITS;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;
import static house.x1337.app.smb3.enumeration.PlayerMovement.DUCKING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FLYING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.POWER_RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.SKIDDING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.STILL;
import static house.x1337.app.smb3.enumeration.PlayerMovement.WALKING;
import static java.lang.Math.abs;
import static java.lang.Math.min;

/**
 * Manages raccoon Mario's sprite-based rendering and walk/run animation.
 *
 * <p>Animation timing is derived from the SMB3 disassembly (prg008.asm):
 * {@code Player_WalkAnimTickMax} defines how many ticks elapse before advancing
 * to the next animation frame. The tick maximum is indexed by
 * {@code abs(Player_XVel) >> 3} in the original - here we map the project's
 * floating-point velocity back to that index.
 *
 * <p>The raccoon walk cycle uses 3 distinct sprites arranged in a 4-frame
 * sequence: frame 0, frame 1, frame 2, frame 1 (matching
 * {@code PF_WALKSPECIAL_BASE+0, +1, +2, +1} from the disassembly).
 *
 * <p>The run ("spread-eagle") cycle uses 3 distinct sprites in an identical
 * 4-frame pattern: frame 0, frame 1, frame 2, frame 1 (matching
 * {@code PF_RUNBIG_BASE+0, +1, +2, +1} via {@code Player_SpreadEagleFrames}).
 * Both cycles share the same {@code Player_WalkFrame} counter and tick-based
 * timing; the original switches to the run sprites when
 * {@code abs(Player_XVel) >= $37} and the P-meter is full.
 *
 * <p>All sprites are 24×32 pixels and drawn facing left. When the player
 * faces right, the geometry is horizontally flipped via a negative X-scale
 * on the local transform.
 */
@Prototype
@RequiredArgsConstructor
public final class RaccoonAnimator
    extends BaseLevelScenePlayerAnimator<RaccoonAnimatorAssets>
    implements RaccoonDimensions {

    private static final float DUCK_TAIL_OFFSET = (DUCK_SPRITE_WIDTH_PX - BODY_WIDTH_PIXELS) * PIXELS_TO_GAME_UNITS;
    private static final int TAIL_WAG_ANIM_DURATION = 10;

    /**
     * Maps the tail-attack countdown step ({@code countdown >> 2}, range 0..4)
     * onto the distinct sprite index, mirroring the walk/run frame-sequence
     * tables. The whip oscillates neutral→swing→neutral→swing→neutral, so the
     * base frame (index 0) is reused at steps 0/2/4 while the two swings sit at
     * steps 1 and 3 (dasm Player_TailAttackFrames: $03,$04,$03,$05,$03).
     */
    private static final int[] TAIL_ATTACK_FRAME_SEQUENCE = {0, 1, 0, 2, 0};

    @Getter
    private final PlayerMode playerMode = RACCOON;
    @Getter
    private final GameEngine gameEngine;
    @Getter
    private final PlayerIdentity identity;

    // Animation state (walkAnimTicks / walkFrameIndex / last* live in the base)
    private int tailWagCount;
    private boolean wasTailAttacking;

    public void update(final LevelScenePlayer levelScenePlayer) {
        final PlayerOrientationHorizontal orientationHorizontal = levelScenePlayer.getOrientation().getHorizontal();
        final PlayerRuntimeState runtimeState = levelScenePlayer.getRuntimeState();
        final PlayerMovement movement = runtimeState.getMovement();

        final int tailAttack = runtimeState.getPlayerTailAttackCountdown();
        final boolean isDucking = runtimeState.isDucking();

        final Node node = levelScenePlayer.getNode();
        final int wagCount = runtimeState.getPlayerWagCount();
        final int flyTime = runtimeState.getPlayerFlyTime();
        final PlayerPosition position = levelScenePlayer.getPosition();

        // Tail attack overrides all other animations (dasm prg008:
        // Player_TailAttackAnim runs after all other logic).
        if (tailAttack > 0) {
            final int frameIndex = tailAttack >> 2; // 4→3→2→1→0
            final int clampedFrame = min(frameIndex, 4);
            // Flip orientation at tailAttack == 11 and 3 (dasm: EOR #SPR_HFLIP)
            final boolean flipped = (tailAttack <= 11 && tailAttack > 3);
            final PlayerOrientationHorizontal effectiveOrientation = orientationHorizontal.oppositeIf(flipped);
            // Select the attack sequence once, then map the countdown step
            // onto the 3 distinct sprites via TAIL_ATTACK_FRAME_SEQUENCE, just
            // like the walk/run frame tables. Ground and air sequences differ
            // only in their base frame.
            final Texture[] attackTextures = runtimeState.isInAir()
                ? assets.tailAttackInAirTextures()
                : assets.tailAttackTextures();
            final Texture texture = attackTextures[TAIL_ATTACK_FRAME_SEQUENCE[clampedFrame]];
            final float quadWidth = tailAttackFrameWidth(clampedFrame);
            final float tailOffset = tailAttackFrameTailOffset(clampedFrame);

            if (lastWalkFrame != clampedFrame
                    || lastOrientation != effectiveOrientation
                    || lastRenderedState != movement
            ) {
                rebuildWithTexture(
                    node,
                    texture,
                    effectiveOrientation,
                    quadWidth,
                    tailOffset
                );
                lastRenderedState = movement;
                lastOrientation = effectiveOrientation;
                lastWalkFrame = clampedFrame;
            }
            wasTailAttacking = true;
            return;
        }

        // When tail attack just ended, force a rebuild so the player
        // reverts to normal sprite with the correct pre-attack orientation.
        if (wasTailAttacking) {
            wasTailAttacking = false;
            lastRenderedState = null;
            lastOrientation = null;
            lastWalkFrame = -1;
        }

        if (isDucking) {
            // Ducking frame — single static sprite (prg008.asm: PF_DUCK_RACCOON).
            // The ducking flag persists independently of the movement state, so
            // this renders the duck frame whether grounded or airborne (duck-jump).
            // dasm prg008: Player_AnimTailWag (PRG008_B082) and
            // Player_SoarJumpFallFrame (PRG008_B0C5) both early-return when
            // Player_IsDucking is set, leaving the duck frame intact.
            // This check MUST precede the movement-state checks (STILL, WALKING,
            // etc.) because ducking visually overrides all grounded states.
            walkAnimTicks = 0;
            walkFrameIndex = 0;

            if (lastRenderedState != DUCKING || lastOrientation != orientationHorizontal) {
                rebuildWithTexture(
                    node,
                    assets.duckTexture(),
                    orientationHorizontal,
                    DUCK_QUAD_WIDTH,
                    DUCK_TAIL_OFFSET
                );
                lastRenderedState = DUCKING;
                lastOrientation = orientationHorizontal;
                lastWalkFrame = -1;
            }
            return;
        }

        if (movement == STILL) {
            renderStill(node, orientationHorizontal, assets.stillTexture());
            return;
        }

        if (movement == SKIDDING) {
            // Skid frame - single static sprite while braking (prg008.asm:
            // Player_SkidFrame). Orientation stays as movement direction.
            walkAnimTicks = 0;
            walkFrameIndex = 0;

            if (lastRenderedState != SKIDDING || lastOrientation != orientationHorizontal) {
                rebuildWithTexture(
                    node,
                    assets.skidTexture(),
                    orientationHorizontal,
                    SKID_QUAD_WIDTH,
                    0f
                );
                lastRenderedState = SKIDDING;
                lastOrientation = orientationHorizontal;
                lastWalkFrame = -1;
            }
            return;
        }

        if (runtimeState.isInAir() && wagCount > 0) {
            // Tail wag animation - two distinct visual sets (dasm prg008:
            // Player_AnimTailWag selects row from Player_TailWagFlyFrames):
            //
            // 1. FLY control (flyTime > 0): PF_TAILWAGFLY_BASE - 3 frames,
            //    player is rising or at apex during powered flight.
            // 2. FALL control (flyTime == 0): PF_TAILWAGFALL - 2 frames,
            //    player is fluttering to slow their descent.
            //
            // Both use Player_TailCount (set to 10 on A press, auto-decrements)
            // with frame = TailCount >> 2 indexing into the frame table.
            if (tailWagCount <= 0 || lastRenderedState != movement) {
                tailWagCount = TAIL_WAG_ANIM_DURATION;
            }

            final int frameOffset = tailWagCount >> 2; // 2, 1, or 0
            final boolean flying = flyTime > 0;
            final int tailFrame = min(frameOffset, 2);;
            final Texture texture;

            if (flying) {
                // Fly control: 3 distinct frames (PF_TAILWAGFLY_BASE +2/+1/+0)
                texture = assets.tailFlyTexture(tailFrame);
            } else {
                // Fall control: 3 distinct frames (PF_TAILWAGFALL +2/+1/+0),
                // frame-indexed by TailCount>>2 → up / middle / down.
                texture = assets.tailFallTexture(tailFrame);
            }

            if (lastRenderedState != movement
                    || lastWalkFrame != tailFrame
                    || lastOrientation != orientationHorizontal) {
                // dasm prg008: Player_AnimTailWag never modifies Player_FlipBits.
                // All frames render with the player's current facing direction
                // unchanged. The tail "swish" comes from distinct sprite tiles.
                rebuildWithTexture(node, texture, orientationHorizontal);
                lastRenderedState = movement;
                lastOrientation = orientationHorizontal;
                lastWalkFrame = tailFrame;
            }

            tailWagCount--;
            return;
        }

        if (movement == FLYING) {
            // Flying without active wag cycle (dasm prg008: Player_AnimTailWag
            // still runs the frame lookup with TailCount = 0, yielding offset 0
            // into the fly row). When rising (DY < 0) → fly_2 (wings up),
            // when free falling/at apex (DY >= 0) → fly_3 (wings spread).
            final int flyFrame = (position.getDY() < 0) ? 1 : 2;
            final Texture texture = assets.tailFlyTexture(flyFrame);

            if (lastRenderedState != FLYING
                    || lastWalkFrame != flyFrame
                    || lastOrientation != orientationHorizontal
            ) {
                rebuildWithTexture(
                    node, texture, orientationHorizontal, QUAD_WIDTH, TAIL_OFFSET
                );
                lastRenderedState = FLYING;
                lastOrientation = orientationHorizontal;
                lastWalkFrame = flyFrame;
            }
            return;
        }

        if (movement == WALKING || movement == RUNNING || movement == POWER_RUNNING) {
            renderWalkRun(node, movement, orientationHorizontal, abs(position.getDX()));
            return;
        }

        if (movement == FALLING) {
            // Falling without wag - static frame (dasm prg008: PF_JUMPRACCOON
            // row in Player_TailWagFlyFrames when WagCount = 0).
            if (lastRenderedState != FALLING || lastOrientation != orientationHorizontal
                    || lastWalkFrame != -1) {
                rebuildWithTexture(node, assets.tailFallTexture(0), orientationHorizontal);
                lastRenderedState = FALLING;
                lastOrientation = orientationHorizontal;
                lastWalkFrame = -1;
            }
            return;
        }

        if (movement == JUMPING) {
            // Jumping without wag - dedicated jump frame.
            if (lastRenderedState != JUMPING || lastOrientation != orientationHorizontal) {
                rebuildWithTexture(node, assets.jumpTexture(), orientationHorizontal);
                lastRenderedState = JUMPING;
                lastOrientation = orientationHorizontal;
                lastWalkFrame = -1;
            }
            return;
        }

        // State not handled by this animator (flying without wag, swimming, etc.)
        // Signal caller to use fallback rendering.
        lastRenderedState = null;
        lastOrientation = null;
        lastWalkFrame = -1;
    }

    @Override
    public void resetState() {
        super.resetState();
        tailWagCount = 0;
        wasTailAttacking = false;
    }

    private float tailAttackFrameWidth(final int frameIndex) {
        if (frameIndex == 0 || frameIndex == 2 || frameIndex == 4) {
            return QUAD_WIDTH;
        }
        return SKID_QUAD_WIDTH;
    }

    private float tailAttackFrameTailOffset(final int frameIndex) {
        if (frameIndex == 0 || frameIndex == 2 || frameIndex == 4) {
            return TAIL_OFFSET;
        }
        return 0f;
    }
}
