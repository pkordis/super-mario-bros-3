package house.x1337.app.smb3.game.object;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.level.AnimatableLevelObject;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.util.CastCapable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ListableBeanFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface Animator {
    void reset();

    /**
     * The one place every animator bean is discovered, whichever family it belongs to. Families are
     * selected by subtype, because they are reset at different moments: the tile-bound
     * {@link GameObjectAnimator}s when the collision grid is (re)built, which is also when they are
     * re-registered, and the enemy animators when the enemy world is set up. Resetting one family at the
     * other's moment would undo a registration that had just been made.
     */
    @Singleton
    @RequiredArgsConstructor
    class Registry implements CastCapable {
        private final ListableBeanFactory beanFactory;
        @Getter(lazy = true)
        private final List<? extends Animator> all = findAll();
        @Getter(lazy = true)
        private final Map<LevelObjectType, GameObjectAnimator<AnimatableLevelObject>> allMapped =
            findAllMapped();

        public void resetAll(final Class<? extends Animator> family) {
            getAll(family).forEach(Animator::reset);
        }

        public <A extends Animator> List<A> getAll(final Class<A> family) {
            return getAll()
                .stream()
                .filter(family::isInstance)
                .map(family::cast)
                .toList();
        }

        public List<GameObjectAnimator<AnimatableLevelObject>> getTileBoundAnimators() {
            return getAll()
                .stream()
                .filter(GameObjectAnimator.class::isInstance)
                .map(this::<GameObjectAnimator<AnimatableLevelObject>>checkedCast)
                .toList();
        }

        public GameObjectAnimator<AnimatableLevelObject> findSuitableAnimator(
            final LevelObjectType animatableObjectType
        ) {
            final GameObjectAnimator<AnimatableLevelObject> animator = getAllMapped().get(animatableObjectType);
            if (animator == null) {
                throw new IllegalStateException(
                    "No suitable GameObjectAnimator found for type " + animatableObjectType
                );
            }
            return animator;
        }

        private Map<LevelObjectType, GameObjectAnimator<AnimatableLevelObject>> findAllMapped() {
            final Map<LevelObjectType, GameObjectAnimator<AnimatableLevelObject>> map = new HashMap<>();
            for (final GameObjectAnimator<AnimatableLevelObject> animator : getTileBoundAnimators()) {
                for (final LevelObjectType supportedType : animator.getSupportedTypes()) {
                    map.put(supportedType, animator);
                }
            }
            return map;
        }

        private List<? extends Animator> findAll() {
            final ListableBeanFactory beanFactory = getBean(Registry.class).beanFactory;
            assert beanFactory != null;
            return beanFactory
                .getBeansOfType(Animator.class)
                .values()
                .stream()
                .map(this::<Animator>checkedCast)
                .toList();
        }
    }
}
