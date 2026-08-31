package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.util.loader.ImageResourceLoader;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ListableBeanFactory;

import java.util.List;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface MotionManager extends ImageResourceLoader {
    void update();

    /**
     * Called once per simulation tick <b>after</b> the engine's active-object collision pass has
     * dispatched every {@code onCollisionWith}. Managers react here to collisions detected this
     * tick — e.g. spawning a score caption for a reward that was just collected — so the reaction
     * lands on the same frame the collision was detected. Default: no reaction.
     */
    default void postCollision() {
    }

    @Singleton
    @RequiredArgsConstructor
    class Registry {
        private final ListableBeanFactory beanFactory;
        @Getter(lazy = true)
        private final List<? extends MotionManager> all = findAll();

        private List<? extends MotionManager> findAll() {
            final ListableBeanFactory beanFactory = getBean(MotionManager.Registry.class).beanFactory;
            assert beanFactory != null;
            return beanFactory
                .getBeansOfType(MotionManager.class)
                .values()
                .stream()
                .toList();
        }
    }
}
