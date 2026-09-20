package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.util.CastCapable;
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
        // Do nothing
    }

    /**
     * Called once per simulation tick <b>instead of</b> {@link #update()} while gameplay is halted
     * (dasm {@code Player_HaltGame} — e.g. the small→Super grow freeze). Almost everything must stand
     * still, so the default does nothing. Managers that own an animation the ROM keeps running
     * through the freeze — the rising score caption — override this to advance only that animation.
     */
    default void updateWhileHalted() {
        // Do nothing
    }

    /**
     * Drops whatever state belongs to one level. Managers whose population is spawned during play (the
     * reward items) empty themselves as their objects are collected or expire and need nothing here;
     * managers that are handed the objects a level was <em>authored</em> with — the enemies — must clear
     * them, or a manager singleton carries the previous level's population into the next scene.
     * Default: nothing to drop.
     */
    default void reset() {
        // Do nothing
    }

    @Singleton
    @RequiredArgsConstructor
    class Registry implements CastCapable {
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
                .map(this::<MotionManager>checkedCast)
                .toList();
        }
    }
}
