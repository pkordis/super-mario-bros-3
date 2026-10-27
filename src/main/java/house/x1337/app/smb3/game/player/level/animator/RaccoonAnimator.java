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
@Getter
@Prototype
@RequiredArgsConstructor
public final class RaccoonAnimator
    extends BaseLevelScenePlayerAnimator<RaccoonAnimatorAssets>
    implements RaccoonDimensions {
    private static final float DUCK_TAIL_OFFSET = (DUCK_SPRITE_WIDTH_PX - BODY_WIDTH_PIXELS) * PIXELS_TO_GAME_UNITS;
    private static final int TAIL_WAG_ANIM_DURATION = 10;
    private static final int[] TAIL_ATTACK_FRAME_SEQUENCE = {0, 1, 0, 2, 0};

    private final PlayerMode mode = RACCOON;
    private final GameEngine gameEngine;
    private final PlayerIdentity identity;

    // Animation state (walkAnimTicks / walkFrameIndex / last* live in the base)
    private int tailWagCount;
    private boolean wasTailAttacking;

    @Override
    public void update(final LevelScenePlayer levelScenePlayer) {
        final PlayerOrientationHorizontal orientationHorizontal = levelScenePlayer.getOrientation().getHorizontal();
        final PlayerRuntimeState runtimeState = levelScenePlayer.getRuntimeState();
        final PlayerMovement movement = runtimeState.getMovement();
        final RaccoonAnimatorAssets assets = getAssets();
        final int tailAttackCountdown = runtimeState.getPlayerTailAttackCountdown();
        final boolean isDucking = runtimeState.isDucking();
        final Node node = levelScenePlayer.getNode();
        final int wagCount = runtimeState.getPlayerWagCount();
        final PlayerPosition position = levelScenePlayer.getPosition();

        if (renderKickPose(node, levelScenePlayer, orientationHorizontal)) {
            return;
        }

        if (renderCarryPose(node, levelScenePlayer, orientationHorizontal)) {
            return;
        }

        // Tail attack overrides all other animations (dasm prg008:
        // Player_TailAttackAnim runs after all other logic).
        if (tailAttackCountdown > 0) {
            renderTailAttack(
                levelScenePlayer,
                runtimeState,
                movement,
                assets,
                node,
                tailAttackCountdown
            );
            return;
        }

        // When tail attack just ended, force a rebuild so the player
        // reverts to normal sprite with the correct pre-attack orientation.
        if (wasTailAttacking) {
            wasTailAttacking = false;
            setLastRenderedState(null);
            setLastOrientation(null);
            setLastWalkFrame(-1);
        }

        if (isDucking) {
            renderDucking(assets, node, orientationHorizontal);
            return;
        }

        if (movement == STILL) {
            renderStill(node, orientationHorizontal, assets.still());
            return;
        }

        if (movement == SKIDDING) {
            renderSkidding(assets, node, orientationHorizontal);
            return;
        }

        if (runtimeState.isInAir() && wagCount > 0) {
            renderTailWagAnimation(runtimeState, assets, node, movement, orientationHorizontal);
            return;
        }

        if (movement == FLYING) {
            renderFlying(position, assets, node, orientationHorizontal);
            return;
        }

        if (movement == WALKING || movement == RUNNING || movement == POWER_RUNNING) {
            renderWalkRun(node, movement, orientationHorizontal, abs(position.getDX()));
            return;
        }

        if (movement == FALLING) {
            renderFalling(assets, node, orientationHorizontal);
            return;
        }

        if (movement == JUMPING) {
            // Jumping without wag - dedicated jump frame.
            if (getLastRenderedState() != JUMPING || getLastOrientation() != orientationHorizontal) {
                rebuildWithTexture(node, assets.jump(), orientationHorizontal);
                setLastRenderedState(JUMPING);
                setLastOrientation(orientationHorizontal);
                setLastWalkFrame(-1);
            }
            return;
        }

        // State not handled by this animator (flying without wag, swimming, etc.)
        // Signal caller to use fallback rendering.
        setLastRenderedState(null);
        setLastOrientation(null);
        setLastWalkFrame(-1);
    }

    private void renderFalling(
        final RaccoonAnimatorAssets assets,
        final Node node,
        final PlayerOrientationHorizontal orientationHorizontal
    ) {
        // Falling without wag - static frame (dasm prg008: PF_JUMPRACCOON
        // row in Player_TailWagFlyFrames when WagCount = 0).
        if (getLastRenderedState() != FALLING || getLastOrientation() != orientationHorizontal
            || getLastWalkFrame() != -1) {
            rebuildWithTexture(node, assets.tailFall(0), orientationHorizontal);
            setLastRenderedState(FALLING);
            setLastOrientation(orientationHorizontal);
            setLastWalkFrame(-1);
        }
    }

    private void renderFlying(
        final PlayerPosition position,
        final RaccoonAnimatorAssets assets,
        final Node node,
        final PlayerOrientationHorizontal orientationHorizontal
    ) {
        // Flying without active wag cycle (dasm prg008: Player_AnimTailWag
        // still runs the frame lookup with TailCount = 0, yielding offset 0
        // into the fly row). When rising (DY < 0) → fly_2 (wings up),
        // when free falling/at apex (DY >= 0) → fly_3 (wings spread).
        final int flyFrame = (position.getDY() < 0) ? 1 : 2;
        final Texture texture = assets.tailFly(flyFrame);

        if (getLastRenderedState() != FLYING
            || getLastWalkFrame() != flyFrame
            || getLastOrientation() != orientationHorizontal
        ) {
            rebuildWithTexture(
                node, texture, orientationHorizontal, QUAD_WIDTH, TAIL_OFFSET
            );
            setLastRenderedState(FLYING);
            setLastOrientation(orientationHorizontal);
            setLastWalkFrame(flyFrame);
        }
    }

    private void renderTailWagAnimation(
        final PlayerRuntimeState runtimeState,
        final RaccoonAnimatorAssets assets,
        final Node node,
        final PlayerMovement movement,
        final PlayerOrientationHorizontal orientationHorizontal
    ) {
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
        if (tailWagCount <= 0 || getLastRenderedState() != movement) {
            tailWagCount = TAIL_WAG_ANIM_DURATION;
        }

        final int frameOffset = tailWagCount >> 2; // 2, 1, or 0
        final boolean flying = runtimeState.getPlayerFlyTime() > 0;
        final int tailFrame = min(frameOffset, 2);;
        final Texture texture;

        if (flying) {
            // Fly control: 3 distinct frames (PF_TAILWAGFLY_BASE +2/+1/+0)
            texture = assets.tailFly(tailFrame);
        } else {
            // Fall control: 3 distinct frames (PF_TAILWAGFALL +2/+1/+0),
            // frame-indexed by TailCount>>2 → up / middle / down.
            texture = assets.tailFall(tailFrame);
        }

        if (getLastRenderedState() != movement
            || getLastWalkFrame() != tailFrame
            || getLastOrientation() != orientationHorizontal) {
            // dasm prg008: Player_AnimTailWag never modifies Player_FlipBits.
            // All frames render with the player's current facing direction
            // unchanged. The tail "swish" comes from distinct sprite tiles.
            rebuildWithTexture(node, texture, orientationHorizontal);
            setLastRenderedState(movement);
            setLastOrientation(orientationHorizontal);
            setLastWalkFrame(tailFrame);
        }

        tailWagCount--;
    }

    private void renderSkidding(
        final RaccoonAnimatorAssets assets,
        final Node node,
        final PlayerOrientationHorizontal orientationHorizontal
    ) {
        // Skid frame - single static sprite while braking (prg008.asm:
        // Player_SkidFrame). Orientation stays as movement direction.
        setWalkAnimTicks(0);
        setWalkFrameIndex(0);

        if (getLastRenderedState() != SKIDDING || getLastOrientation() != orientationHorizontal) {
            rebuildWithTexture(
                node,
                assets.skid(),
                orientationHorizontal,
                SKID_QUAD_WIDTH,
                0f
            );
            setLastRenderedState(SKIDDING);
            setLastOrientation(orientationHorizontal);
            setLastWalkFrame(-1);
        }
    }

    private void renderDucking(
        final RaccoonAnimatorAssets assets,
        final Node node,
        final PlayerOrientationHorizontal orientationHorizontal
    ) {
        // Ducking frame — single static sprite (prg008.asm: PF_DUCK_RACCOON).
        // The ducking flag persists independently of the movement state, so
        // this renders the duck frame whether grounded or airborne (duck-jump).
        // dasm prg008: Player_AnimTailWag (PRG008_B082) and
        // Player_SoarJumpFallFrame (PRG008_B0C5) both early-return when
        // Player_IsDucking is set, leaving the duck frame intact.
        // This check MUST precede the movement-state checks (STILL, WALKING,
        // etc.) because ducking visually overrides all grounded states.
        setWalkAnimTicks(0);
        setWalkFrameIndex(0);

        if (getLastRenderedState() != DUCKING || getLastOrientation() != orientationHorizontal) {
            rebuildWithTexture(
                node,
                assets.duck(),
                orientationHorizontal,
                DUCK_QUAD_WIDTH,
                DUCK_TAIL_OFFSET
            );
            setLastRenderedState(DUCKING);
            setLastOrientation(orientationHorizontal);
            setLastWalkFrame(-1);
        }
    }

    private void renderTailAttack(
        final LevelScenePlayer levelScenePlayer,
        final PlayerRuntimeState runtimeState,
        final PlayerMovement movement,
        final RaccoonAnimatorAssets assets,
        final Node node,
        final int tailAttackCountdown
    ) {
        final int frameIndex = tailAttackCountdown >> 2;
        final int clampedFrame = min(frameIndex, 4);
        // The sprite is mirrored for the middle of the swing (dasm: EOR #SPR_HFLIP at $0B and $03).
        // Taken from the player rather than recomputed here, because the tail's hitbox is placed off
        // this same facing — the two must not drift (LevelScenePlayer#getTailAttackSwingFacing).
        final PlayerOrientationHorizontal effectiveOrientation = levelScenePlayer.getTailAttackSwingFacing();
        // Select the attack sequence once, then map the countdown step
        // onto the 3 distinct sprites via TAIL_ATTACK_FRAME_SEQUENCE, just
        // like the walk/run frame tables. Ground and air sequences differ
        // only in their base frame.
        final Texture[] attackTextures = runtimeState.isInAir()
            ? assets.tailAttackInAir()
            : assets.tailAttack();
        final Texture texture = attackTextures[TAIL_ATTACK_FRAME_SEQUENCE[clampedFrame]];
        final float quadWidth = tailAttackFrameWidth(clampedFrame);
        final float tailOffset = tailAttackFrameTailOffset(clampedFrame);

        if (getLastWalkFrame() != clampedFrame
            || getLastOrientation() != effectiveOrientation
            || getLastRenderedState() != movement
        ) {
            rebuildWithTexture(
                node,
                texture,
                effectiveOrientation,
                quadWidth,
                tailOffset
            );
            setLastRenderedState(movement);
            setLastOrientation(effectiveOrientation);
            setLastWalkFrame(clampedFrame);
        }
        wasTailAttacking = true;
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
