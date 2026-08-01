package house.x1337.app.smb3.game.player.level.animator;

import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.enumeration.PlayerState;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import house.x1337.app.smb3.model.game.player.asset.RaccoonAnimatorAssets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import static com.jme3.material.RenderState.BlendMode.Alpha;
import static com.jme3.material.RenderState.FaceCullMode.Off;
import static com.jme3.renderer.queue.RenderQueue.Bucket.Translucent;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.LEFT;
import static house.x1337.app.smb3.enumeration.PlayerState.DUCKING;
import static house.x1337.app.smb3.enumeration.PlayerState.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerState.FLYING;
import static house.x1337.app.smb3.enumeration.PlayerState.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerState.POWER_RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerState.RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerState.SKIDDING;
import static house.x1337.app.smb3.enumeration.PlayerState.STILL;
import static house.x1337.app.smb3.enumeration.PlayerState.WALKING;
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
public final class RaccoonAnimator implements LevelScenePlayerAnimator<RaccoonAnimatorAssets> {

    /**
     * NES tick counts before advancing to the next walk frame, indexed by
     * velocity tier. From prg008.asm {@code Player_WalkAnimTickMax}:
     * <pre>
     * .byte $07, $06, $05, $04, $03, $02, $01, $01, $01
     * </pre>
     * Index = abs(Player_XVel) >> 3 in the original fixed-point system.
     */
    private static final int[] WALK_ANIM_TICK_MAX = {7, 6, 5, 4, 3, 2, 1, 1, 1};

    /**
     * Walk animation frame sequence for raccoon/leaf power-up (prg008.asm):
     * {@code PF_WALKSPECIAL_BASE, +1, +2, +1} - maps to sprite indices 0, 1, 2, 1.
     */
    private static final int[] WALK_FRAME_SEQUENCE = {0, 1, 2, 1};

    /**
     * Run ("spread-eagle") frame sequence for non-small suits (prg008.asm):
     * {@code PF_RUNBIG_BASE, +1, +2, +1} - 3 distinct sprites in a 4-frame cycle.
     * Uses the same {@code Player_WalkFrame} counter and tick timing as walking.
     */
    private static final int[] RUN_FRAME_SEQUENCE = {0, 1, 2, 1};

    /**
     * Duration of one tail wag animation cycle in ticks (dasm prg008:
     * {@code Player_TailCount} is set to 10 on each A press and auto-
     * decrements). The 3 visual frames are indexed by {@code count >> 2}:
     * ticks 10-8 → frame 2, ticks 7-4 → frame 1, ticks 3-1 → frame 0.
     */
    private static final int TAIL_WAG_ANIM_DURATION = 10;


    // All raccoon sprites are 24×32 pixels (body 16px + tail 8px overflow right)
    private static final float SPRITE_WIDTH_PX = 24.0f;
    private static final float SPRITE_HEIGHT_PX = 32.0f;

    /**
     * The player body (collision box) width in sprite-pixels. All sprites
     * face left with the body occupying the leftmost 16px; the remaining 8px
     * on the right is the tail overflow.
     */
    private static final float BODY_WIDTH_PX = 16.0f;

    /**
     * Tail overflow beyond the collision box (sprite width − body width).
     * When facing left the tail extends rightward from the body; when facing
     * right the quad must shift left by this amount so the body stays aligned.
     */
    private static final float TAIL_OVERFLOW_PX = SPRITE_WIDTH_PX - BODY_WIDTH_PX;

    /** Width of the skid sprite which has no tail overflow. */
    private static final float SKID_SPRITE_WIDTH_PX = 16.0f;

    /** Width of the ducking sprite (23px - body 16px + partial tail 7px). */
    private static final float DUCK_SPRITE_WIDTH_PX = 23.0f;

    private static final float PX_TO_GAME_UNITS = 1.0f / TILE_SPRITE_SIZE;

    /** Quad width in game-units for normal sprites (24px / 16 = 1.5 tiles). */
    private static final float QUAD_WIDTH = SPRITE_WIDTH_PX * PX_TO_GAME_UNITS;

    /** Quad width in game-units for the skid sprite (16px / 16 = 1.0 tiles). */
    private static final float SKID_QUAD_WIDTH = SKID_SPRITE_WIDTH_PX * PX_TO_GAME_UNITS;

    /** Quad height in game-units (32px / 16 = 2.0 tiles). */
    private static final float QUAD_HEIGHT = SPRITE_HEIGHT_PX * PX_TO_GAME_UNITS;

