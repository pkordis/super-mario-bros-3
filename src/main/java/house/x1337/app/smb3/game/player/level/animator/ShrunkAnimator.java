package house.x1337.app.smb3.game.player.level.animator;

import com.jme3.material.Material;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.enumeration.PlayerMovement;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import house.x1337.app.smb3.model.game.player.asset.ShrunkAnimatorAssets;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import static com.jme3.material.RenderState.BlendMode.Alpha;
import static com.jme3.material.RenderState.FaceCullMode.Off;
import static com.jme3.renderer.queue.RenderQueue.Bucket.Translucent;
import static com.jme3.texture.Texture.MagFilter.Nearest;
import static com.jme3.texture.Texture.MinFilter.NearestNoMipMaps;
import static com.jme3.texture.Texture.WrapMode.EdgeClamp;
import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.LEFT;
import static house.x1337.app.smb3.enumeration.PlayerMovement.FALLING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.JUMPING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.POWER_RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.SKIDDING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.STILL;
import static house.x1337.app.smb3.enumeration.PlayerMovement.WALKING;
import static java.lang.Math.abs;
import static java.lang.Math.min;

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
 * <p>All sprites face left. Facing right is achieved by horizontal flip +
 * X-shift, identical to {@link RaccoonAnimator}.
 */
@Prototype
@RequiredArgsConstructor
public final class ShrunkAnimator implements LevelScenePlayerAnimator<ShrunkAnimatorAssets> {

    /**
     * NES tick counts before advancing to the next walk frame, indexed by
     * velocity tier. Reuses {@code Player_WalkAnimTickMax} from prg008.asm:
     * <pre>
     * .byte $07, $06, $05, $04, $03, $02, $01, $01, $01
     * </pre>
     * Index = abs(Player_XVel) &gt;&gt; 3 in 4.4 fixed-point → (int)(absDx * 16) &gt;&gt; 3.
     */
    private static final int[] WALK_ANIM_TICK_MAX = {7, 6, 5, 4, 3, 2, 1, 1, 1};

    /**
     * Walk animation frame sequence for small Mario (prg008.asm
     * {@code Player_WalkFramesByPUp} row 0): {@code PF_3E, PF_3F, PF_3E, PF_3F}.
     * Maps to sprite indices 0, 1, 0, 1 — only 2 distinct sprites.
     */
    private static final int[] WALK_FRAME_SEQUENCE = {0, 1, 0, 1};

    /**
     * Run ("spread-eagle") frame sequence for small Mario (prg008.asm
     * {@code Player_SpreadEagleFrames} last row): {@code PF_4C, PF_4D, PF_4C, PF_4D}.
     * Maps to sprite indices 0, 1, 0, 1 — only 2 distinct sprites.
     */
    private static final int[] RUN_FRAME_SEQUENCE = {0, 1, 0, 1};

    // Small Mario sprites are 16×16 px (one NES tile row — the upper three
    // OAM slots are all tile $F1/invisible in every small-Mario frame).
    private static final float SPRITE_WIDTH_PX = 16.0f;
    private static final float SPRITE_HEIGHT_PX = 16.0f;
    private static final float PX_TO_GAME_UNITS = 1.0f / TILE_SPRITE_SIZE;

    /** Quad width in game-units (16px / 16 = 1.0 tile). */
    private static final float QUAD_WIDTH = SPRITE_WIDTH_PX * PX_TO_GAME_UNITS;

    /**
     * Quad height in game-units (16px / 16 = 1.0 tile).
     *
     * <p>Small Mario is a single NES tile row tall (16px). The collision
     * system's foot-origin is at Player_Y+32, which is also where the node
     * origin is placed by {@code updateVisualPosition}. The quad extends
     * upward by QUAD_HEIGHT from that origin, so it sits in the lower half
     * of the 32px render slot — exactly where the NES bottom-row OAM sprites
     * sit (Player_SpriteY+16 in prg029 Player_Draw).
     */
    private static final float QUAD_HEIGHT = SPRITE_HEIGHT_PX * PX_TO_GAME_UNITS;

    @Getter
    private final PlayerMode playerMode = SHRUNK;
    @Getter
    private final GameEngine gameEngine;
    @Getter
    private final PlayerIdentity identity;

    // Loaded textures
    @Setter
    private ShrunkAnimatorAssets assets;

    // Animation state
    private int walkAnimTicks;
    private int walkFrameIndex;
    private PlayerMovement lastRenderedState;
    private PlayerOrientationHorizontal lastOrientation;
    private int lastWalkFrame = -1;

