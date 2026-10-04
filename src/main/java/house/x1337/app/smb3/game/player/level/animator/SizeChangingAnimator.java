package house.x1337.app.smb3.game.player.level.animator;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import house.x1337.app.smb3.model.game.player.level.asset.SizeChangingAnimatorAssets;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.PlayerMode.NORMAL;
import static house.x1337.app.smb3.enumeration.PlayerMode.SHRUNK;

@Data
@Prototype
@RequiredArgsConstructor
public final class SizeChangingAnimator implements LevelScenePlayerAnimator<SizeChangingAnimatorAssets> {
    private final PlayerMode playerMode = NORMAL;
    private final GameEngine gameEngine;
    private final PlayerIdentity identity;

    private int lastFrameIndex = -1;
    private SizeChangingAnimatorAssets assets;
    private PlayerOrientationHorizontal lastOrientation;

    @Override
    public String getFramesParentContext() {
        return "sprites/player/%s/level/size_changing/"
            .formatted(getIdentity().getAnimationFramesPath());
    }

    @Override
    public void resetState() {
        lastFrameIndex = -1;
        lastOrientation = null;
    }

    @Override
    public void update(final LevelScenePlayer levelScenePlayer) {
        final int growShrinkCounter = levelScenePlayer.getRuntimeState().getGrowShrinkCounter();
        // One counter, one table, both directions — the shrink reads the table backwards so it lands on
        // the shrunk frame rather than playing the grow sequence and snapping small at the end (dasm
        // prg029 @ PRG029_D22E). The destination comes from the queued suit, as the ROM's reversal does.
        final boolean shrinking = levelScenePlayer.getRuntimeState().getQueuedMode() == SHRUNK;
        final int frameIndex = SizeChangingAnimatorAssets.frameIndexFor(growShrinkCounter, shrinking);
        final PlayerOrientationHorizontal orientation = levelScenePlayer.getOrientation().getHorizontal();

        if (frameIndex == lastFrameIndex && orientation == lastOrientation) {
            return;
        }
        lastFrameIndex = frameIndex;
        lastOrientation = orientation;
        rebuildWithTexture(levelScenePlayer.getNode(), assets.sizeChanging()[frameIndex], orientation);
    }
}
