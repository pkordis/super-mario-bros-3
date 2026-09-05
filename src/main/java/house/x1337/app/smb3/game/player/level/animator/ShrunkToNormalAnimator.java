package house.x1337.app.smb3.game.player.level.animator;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import house.x1337.app.smb3.model.game.player.level.asset.ShrunkToNormalAnimatorAssets;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.model.game.player.level.asset.ShrunkToNormalAnimatorAssets.GROW_FRAMES;
import static java.lang.Math.clamp;

@Data
@Prototype
@RequiredArgsConstructor
public final class ShrunkToNormalAnimator implements LevelScenePlayerAnimator<ShrunkToNormalAnimatorAssets> {
    private static final int GROW_FRAME_SHIFT = 2;

    private final PlayerMode playerMode = NORMAL;
    private final GameEngine gameEngine;
    private final PlayerIdentity identity;

    private int lastFrameIndex = -1;
    private ShrunkToNormalAnimatorAssets assets;
    private PlayerOrientationHorizontal lastOrientation;

    @Override
    public String getFramesParentContext() {
        return "sprites/player/%s/level/shrunk_to_normal/"
            .formatted(getIdentity().getAnimationFramesPath());
    }

    @Override
    public void resetState() {
        lastFrameIndex = -1;
        lastOrientation = null;
    }

    @Override
    public void update(final LevelScenePlayer levelScenePlayer) {
        final int growCounter = levelScenePlayer.getRuntimeState().getGrowCounter();
        final int step = clamp(growCounter >> GROW_FRAME_SHIFT, 0, GROW_FRAMES.length - 1);
        final int frameIndex = GROW_FRAMES[step];
        final PlayerOrientationHorizontal orientation = levelScenePlayer.getOrientation().getHorizontal();

        if (frameIndex == lastFrameIndex && orientation == lastOrientation) {
            return;
        }
        lastFrameIndex = frameIndex;
        lastOrientation = orientation;
        rebuildWithTexture(levelScenePlayer.getNode(), assets.growing()[frameIndex], orientation);
    }
}
