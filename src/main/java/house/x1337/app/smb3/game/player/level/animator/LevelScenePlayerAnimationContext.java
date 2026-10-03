package house.x1337.app.smb3.game.player.level.animator;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.player.level.asset.NormalAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.RaccoonAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.ShrunkAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.SizeChangingAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.SuitLostPoofAnimatorAssets;
import lombok.RequiredArgsConstructor;

@Prototype
@RequiredArgsConstructor
public class LevelScenePlayerAnimationContext {
    private final ShrunkAnimator shrunkAnimator;
    private final NormalAnimator normalAnimator;
    private final RaccoonAnimator raccoonAnimator;
    private final SizeChangingAnimator sizeChangingAnimator;
    private final SuitLostPoofAnimator suitLostPoofAnimator;
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
        // The size transitions own rendering for their whole duration (dasm Player_Grow): a dedicated
        // animator plays the size shimmer, then rendering returns to the mode animators. The one grow
        // counter runs in both directions — a small→Super grow and the Normal→Shrunk shrink a hit
        // causes — so the queued mode is what tells the two apart (dasm reverses the frame index when
        // the suit is small, prg029 @ PRG029_D22E).
        if (levelScenePlayer.getRuntimeState().isChangingSize()) {
            sizeChangingAnimator.update(levelScenePlayer);
            return;
        }
        // Likewise every suit change that is not a size change (dasm Player_SuitLost): the poof cloud
        // replaces the player sprite until the queued mode takes effect — whether that is the Raccoon
        // suit being gained or an advanced suit being lost to a hit.
        if (levelScenePlayer.getRuntimeState().isTurningToRaccoon()) {
            suitLostPoofAnimator.update(levelScenePlayer);
            return;
        }
        sizeChangingAnimator.resetState();
        suitLostPoofAnimator.resetState();
        activeAnimator.update(levelScenePlayer);
    }

    public void loadAssets() {
        ShrunkAnimatorAssets.loadFor(shrunkAnimator);
        SizeChangingAnimatorAssets.loadFor(sizeChangingAnimator);
        SuitLostPoofAnimatorAssets.loadFor(suitLostPoofAnimator);
        NormalAnimatorAssets.loadFor(normalAnimator);
        RaccoonAnimatorAssets.loadFor(raccoonAnimator);
    }
}
