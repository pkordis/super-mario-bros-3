package house.x1337.app.smb3.game.player.level.animator;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.player.level.asset.NormalAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.RaccoonAnimatorAssets;
import house.x1337.app.smb3.model.game.player.level.asset.ShrunkAnimatorAssets;
import lombok.RequiredArgsConstructor;

@Prototype
@RequiredArgsConstructor
public class LevelScenePlayerAnimationContext {
    private final ShrunkAnimator shrunkAnimator;
    private final NormalAnimator normalAnimator;
    private final RaccoonAnimator raccoonAnimator;
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

    public void update(final LevelScenePlayer levelScenePlayer) {
        activeAnimator.update(levelScenePlayer);
    }

    public void loadAssets() {
        ShrunkAnimatorAssets.loadFor(shrunkAnimator);
        NormalAnimatorAssets.loadFor(normalAnimator);
        RaccoonAnimatorAssets.loadFor(raccoonAnimator);
    }
}
