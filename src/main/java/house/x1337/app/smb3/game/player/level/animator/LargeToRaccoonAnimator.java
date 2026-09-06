package house.x1337.app.smb3.game.player.level.animator;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import house.x1337.app.smb3.model.game.player.level.asset.LargeToRaccoonAnimatorAssets;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;
import static house.x1337.app.smb3.model.game.player.level.asset.LargeToRaccoonAnimatorAssets.POOF_FRAMES;
import static java.lang.Math.clamp;

@Data
@Prototype
@RequiredArgsConstructor
public final class LargeToRaccoonAnimator implements LevelScenePlayerAnimator<LargeToRaccoonAnimatorAssets> {
    private static final int POOF_FRAME_SHIFT = 2;

    private final PlayerMode playerMode = RACCOON;
    private final GameEngine gameEngine;
    private final PlayerIdentity identity;

    private int lastFrameIndex = -1;
    private LargeToRaccoonAnimatorAssets assets;
    private PlayerOrientationHorizontal lastOrientation;

    @Override
    public String getFramesParentContext() {
        return "sprites/player/%s/level/large_to_raccoon/"
            .formatted(getIdentity().getAnimationFramesPath());
    }

    @Override
    public void resetState() {
        lastFrameIndex = -1;
        lastOrientation = null;
    }

    @Override
    public void update(final LevelScenePlayer levelScenePlayer) {
        final int poofCounter = levelScenePlayer.getRuntimeState().getPoofCounter();
        final int step = clamp(poofCounter >> POOF_FRAME_SHIFT, 0, POOF_FRAMES.length - 1);
        final int frameIndex = POOF_FRAMES[step];
        final PlayerOrientationHorizontal orientation = levelScenePlayer.getOrientation().getHorizontal();

        if (frameIndex == lastFrameIndex && orientation == lastOrientation) {
            return;
        }
        lastFrameIndex = frameIndex;
        lastOrientation = orientation;
        rebuildWithTexture(levelScenePlayer.getNode(), assets.poof()[frameIndex], orientation);
    }
}
