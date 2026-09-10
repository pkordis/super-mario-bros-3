package house.x1337.app.smb3.game.player.level.animator;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.effect.PoofSequence;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import house.x1337.app.smb3.model.game.player.level.asset.LargeToRaccoonAnimatorAssets;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.PlayerMode.RACCOON;
import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.LEFT;
import static house.x1337.app.smb3.model.game.effect.PoofSequence.POOF_FRAMES_CONTEXT;

@Data
@Prototype
@RequiredArgsConstructor
public final class LargeToRaccoonAnimator implements LevelScenePlayerAnimator<LargeToRaccoonAnimatorAssets> {
    private final PoofSequence poofSequence = PoofSequence.wrapping();

    private final PlayerMode playerMode = RACCOON;
    private final GameEngine gameEngine;
    private final PlayerIdentity identity;

    private int lastFrameIndex = -1;
    private LargeToRaccoonAnimatorAssets assets;

    /**
     * The poof art is player-independent and shared with the switch-block puff, so it is loaded from the
     * common effect path rather than from under {@code sprites/player/...}.
     *
     * @return the shared poof frame directory
     */
    @Override
    public String getFramesParentContext() {
        return POOF_FRAMES_CONTEXT;
    }

    @Override
    public void resetState() {
        lastFrameIndex = -1;
    }

    @Override
    public void update(final LevelScenePlayer levelScenePlayer) {
        final int poofCounter = levelScenePlayer.getRuntimeState().getPoofCounter();
        final int frameIndex = poofSequence.frameIndexFor(poofCounter);

        if (frameIndex == lastFrameIndex) {
            return;
        }
        lastFrameIndex = frameIndex;
        rebuildWithTexture(levelScenePlayer.getNode(), assets.poof()[frameIndex], LEFT);
    }
}