    @Override
    public void update(final LevelScenePlayer levelScenePlayer) {
        final Node node = levelScenePlayer.getNode();
        if (node == null) {
            return;
        }

        final PlayerOrientationHorizontal orientation = levelScenePlayer.getOrientationHorizontal();
        final PlayerMovement movement = levelScenePlayer.getRuntimeState().getMovement();
        final int flyTime = levelScenePlayer.getRuntimeState().getPlayerFlyTime();
        final double absDx = abs(levelScenePlayer.getPosition().getDX());

        // -----------------------------------------------------------------------
        // Small Mario CANNOT duck (dasm prg008 PRG008_A70E). The flag is
        // forcefully cleared each frame in LevelScenePlayer.handleDucking(),
        // so we never check isDucking() here — it will always be false.
        // -----------------------------------------------------------------------

        if (movement == STILL) {
            walkAnimTicks = 0;
            walkFrameIndex = 2; // match raccoon animator convention (WalkFrame=2 when still)

            if (lastRenderedState != STILL || lastOrientation != orientation) {
                rebuildWithTexture(node, assets.stillTexture(), orientation);
                lastRenderedState = STILL;
                lastOrientation = orientation;
                lastWalkFrame = -1;
            }
            return;
        }

        if (movement == SKIDDING) {
            walkAnimTicks = 0;
            walkFrameIndex = 0;

            if (lastRenderedState != SKIDDING || lastOrientation != orientation) {
                rebuildWithTexture(node, assets.skidTexture(), orientation);
                lastRenderedState = SKIDDING;
                lastOrientation = orientation;
                lastWalkFrame = -1;
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

            if (lastRenderedState != movement
                    || lastWalkFrame != airFrame
                    || lastOrientation != orientation) {
                rebuildWithTexture(node, airTexture, orientation);
                lastRenderedState = movement;
                lastOrientation = orientation;
                lastWalkFrame = airFrame;
            }
            return;
        }

        // Walking, running (non-power), and power-running all use the walk
        // animation tick system. RUNNING uses the walk sprite set (faster
        // cycle, not spread-eagle). POWER_RUNNING selects the run sprite set
        // (PF_4C/4D — dasm Player_SpreadEagleFrames last row).
        if (movement == POWER_RUNNING
                || movement == RUNNING
                || movement == WALKING) {
            advanceWalkAnimation(absDx);

            final int[] frameSequence = (movement == POWER_RUNNING)
                    ? RUN_FRAME_SEQUENCE : WALK_FRAME_SEQUENCE;
            final int currentSpriteFrame = frameSequence[walkFrameIndex];

            if (lastRenderedState != movement
                    || lastWalkFrame != currentSpriteFrame
                    || lastOrientation != orientation) {
                final Texture texture = (movement == POWER_RUNNING)
                        ? textureForRunFrame(currentSpriteFrame)
                        : textureForWalkFrame(currentSpriteFrame);
                rebuildWithTexture(node, texture, orientation);
                lastRenderedState = movement;
                lastOrientation = orientation;
                lastWalkFrame = currentSpriteFrame;
            }
            return;
        }

        // Unhandled state — reset so the next handled state forces a rebuild.
        lastRenderedState = null;
        lastOrientation = null;
        lastWalkFrame = -1;
    }

    /**
     * Resets internal tracking so the next {@link #update} call unconditionally
     * rebuilds the geometry. Call this when switching away from this animator.
     */
    public void resetState() {
        lastRenderedState = null;
        lastOrientation = null;
        lastWalkFrame = -1;
        walkAnimTicks = 0;
        walkFrameIndex = 0;
    }

    // -------------------------------------------------------------------------
    // Animation helpers
    // -------------------------------------------------------------------------

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
     * Returns the walk texture for the given frame index.
     * Small Mario has 2 distinct walk sprites (PF_3E, PF_3F).
     */
    private Texture textureForWalkFrame(final int spriteFrame) {
        if (spriteFrame == 1) {
            return assets.walkTexture();
        }
        return assets.stillTexture();
    }

    /**
     * Returns the run texture for the given frame index.
     * Small Mario has 2 distinct run sprites (PF_4C, PF_4D).
     */
    private Texture textureForRunFrame(final int spriteFrame) {
        if (spriteFrame == 1) {
            return assets.runTexture2();
        }
        return assets.runTexture1();
    }

    // -------------------------------------------------------------------------
    // Geometry building
    // -------------------------------------------------------------------------

    /**
     * Replaces the node's children with a textured 16×16 quad anchored at
     * the foot origin (node origin = Player_X, Player_Y+32 foot position).
     *
     * <p>The quad is 1.0×1.0 game-units matching the actual sprite size.
     * It occupies the lower half of the 32px render slot, exactly mirroring
     * the NES where small Mario's bottom-row OAM sprites sit at Y+16.
     *
     * <p>Sprites face left. Facing right is a horizontal flip + X-shift so
     * the left edge (collision-box origin) stays at the node origin.
     */
    private void rebuildWithTexture(
        final Node node,
        final Texture texture,
        final PlayerOrientationHorizontal orientation
    ) {
        node.detachAllChildren();

        texture.setMagFilter(Nearest);
        texture.setMinFilter(NearestNoMipMaps);
        texture.setWrap(EdgeClamp);

        final Quad quad = new Quad(QUAD_WIDTH, QUAD_HEIGHT);
        final Geometry geometry = new Geometry("Player", quad);

        final Material material = new Material(getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        material.setTexture("ColorMap", texture);
        material.getAdditionalRenderState().setBlendMode(Alpha);

        geometry.setMaterial(material);
        geometry.setQueueBucket(Translucent);

        if (orientation == LEFT) {
            geometry.setLocalTranslation(0, 0, 0);
            geometry.setLocalScale(1, 1, 1);
        } else {
            // Flip horizontally. Small sprites are exactly body-width (16px =
            // QUAD_WIDTH game-units) with no tail overflow, so after flipping
            // the origin aligns correctly with a simple full-width shift.
            material.getAdditionalRenderState().setFaceCullMode(Off);
            geometry.setLocalScale(-1, 1, 1);
            geometry.setLocalTranslation(QUAD_WIDTH, 0, 0);
        }

        node.attachChild(geometry);
    }
}
