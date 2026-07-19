package house.x1337.app.smb3.game.player.level;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.enumeration.PlayerOrientation;
import house.x1337.app.smb3.enumeration.PlayerState;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.PlayerOrientation.LEFT;
import static house.x1337.app.smb3.enumeration.PlayerState.POWER_RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerState.RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerState.SKIDDING;
import static house.x1337.app.smb3.enumeration.PlayerState.STILL;
import static house.x1337.app.smb3.enumeration.PlayerState.WALKING;

/**
 * Manages raccoon Mario's sprite-based rendering and walk/run animation.
 *
 * <p>Animation timing is derived from the SMB3 disassembly (prg008.asm):
 * {@code Player_WalkAnimTickMax} defines how many ticks elapse before advancing
 * to the next animation frame. The tick maximum is indexed by
 * {@code abs(Player_XVel) >> 3} in the original — here we map the project's
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
@RequiredArgsConstructor
public final class RacoonPlayerAnimator {

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
     * {@code PF_WALKSPECIAL_BASE, +1, +2, +1} — maps to sprite indices 0, 1, 2, 1.
     */
    private static final int[] WALK_FRAME_SEQUENCE = {0, 1, 2, 1};

    /**
     * Run ("spread-eagle") frame sequence for non-small suits (prg008.asm):
     * {@code PF_RUNBIG_BASE, +1, +2, +1} — 3 distinct sprites in a 4-frame cycle.
     * Uses the same {@code Player_WalkFrame} counter and tick timing as walking.
     */
    private static final int[] RUN_FRAME_SEQUENCE = {0, 1, 2, 1};

    /**
     * Sprite path prefix inside the classpath resources.
     */
    private static final String SPRITE_PATH = "sprites/player/";

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

    private static final float PX_TO_GAME_UNITS = 1.0f / 16.0f;

    /** Quad width in game-units for normal sprites (24px / 16 = 1.5 tiles). */
    private static final float QUAD_WIDTH = SPRITE_WIDTH_PX * PX_TO_GAME_UNITS;

    /** Quad width in game-units for the skid sprite (16px / 16 = 1.0 tiles). */
    private static final float SKID_QUAD_WIDTH = SKID_SPRITE_WIDTH_PX * PX_TO_GAME_UNITS;

    /** Quad height in game-units (32px / 16 = 2.0 tiles). */
    private static final float QUAD_HEIGHT = SPRITE_HEIGHT_PX * PX_TO_GAME_UNITS;

    /** Tail overflow in game-units — shift applied when facing right. */
    private static final float TAIL_OFFSET = TAIL_OVERFLOW_PX * PX_TO_GAME_UNITS;

    private final AssetManager assetManager;

    // Loaded textures — walk
    private Texture stillTexture;
    private Texture walkTexture1;
    private Texture walkTexture2;

    // Loaded textures — run (spread-eagle)
    private Texture runTexture1;
    private Texture runTexture2;
    private Texture runTexture3;

    // Loaded textures — skid (rapid direction change)
    private Texture skidTexture;

    // Animation state
    private int walkAnimTicks;
    private int walkFrameIndex;
    private PlayerState lastRenderedState;
    private PlayerOrientation lastOrientation;
    private int lastWalkFrame = -1;
    private boolean initialized;

    /**
     * Loads all raccoon sprite textures from the classpath. Must be called
     * once after the asset manager is ready (during {@code renderUpdate}).
     */
    public void initialize() {
        if (initialized) {
            return;
        }
        stillTexture = loadSprite("mario_racoon_still.png");
        walkTexture1 = loadSprite("mario_racoon_walking_1.png");
        walkTexture2 = loadSprite("mario_racoon_walking_2.png");
        runTexture1 = loadSprite("mario_racoon_running_1.png");
        runTexture2 = loadSprite("mario_racoon_running_2.png");
        runTexture3 = loadSprite("mario_racoon_running_3.png");
        skidTexture = loadSprite("mario_racoon_rapid_turn.png");
        initialized = true;
    }

    /**
     * Advances the walk animation tick counter and determines which frame to
     * display based on the player's current state, velocity, and orientation.
     *
     * <p>Must be called once per fixed-rate tick from the player's
     * {@code updateFrame()} method. Only handles STILL, WALKING, RUNNING,
     * and POWER_RUNNING states — all other states should be rendered by the
     * caller (e.g. as the legacy cyan box).
     *
     * @param node        the player's scene node to update
     * @param state       the current player state
     * @param orientation the current facing direction
     * @param absDx       absolute horizontal velocity (game-units/tick)
     * @return {@code true} if this animator rendered a sprite for the given
     *         state, {@code false} if the state is not handled and the caller
     *         should provide its own rendering (e.g. cyan box fallback).
     */
    public boolean tick(
        final Node node,
        final PlayerState state,
        final PlayerOrientation orientation,
        final double absDx
    ) {
        if (!initialized || node == null) {
            return false;
        }

        if (state == STILL) {
            // Reset walk animation when standing still
            walkAnimTicks = 0;
            walkFrameIndex = 2; // Original: Player_WalkFrame forced to 2 when still

            if (lastRenderedState != STILL || lastOrientation != orientation) {
                rebuildWithTexture(node, stillTexture, orientation);
                lastRenderedState = STILL;
                lastOrientation = orientation;
                lastWalkFrame = -1;
            }
            return true;
        }

        if (state == SKIDDING) {
            // Skid frame — single static sprite while braking (prg008.asm:
            // Player_SkidFrame). Orientation stays as movement direction.
            walkAnimTicks = 0;
            walkFrameIndex = 0;

            if (lastRenderedState != SKIDDING || lastOrientation != orientation) {
                rebuildSkidTexture(node, skidTexture, orientation);
                lastRenderedState = SKIDDING;
                lastOrientation = orientation;
                lastWalkFrame = -1;
            }
            return true;
        }

        if (state == WALKING || state == RUNNING || state == POWER_RUNNING) {
            // Advance walk animation based on NES tick timing
            advanceWalkAnimation(absDx);

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
            return true;
        }

        // State not handled by this animator (jumping, falling, flying, etc.)
        // Signal caller to use fallback rendering.
        lastRenderedState = null;
        lastOrientation = null;
        lastWalkFrame = -1;
        return false;
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
    }

    /**
     * Maps the project's floating-point velocity to the NES tick-max table index.
     *
     * <p>In the original: index = abs(Player_XVel) >> 3, where Player_XVel is
     * 4.4 fixed-point (raw $00–$38). The project stores velocity as
     * raw_value / 16.0, so raw = absDx * 16. Index = (int)(raw) >> 3.
     */
    private void advanceWalkAnimation(final double absDx) {
        final int rawVel = (int) (absDx * 16.0);
        final int tickIndex = Math.min(rawVel >> 3, WALK_ANIM_TICK_MAX.length - 1);
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
            case 1 -> walkTexture2;
            case 2 -> stillTexture;
            default -> walkTexture1;
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
            case 1 -> runTexture2;
            case 2 -> runTexture3;
            default -> runTexture1;
        };
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
        final PlayerOrientation orientation
    ) {
        rebuildWithTexture(node, texture, orientation, QUAD_WIDTH, TAIL_OFFSET);
    }

    /**
     * Variant used for the skid sprite which has no tail overflow.
     */
    private void rebuildSkidTexture(
        final Node node,
        final Texture texture,
        final PlayerOrientation orientation
    ) {
        rebuildWithTexture(node, texture, orientation, SKID_QUAD_WIDTH, 0f);
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
        final PlayerOrientation orientation,
        final float quadWidth,
        final float tailOffset
    ) {
        node.detachAllChildren();

        final Quad quad = new Quad(quadWidth, QUAD_HEIGHT);
        final Geometry geometry = new Geometry("Player", quad);

        final Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setTexture("ColorMap", texture);
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);

        geometry.setMaterial(material);
        geometry.setQueueBucket(RenderQueue.Bucket.Transparent);

        // Sprites are drawn facing left: body on the left, tail on the right.
        // The node origin corresponds to Player_X (left edge of the collision box).
        if (orientation == LEFT) {
            // Facing left: quad origin is at node origin. The tail overflows
            // to the right beyond the collision box — visually correct.
            geometry.setLocalTranslation(0, 0, 0);
            geometry.setLocalScale(1, 1, 1);
        } else {
            // Facing right: flip horizontally. After flipping, the body
            // (originally left-side) is now on the right, so shift the quad
            // left by the tail overflow to re-align the body with the
            // collision box.
            material.getAdditionalRenderState().setFaceCullMode(RenderState.FaceCullMode.Off);
            geometry.setLocalScale(-1, 1, 1);
            geometry.setLocalTranslation(quadWidth - tailOffset, 0, 0);
        }

        node.attachChild(geometry);
    }

    private Texture loadSprite(final String filename) {
        final Texture texture = assetManager.loadTexture(SPRITE_PATH + filename);
        texture.setMagFilter(Texture.MagFilter.Nearest);
        texture.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
        texture.setWrap(Texture.WrapMode.EdgeClamp);
        return texture;
    }
}
