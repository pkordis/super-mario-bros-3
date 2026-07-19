package house.x1337.app.smb3.game.player.level.animator;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import lombok.RequiredArgsConstructor;

@Prototype
@RequiredArgsConstructor
public class LevelScenePlayerAnimationContext {
    private final RaccoonAnimator raccoonAnimator;
    private final EmptyAnimator emptyAnimator;
    private LevelScenePlayerAnimator activeAnimator;

    public void updateActiveAnimator(final LevelScenePlayer levelScenePlayer) {
        activeAnimator = switch (levelScenePlayer.getMode()) {
            case SHRUNK -> emptyAnimator;
            case NORMAL -> emptyAnimator;
            case RACCOON -> raccoonAnimator;
            case TANOOKI -> emptyAnimator;
        };
    }

    public void update(final LevelScenePlayer levelScenePlayer) {
        activeAnimator.update(levelScenePlayer);
    }

    public void loadAssets() {
        raccoonAnimator.loadAssets();
    }
}
