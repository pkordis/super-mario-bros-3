package house.x1337.app.smb3.game.player.factory;

import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.player.level.LevelScenePlayerRenderer;
import house.x1337.app.smb3.game.player.level.animator.EmptyAnimator;
import house.x1337.app.smb3.game.player.level.animator.LevelScenePlayerAnimationContext;
import house.x1337.app.smb3.game.player.level.animator.RaccoonAnimator;
import house.x1337.app.smb3.game.player.level.animator.ShrunkAnimator;
import house.x1337.app.smb3.model.game.player.PlayerIdentity;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface PlayerAnimatorFactory {
    static LevelScenePlayerAnimationContext contextForLevel(
        final LevelScenePlayerRenderer renderer
    ) {
        final GameEngine e = renderer.getGameEngine();
        final PlayerIdentity i = renderer.getIdentity();
        return getBean(
            LevelScenePlayerAnimationContext.class,
            getBean(ShrunkAnimator.class, e, i),
            getBean(RaccoonAnimator.class, e, i),
            getBean(EmptyAnimator.class, e, i)
        );
    }
}
