package house.x1337.app.smb3.game.player.level.animator;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PlayerMode;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.effect.PoofSequence;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;
import house.x1337.app.smb3.model.game.player.level.asset.SuitLostPoofAnimatorAssets;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.enumeration.PlayerOrientationHorizontal.LEFT;
import static house.x1337.app.smb3.model.game.effect.PoofSequence.POOF_FRAMES_CONTEXT;

/**
 * Draws the {@code Player_SuitLost} poof cloud in place of the player sprite, whichever direction the
 * suit change is going (dasm prg029 {@code Player_SuitLost_DoPoof}).
 *
 * <p>Deliberately not named for a destination mode. One counter and one cloud serve both the Super
 * Leaf's promotion to Raccoon ({@code ObjHit_SuperLeaf}) and the suit loss back down to Normal that a
 * hit causes ({@code Player_GetHurt}); where the player lands is decided by the queued mode, not here.
 */
@Data
@Prototype
@RequiredArgsConstructor
public final class SuitLostPoofAnimator implements LevelScenePlayerAnimator<SuitLostPoofAnimatorAssets> {
    private final PoofSequence poofSequence = PoofSequence.wrapping();

    /**
     * Inert. The interface requires a mode purely to build the default sprite path, which this animator
     * overrides with the shared effect directory — so nothing ever reads this, and no value of it would
     * be more correct than another for a cloud that plays in both directions.
     */
    private final PlayerMode playerMode = null;
    private final GameEngine gameEngine;
    private final PlayerIdentity identity;

    private int lastFrameIndex = -1;
    private SuitLostPoofAnimatorAssets assets;

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
