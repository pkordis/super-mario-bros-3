package house.x1337.app.smb3.game.player.level.animator;

import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.enumeration.PlayerMovement;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.model.game.player.PlayerAnimatorAssetsMoving;
import house.x1337.app.smb3.model.game.player.level.LevelScenePlayerAnimatorSpecifications;
import lombok.Setter;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerMovement.POWER_RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.STILL;
import static java.lang.Math.min;

/**
 * Shared behaviour for the level-scene sprite animators (small and raccoon
 * Mario). Concrete animators differ only in their sprite dimensions, frame
 * sequences and per-frame texture lookups; the walk/run tick timing, the
 * frame-change bookkeeping and the JME quad-rebuild are identical and live here.
 *
 * <p>All timing constants are ported from the SMB3 disassembly (prg008.asm):
 * {@code Player_WalkAnimTickMax} drives how many NES ticks elapse before the
 * walk frame advances, indexed by {@code abs(Player_XVel) >> 3}.
 *
 * <p>Sprites face left; facing right is a horizontal flip plus an X-shift so the
 * collision-box origin stays aligned (see {@link #rebuildWithTexture}).
 *
 * @param <A> the concrete animator's asset bundle type
 */
public abstract class BaseLevelScenePlayerAnimator<A extends PlayerAnimatorAssetsMoving>
    implements LevelScenePlayerAnimator<A> {

    /**
     * NES tick counts before advancing to the next walk frame, indexed by
     * velocity tier. From prg008.asm {@code Player_WalkAnimTickMax}:
     * <pre>
     * .byte $07, $06, $05, $04, $03, $02, $01, $01, $01
     * </pre>
     * Index = abs(Player_XVel) >> 3 in the original 4.4 fixed-point system.
     */
    private static final int[] WALK_ANIM_TICK_MAX = {7, 6, 5, 4, 3, 2, 1, 1, 1};

    protected int walkAnimTicks;
    protected int walkFrameIndex;
    protected PlayerMovement lastRenderedState;
    protected PlayerOrientationHorizontal lastOrientation;
    protected int lastWalkFrame = -1;

    @Setter
    protected LevelScenePlayerAnimatorSpecifications specifications;

    @Setter
    protected A assets;

    protected final void advanceWalkAnimation(final double absDx) {
        final int rawVel = (int) (absDx * TILE_SPRITE_SIZE);
        final int tickIndex = min(rawVel >> 3, WALK_ANIM_TICK_MAX.length - 1);
        final int tickMax = WALK_ANIM_TICK_MAX[tickIndex];

        walkAnimTicks++;
        if (walkAnimTicks >= tickMax) {
            walkAnimTicks = 0;
            walkFrameIndex = (walkFrameIndex + 1) % specifications.getWalkFrameSequence().length;
        }
    }

    protected final boolean frameChanged(
        final PlayerMovement state,
        final PlayerOrientationHorizontal orientation,
        final int frame
    ) {
        return lastRenderedState != state || lastOrientation != orientation || lastWalkFrame != frame;
    }

    protected final void markRendered(
        final PlayerMovement state,
        final PlayerOrientationHorizontal orientation,
        final int frame
    ) {
        lastRenderedState = state;
        lastOrientation = orientation;
        lastWalkFrame = frame;
    }

    protected final void renderStill(
        final Node node,
        final PlayerOrientationHorizontal orientation,
        final Texture stillTexture
    ) {
        walkAnimTicks = 0;
        walkFrameIndex = 2;
        if (frameChanged(STILL, orientation, -1)) {
            rebuildWithTexture(node, stillTexture, orientation);
            markRendered(STILL, orientation, -1);
        }
    }

    protected final void renderWalkRun(
        final Node node,
        final PlayerMovement movement,
        final PlayerOrientationHorizontal orientation,
        final double absDx
    ) {
        advanceWalkAnimation(absDx);

        final boolean powerRunning = movement == POWER_RUNNING;
        final int[] frameSequence = powerRunning
            ? specifications.getRunFrameSequence() : specifications.getWalkFrameSequence();
        final int currentSpriteFrame = frameSequence[walkFrameIndex];
        if (frameChanged(movement, orientation, currentSpriteFrame)) {
            final Texture texture = powerRunning
                ? assets.runFrameTextures()[currentSpriteFrame]
                : assets.walkFrameTextures()[currentSpriteFrame];
            rebuildWithTexture(node, texture, orientation);
            markRendered(movement, orientation, currentSpriteFrame);
        }
    }

    public void resetState() {
        lastRenderedState = null;
        lastOrientation = null;
        lastWalkFrame = -1;
        walkAnimTicks = 0;
        walkFrameIndex = 0;
    }

    protected final void rebuildWithTexture(
        final Node node,
        final Texture texture,
        final PlayerOrientationHorizontal orientation,
        final float quadWidth,
        final float rightPadding
    ) {
        rebuildWithTexture(
            node,
            texture,
            orientation,
            quadWidth,
            specifications.getQuadHeight(),
            rightPadding
        );
    }

    @Override
    public final void rebuildWithTexture(
        final Node node,
        final Texture texture,
        final PlayerOrientationHorizontal orientation
    ) {
        rebuildWithTexture(
            node,
            texture,
            orientation,
            specifications.getQuadWidth(),
            specifications.getRightPadding()
        );
    }
}
