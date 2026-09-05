package house.x1337.app.smb3.game.player.level.animator;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.player.level.asset.NormalAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.RaccoonAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.ShrunkAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.ShrunkToNormalAnimatorAssets;
import lombok.RequiredArgsConstructor;

@Prototype
@RequiredArgsConstructor
public class LevelScenePlayerAnimationContext {
    private final ShrunkAnimator shrunkAnimator;
    private final NormalAnimator normalAnimator;
    private final RaccoonAnimator raccoonAnimator;
    private final ShrunkToNormalAnimator shrunkToNormalAnimator;
    private final EmptyAnimator emptyAnimator;
    private LevelScenePlayerAnimator<?> activeAnimator;

    public void updateActiveAnimator(final LevelScenePlayer levelScenePlayer) {
        activeAnimator = switch (levelScenePlayer.getMode()) {
            case SHRUNK -> shrunkAnimator;
            case NORMAL -> normalAnimator;
            case RACCOON -> raccoonAnimator;
            case TANOOKI -> emptyAnimator;
        };
    }

    /**
     * Forces the active animator to rebuild on its next {@code update}. Invoked
     * from {@code rebuildGeometry} after a mode switch, where the node has just
     * been detached: without this the animator's cached last-rendered state
     * could match the requested frame and skip the rebuild, leaving an empty
     * node (e.g. right after the grow transition flips to NORMAL).
     */
    public void resetActiveAnimator() {
        if (activeAnimator != null) {
            activeAnimator.resetState();
        }
    }

    public void update(final LevelScenePlayer levelScenePlayer) {
        // The small→Super grow transition owns rendering for its whole duration
        // (dasm Player_Grow): a dedicated animator plays the size shimmer, then
        // rendering returns to the mode animators once the player is NORMAL.
        if (levelScenePlayer.getRuntimeState().isGrowing()) {
            shrunkToNormalAnimator.update(levelScenePlayer);
            return;
        }
        shrunkToNormalAnimator.resetState();
        activeAnimator.update(levelScenePlayer);
    }

    public void loadAssets() {
        ShrunkAnimatorAssets.loadFor(shrunkAnimator);
        ShrunkToNormalAnimatorAssets.loadFor(shrunkToNormalAnimator);
        NormalAnimatorAssets.loadFor(normalAnimator);
        RaccoonAnimatorAssets.loadFor(raccoonAnimator);
    }
}
