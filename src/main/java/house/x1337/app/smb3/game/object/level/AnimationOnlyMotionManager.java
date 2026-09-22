package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.model.AnimationImageResource;
import house.x1337.app.smb3.model.game.Offset;

import java.util.Iterator;
import java.util.List;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface AnimationOnlyMotionManager<A extends Animation> extends MotionManager {
    Class<A> getAnimationType();
    List<A> getActiveAnimations();
    AnimationImageResource getAnimationFrames();

    @Override
    default void update() {
        final Iterator<A> iterator = getActiveAnimations().iterator();
        while (iterator.hasNext()) {
            final A animation = iterator.next();
            animation.tick();
            if (animation.isExpired()) {
                animation.detach();
                iterator.remove();
            }
        }
    }

    default void spawn(
        final GameEngine gameEngine,
        final Offset offset
    ) {
        final A animation = getBean(getAnimationType(), gameEngine, offset, getAnimationFrames());
        getActiveAnimations().add(animation);
    }
}