    /** Quad width in game-units for the ducking sprite (23px / 16). */
    private static final float DUCK_QUAD_WIDTH = DUCK_SPRITE_WIDTH_PX * PX_TO_GAME_UNITS;

    /** Tail overflow for the ducking sprite (23 - 16 = 7px). */
    private static final float DUCK_TAIL_OFFSET = (DUCK_SPRITE_WIDTH_PX - BODY_WIDTH_PX) * PX_TO_GAME_UNITS;

    /** Tail overflow in game-units - shift applied when facing right. */
    private static final float TAIL_OFFSET = TAIL_OVERFLOW_PX * PX_TO_GAME_UNITS;

    @Getter
    private final PlayerMode playerMode = RACCOON;
    @Getter
    private final GameEngine gameEngine;
    @Getter
    private final PlayerIdentity identity;

    @Setter
    private RaccoonAnimatorAssets assets;

    // Animation state
    private int walkAnimTicks;
    private int walkFrameIndex;
    private int tailWagCount;
    private boolean wasTailAttacking;
    private PlayerState lastRenderedState;
    private PlayerOrientationHorizontal lastOrientation;
    private int lastWalkFrame = -1;

    public void update(final LevelScenePlayer levelScenePlayer) {
        final int tailAttack = levelScenePlayer.getPlayerTailAttack();
        final PlayerOrientationHorizontal orientation = levelScenePlayer.getPlayerOrientationHorizontal();
        final PlayerState state = levelScenePlayer.getState().getCurrent();
        final boolean isDucking = levelScenePlayer.getState().isDucking();
        final Node node = levelScenePlayer.getNode();
        if (node == null) {
            return;
        }
        final int wagCount = levelScenePlayer.getPlayerWagCount();
        final int flyTime = levelScenePlayer.getPlayerFlyTime();
        final PlayerPosition position = levelScenePlayer.getPosition();

        // Tail attack overrides all other animations (dasm prg008:
        // Player_TailAttackAnim runs after all other logic).
        if (tailAttack > 0) {
            final int frameIndex = tailAttack >> 2; // 4→3→2→1→0
            final int clampedFrame = min(frameIndex, 4);
            // Flip orientation at tailAttack == 11 and 3 (dasm: EOR #SPR_HFLIP)
            final boolean flipped = (tailAttack <= 11 && tailAttack > 3);
            final PlayerOrientationHorizontal effectiveOrientation = orientation.oppositeIf(flipped);

            // dasm prg008: In-air tail attack uses a different frame table
            // (Player_TailAttackFrames +5). The "resting" frames (indices
            // 0, 2, 4) become PF_TAILATKINAIR_BASE ($09 = jump/fall body
            // frame) instead of PF_TAILATKGROUND_BASE (attack_1). The
            // active swing frames (indices 1, 3) remain the same.
            final boolean inAir = (state == JUMPING || state == FALLING || state == FLYING);
            final Texture texture = textureForTailAttackFrame(clampedFrame, inAir);
            final float quadWidth = tailAttackFrameWidth(clampedFrame);
            final float tailOffset = tailAttackFrameTailOffset(clampedFrame);

            if (lastWalkFrame != clampedFrame
                    || lastOrientation != effectiveOrientation
                    || lastRenderedState != state) {
                rebuildWithTexture(
                    node, texture, effectiveOrientation, quadWidth, tailOffset
                );
                lastRenderedState = state;
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

            if (lastRenderedState != DUCKING || lastOrientation != orientation) {
                rebuildDuckTexture(node, assets.duckTexture(), orientation);
                lastRenderedState = DUCKING;
                lastOrientation = orientation;
                lastWalkFrame = -1;
            }
            return;
        }

        if (state == STILL) {
            // Reset walk animation when standing still
            walkAnimTicks = 0;
            walkFrameIndex = 2; // Original: Player_WalkFrame forced to 2 when still

            if (lastRenderedState != STILL || lastOrientation != orientation) {
                rebuildWithTexture(node, assets.stillTexture(), orientation);
                lastRenderedState = STILL;
                lastOrientation = orientation;
                lastWalkFrame = -1;
            }
            return;
        }

        if (state == SKIDDING) {
            // Skid frame - single static sprite while braking (prg008.asm:
            // Player_SkidFrame). Orientation stays as movement direction.
            walkAnimTicks = 0;
            walkFrameIndex = 0;

            if (lastRenderedState != SKIDDING || lastOrientation != orientation) {
                rebuildSkidTexture(node, assets.skidTexture(), orientation);
                lastRenderedState = SKIDDING;
                lastOrientation = orientation;
                lastWalkFrame = -1;
            }
            return;
        }

        if ((state == FLYING || state == FALLING || state == JUMPING) && wagCount > 0) {
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
            if (tailWagCount <= 0 || lastRenderedState != state) {
                tailWagCount = TAIL_WAG_ANIM_DURATION;
            }

            final int frameOffset = tailWagCount >> 2; // 2, 1, or 0
            final boolean flying = flyTime > 0;
            final int tailFrame;
            final Texture texture;

            if (flying) {
                // Fly control: 3 distinct frames (PF_TAILWAGFLY_BASE +2/+1/+0)
                tailFrame = min(frameOffset, 2);
                texture = textureForTailWagFlyFrame(tailFrame);
            } else {
                // Fall control: 2 distinct frames (PF_TAILWAGFALL +1/+0)
                tailFrame = min(frameOffset, 1);
                texture = textureForTailWagFallFrame(tailFrame);
            }

            if (lastRenderedState != state
                    || lastWalkFrame != tailFrame
                    || lastOrientation != orientation) {
                // dasm prg008: Player_AnimTailWag never modifies Player_FlipBits.
                // All frames render with the player's current facing direction
                // unchanged. The tail "swish" comes from distinct sprite tiles.
                rebuildWithTexture(
                    node, texture, orientation, QUAD_WIDTH, TAIL_OFFSET
                );
                lastRenderedState = state;
                lastOrientation = orientation;
                lastWalkFrame = tailFrame;
            }

            tailWagCount--;
            return;
        }

        if (state == FLYING) {
            // Flying without active wag cycle (dasm prg008: Player_AnimTailWag
            // still runs the frame lookup with TailCount = 0, yielding offset 0
            // into the fly row). When rising (DY < 0) → fly_2 (wings up),
            // when free falling/at apex (DY >= 0) → fly_3 (wings spread).
            final int flyFrame = (position.getDY() < 0) ? 1 : 2;
            final Texture texture = textureForTailWagFlyFrame(flyFrame);

            if (lastRenderedState != FLYING
                    || lastWalkFrame != flyFrame
                    || lastOrientation != orientation) {
                rebuildWithTexture(
                    node, texture, orientation, QUAD_WIDTH, TAIL_OFFSET
                );
                lastRenderedState = FLYING;
                lastOrientation = orientation;
                lastWalkFrame = flyFrame;
            }
            return;
        }

        if (state == WALKING || state == RUNNING || state == POWER_RUNNING) {
            // Advance walk animation based on NES tick timing
            advanceWalkAnimation(abs(position.getDX()));

            final int[] frameSequence = (state == POWER_RUNNING)
                    ? RUN_FRAME_SEQUENCE : WALK_FRAME_SEQUENCE;
            final int currentSpriteFrame = frameSequence[walkFrameIndex];
            if (lastRenderedState != state
                    || lastWalkFrame != currentSpriteFrame
                    || lastOrientation != orientation) {
                final Texture texture = (state == POWER_RUNNING)
                        ? textureForRunFrame(currentSpriteFrame)
                        : textureForWalkFrame(currentSpriteFrame);
                rebuildWithTexture(node, texture, orientation);
                lastRenderedState = state;
                lastOrientation = orientation;
                lastWalkFrame = currentSpriteFrame;
            }
            return;
        }

        if (state == FALLING) {
            // Falling without wag - static frame (dasm prg008: PF_JUMPRACCOON
            // row in Player_TailWagFlyFrames when WagCount = 0).
            if (lastRenderedState != FALLING || lastOrientation != orientation
                    || lastWalkFrame != -1) {
                rebuildWithTexture(node, assets.tailFallTexture1(), orientation, QUAD_WIDTH, TAIL_OFFSET);
                lastRenderedState = FALLING;
                lastOrientation = orientation;
                lastWalkFrame = -1;
            }
            return;
        }

        if (state == JUMPING) {
            // Jumping without wag - dedicated jump frame.
            if (lastRenderedState != JUMPING || lastOrientation != orientation) {
                rebuildWithTexture(node, assets.jumpTexture(), orientation, QUAD_WIDTH, TAIL_OFFSET);
                lastRenderedState = JUMPING;
                lastOrientation = orientation;
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

    /**
     * Resets internal tracking so the next call to {@code tick} will
     * unconditionally rebuild the geometry. Call this when the renderer
     * switches away from sprite-based rendering (e.g. to the cyan fallback).
     */
    public void resetState() {
        lastRenderedState = null;
        lastOrientation = null;
        lastWalkFrame = -1;
        walkAnimTicks = 0;
        tailWagCount = 0;
        wasTailAttacking = false;
    }

    /**
     * Maps the project's floating-point velocity to the NES tick-max table index.
     *
     * <p>In the original: index = abs(Player_XVel) >> 3, where Player_XVel is
     * 4.4 fixed-point (raw $00–$38). The project stores velocity as
     * raw_value / 16.0, so raw = absDx * 16. Index = (int)(raw) >> 3.
     */
    private void advanceWalkAnimation(final double absDx) {
        final int rawVel = (int) (absDx * TILE_SPRITE_SIZE);
        final int tickIndex = min(rawVel >> 3, WALK_ANIM_TICK_MAX.length - 1);
        final int tickMax = WALK_ANIM_TICK_MAX[tickIndex];

        walkAnimTicks++;
        if (walkAnimTicks >= tickMax) {
            walkAnimTicks = 0;
            walkFrameIndex = (walkFrameIndex + 1) % WALK_FRAME_SEQUENCE.length;
        }
    }

    /**
     * Returns the texture for the given walk frame index (from the 4-frame
     * sequence mapped to 3 sprites).
     */
    private Texture textureForWalkFrame(final int spriteFrame) {
        return switch (spriteFrame) {
            case 1 -> assets.walkTexture2();
            case 2 -> assets.stillTexture();
            default -> assets.walkTexture1();
        };
    }

    /**
     * Returns the texture for the given run (spread-eagle) frame index.
     * The 4-frame sequence maps to 3 distinct sprites:
     * frame 0 → running_1, frame 1 → running_2, frame 2 → running_3.
     * Matches {@code Player_SpreadEagleFrames} from prg008.asm.
     */
    private Texture textureForRunFrame(final int spriteFrame) {
        return switch (spriteFrame) {
            case 1 -> assets.runTexture2();
            case 2 -> assets.runTexture3();
            default ->assets.runTexture1();
        };
    }

    /**
     * Returns the texture for a flying tail wag frame (dasm prg008:
     * {@code Player_TailWagFlyFrames} row 0 - {@code PF_TAILWAGFLY_BASE+2, +1, +0}).
     * Used when {@code flyTime > 0} and the player is rising or at apex.
     *
     * @param frameOffset 2, 1, or 0 (derived from TailCount >> 2)
     */
    private Texture textureForTailWagFlyFrame(final int frameOffset) {
        return switch (frameOffset) {
            case 2 -> assets.tailFlyTexture3();
            case 1 -> assets.tailFlyTexture2();
            default -> assets.tailFlyTexture1();
        };
    }

    /**
     * Returns the texture for a falling flutter wag frame (dasm prg008:
     * {@code Player_TailWagFlyFrames} row 3 - {@code PF_TAILWAGFALL+1, +0}).
     * Used when {@code flyTime == 0} and the player is fluttering descent.
     *
     * @param frameOffset 1 or 0 (derived from TailCount >> 2, clamped to 1)
     */
    private Texture textureForTailWagFallFrame(final int frameOffset) {
        if (frameOffset == 1) {
            return assets.tailFallTexture2();
        }
        return assets.tailFallTexture1();
    }

    /**
     * Returns the texture for the given tail attack frame index (dasm prg008:
     * {@code Player_TailAttackFrames}).
     *
     * <p>Ground sequence (indices 0-4): attack_1, attack_2, attack_1, attack_3, attack_1
     * <p>In-air sequence (indices 5-9): walking_1, attack_2, walking_1, attack_3, walking_1
     * <p>PF_TAILATKINAIR_BASE ($09) has identical sprite patterns to PF_WALKSPECIAL_BASE
     * ($00), i.e. walking_1.
     *
     * @param frameIndex frame position in the 5-frame cycle (0-4)
     * @param inAir      true if the player is airborne
     */
    private Texture textureForTailAttackFrame(final int frameIndex, final boolean inAir) {
        return switch (frameIndex) {
            case 1 -> assets.tailAttackTexture2();
            case 3 -> assets.tailAttackTexture3();
            default -> inAir ? assets.walkTexture1() : assets.tailAttackTexture1();
        };
    }

    /**
     * Returns the quad width for the given tail attack frame.
     * Ground: attack_1 (indices 0, 2, 4) is 24px wide; attack_2/3 are 16px.
     * In-air: jump texture (indices 0, 2, 4) is 24px wide; attack_2/3 are 16px.
     */
    private float tailAttackFrameWidth(final int frameIndex) {
        if (frameIndex == 0 || frameIndex == 2 || frameIndex == 4) {
            return QUAD_WIDTH;
        }
        return SKID_QUAD_WIDTH;
    }

    /**
     * Returns the tail offset for the given tail attack frame.
     * Only the 24px-wide resting frames (indices 0, 2, 4) have tail overflow.
     */
    private float tailAttackFrameTailOffset(final int frameIndex) {
        if (frameIndex == 0 || frameIndex == 2 || frameIndex == 4) {
            return TAIL_OFFSET;
        }
        return 0f;
    }

    /**
     * Replaces the node's children with a textured quad positioned so that the
     * player's collision box (leftmost 16px of the sprite) aligns with the
     * logical player position regardless of orientation.
     *
     * <p>All raccoon sprites face left. The body (collision box) occupies the
     * left 16px; any tail overflow extends rightward. When the player faces
     * right the quad is horizontally flipped AND shifted left by the tail
     * overflow so the body remains collision-aligned.
     *
     * <p>The skid sprite is exactly 16px wide (no tail), so it uses a narrower
     * quad and needs no tail-offset compensation.
     */
    private void rebuildWithTexture(
        final Node node,
        final Texture texture,
        final PlayerOrientationHorizontal orientation
    ) {
        rebuildWithTexture(node, texture, orientation, QUAD_WIDTH, TAIL_OFFSET);
    }

    /**
     * Variant used for the skid sprite which has no tail overflow.
     */
    private void rebuildSkidTexture(
        final Node node,
        final Texture texture,
        final PlayerOrientationHorizontal orientation
    ) {
        rebuildWithTexture(node, texture, orientation, SKID_QUAD_WIDTH, 0f);
    }

    /**
     * Variant used for the ducking sprite which is 23×32px (slightly narrower
     * than normal with a shorter tail overflow of 7px).
     */
    private void rebuildDuckTexture(
        final Node node,
        final Texture texture,
        final PlayerOrientationHorizontal orientation
    ) {
        rebuildWithTexture(node, texture, orientation, DUCK_QUAD_WIDTH, DUCK_TAIL_OFFSET);
    }

    /**
     * Core rendering: builds a textured quad of the given width, aligned to
     * the collision box origin.
     *
     * @param node        the scene node to populate
     * @param texture     the sprite texture
     * @param orientation current facing direction
     * @param quadWidth   quad width in game-units (varies per sprite)
     * @param tailOffset  tail overflow offset in game-units (0 for body-only sprites)
     */
    private void rebuildWithTexture(
        final Node node,
        final Texture texture,
        final PlayerOrientationHorizontal orientation,
        final float quadWidth,
        final float tailOffset
    ) {
        node.detachAllChildren();

        final Quad quad = new Quad(quadWidth, RaccoonAnimator.QUAD_HEIGHT);
        final Geometry geometry = new Geometry("Player", quad);

        final Material material = new Material(getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        material.setTexture("ColorMap", texture);
        material.getAdditionalRenderState().setBlendMode(Alpha);

        geometry.setMaterial(material);
        geometry.setQueueBucket(Translucent);

        // Sprites are drawn facing left: body on the left, tail on the right.
        // The node origin corresponds to Player_X (left edge of the collision box).
        if (orientation == LEFT) {
            // Facing left: quad origin is at node origin. The tail overflows
            // to the right beyond the collision box - visually correct.
            geometry.setLocalTranslation(0, 0, 0);
            geometry.setLocalScale(1, 1, 1);
        } else {
            // Facing right: flip horizontally. After flipping, the body
            // (originally left-side) is now on the right, so shift the quad
            // left by the tail overflow to re-align the body with the
            // collision box.
            material.getAdditionalRenderState().setFaceCullMode(Off);
            geometry.setLocalScale(-1, 1, 1);
            geometry.setLocalTranslation(quadWidth - tailOffset, 0, 0);
        }

        node.attachChild(geometry);
    }
}
