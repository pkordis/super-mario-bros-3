package house.x1337.app.smb3.game.object;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.game.object.level.AnimatableLevelObject;
import house.x1337.app.smb3.game.object.level.AnimationManager;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ListableBeanFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface GameObjectAnimator<A extends AnimatableLevelObject> extends AnimationManager {
    void add(A animatableLevelObject);
    void reset();
    void registerLevel(
        Geometry interactiveObjectsLayerGeometry,
        LevelSceneDimensions dimensions
    );
    List<Class<? extends A>> getSupportedTypes();

    @Singleton
    @RequiredArgsConstructor
    class Registry {
        private final ListableBeanFactory beanFactory;
        @Getter(lazy = true)
        private final List<? extends GameObjectAnimator<?>> all = findAll();
        @Getter(lazy = true)
        private final Map<Class<? extends AnimatableLevelObject>, GameObjectAnimator<AnimatableLevelObject>> allMapped =
            findAllMapped();

        public void resetAll() {
            getBean(Registry.class)
                .getAll()
                .forEach(GameObjectAnimator::reset);
        }

        private List<? extends GameObjectAnimator<?>> findAll() {
            final ListableBeanFactory beanFactory = getBean(Registry.class).beanFactory;
            assert beanFactory != null;
            return beanFactory
                .getBeansOfType(GameObjectAnimator.class)
                .values()
                .stream()
                .map(a -> (GameObjectAnimator<?>) a)
                .toList();
        }

        private Map<Class<? extends AnimatableLevelObject>, GameObjectAnimator<AnimatableLevelObject>> findAllMapped() {
            final Map<Class<? extends AnimatableLevelObject>, GameObjectAnimator<AnimatableLevelObject>> map =
                new HashMap<>();
            for (final GameObjectAnimator<?> animator : getAll()) {
                for (final Class<? extends AnimatableLevelObject> supportedType : animator.getSupportedTypes()) {
                    map.put(supportedType, downcast(animator));
                }
            }
            return map;
        }

        @SuppressWarnings("unchecked")
        private GameObjectAnimator<AnimatableLevelObject> downcast(final GameObjectAnimator<?> animator) {
            return (GameObjectAnimator<AnimatableLevelObject>) animator;
        }

        public GameObjectAnimator<AnimatableLevelObject> findSuitableAnimator(
            final Class<? extends AnimatableLevelObject> animatableObjectType
        ) {
            final GameObjectAnimator<AnimatableLevelObject> animator = getAllMapped().get(animatableObjectType);
            if (animator == null) {
                throw new IllegalStateException(
                    "No suitable GameObjectAnimator found for type " + animatableObjectType
                );
            }
            return animator;
        }
    }
}
