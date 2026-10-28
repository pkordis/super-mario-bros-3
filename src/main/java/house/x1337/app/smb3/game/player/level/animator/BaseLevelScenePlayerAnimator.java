package house.x1337.app.smb3.game.player.level.animator;

import com.jme3.scene.Node;
import com.jme3.texture.Texture;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.PlayerMovement;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.game.player.PlayerModeAware;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.player.PlayerAnimatorAssetsMoving;
import house.x1337.app.smb3.model.game.player.PlayerRuntimeState;
import house.x1337.app.smb3.model.game.player.level.LevelScenePlayerAnimatorSpecifications;
import lombok.Data;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.enumeration.PlayerMovement.POWER_RUNNING;
import static house.x1337.app.smb3.enumeration.PlayerMovement.STILL;
import static java.lang.Math.abs;
import static java.lang.Math.min;

@Data
public abstract class BaseLevelScenePlayerAnimator<A extends PlayerAnimatorAssetsMoving>
    implements
        LevelScenePlayerAnimator<A>,
        PlayerModeAware {
    static final int[] WALK_ANIM_TICK_MAX = {7, 6, 5, 4, 3, 2, 1, 1, 1};
    static final int AIRBORNE_HOLD_FRAME = 0;
    static final int AIRBORNE_HOLD_FRAME_SMALL = 1;
    static final int FACE_FORWARD_FRAME = -2;
    static final int KICK_FRAME = -3;
    static final int STANDING_WALK_FRAME = 2;

    private A assets;
    private LevelScenePlayerAnimatorSpecifications specifications;
    private PlayerMovement lastRenderedState;
    private PlayerOrientationHorizontal lastOrientation;
    private boolean wasCarryPosed;
    private boolean wasKickPosed;
    private int lastWalkFrame = -1;
    private int walkAnimTicks;
    private int walkFrameIndex;

    @Override
    public String getFramesParentContext() {
        return "sprites/player/%s/level/%s/"
            .formatted(
                getIdentity().getAnimationFramesPath(),
                getMode().name().toLowerCase()
            );
    }

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
        walkFrameIndex = STANDING_WALK_FRAME;
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
                ? assets.run()[currentSpriteFrame]
                : assets.walk()[currentSpriteFrame];
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
        wasCarryPosed = false;
        wasKickPosed = false;
    }

    protected final boolean renderKickPose(
        final Node node,
        final LevelScenePlayer levelScenePlayer,
        final PlayerOrientationHorizontal orientation
    ) {
        if (!levelScenePlayer.getRuntimeState().isKicking()) {
            if (wasKickPosed) {
                // The pose that follows may be the very state the kick rendered under, so the dedup cache
                // has to be invalidated outright or the quad would never be rebuilt.
                wasKickPosed = false;
                wasCarryPosed = false;
                lastRenderedState = null;
                lastOrientation = null;
                lastWalkFrame = -1;
            }
            return false;
        }

        final PlayerMovement movement = levelScenePlayer.getRuntimeState().getMovement();
        if (!wasKickPosed || frameChanged(movement, orientation, KICK_FRAME)) {
            rebuildWithTexture(
                node,
                assets.kick(),
                orientation,
                specifications.getQuadWidth(),
                specifications.getQuadHeight(),
                0f,
                specifications.getKickLeadingOffset()
            );
            markRendered(movement, orientation, KICK_FRAME);
        }
        wasKickPosed = true;
        return true;
    }

    protected final boolean renderCarryPose(
        final Node node,
        final LevelScenePlayer levelScenePlayer,
        final PlayerOrientationHorizontal orientation
    ) {
        final PlayerRuntimeState runtimeState = levelScenePlayer.getRuntimeState();
        final boolean turningAround = runtimeState.isTurningAroundWhileCarrying();
        if (!turningAround && !runtimeState.isCarrying()) {
            if (wasCarryPosed) {
                // Dropping back to the ordinary frames needs a forced rebuild, since the movement state
                // the carry rendered under may not have changed at all.
                wasCarryPosed = false;
                lastRenderedState = null;
                lastOrientation = null;
                lastWalkFrame = -1;
            }
            return false;
        }

        final PlayerMovement movement = runtimeState.getMovement();
        final int frame;
        final Texture texture;
        if (turningAround) {
            frame = FACE_FORWARD_FRAME;
            texture = assets.faceForward();
        } else if (runtimeState.isInAir()) {
            frame = isSmall() ? AIRBORNE_HOLD_FRAME_SMALL : AIRBORNE_HOLD_FRAME;
            texture = holdFrame(frame);
        } else {
            final double absDx = abs(levelScenePlayer.getPosition().getDX());
            if (absDx > 0) {
                advanceWalkAnimation(absDx);
            } else {
                walkAnimTicks = 0;
                walkFrameIndex = STANDING_WALK_FRAME;
            }
            frame = walkFrameIndex;
            texture = holdFrame(frame);
        }

        if (!wasCarryPosed || frameChanged(movement, orientation, frame)) {
            // Texture-derived rather than mode-derived: big Mario's hold poses and every mode's
            // camera-facing pose are narrower than their canvas. See rebuildWithTextureDerivedQuad.
            rebuildWithTextureDerivedQuad(node, texture, orientation);
            markRendered(movement, orientation, frame);
        }
        wasCarryPosed = true;
        return true;
    }

    private Texture holdFrame(final int beat) {
        final Texture[] hold = assets.hold();
        return hold[beat % hold.length];
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

    public final void setMode(final PlayerMode playerMode) {
        throw new UnsupportedOperationException(
            "Implementations of BaseLevelScenePlayerAnimator cannot have player's mode changed. It must be final"
        );
    }
}
