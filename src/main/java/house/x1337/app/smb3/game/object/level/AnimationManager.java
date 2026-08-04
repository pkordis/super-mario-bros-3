package house.x1337.app.smb3.game.object.level;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.util.loader.ImageResourceLoader;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ListableBeanFactory;

import java.util.List;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

public interface AnimationManager extends ImageResourceLoader {
    void update();

    @Singleton
    @RequiredArgsConstructor
    class Registry {
        private final ListableBeanFactory beanFactory;
        @Getter(lazy = true)
        private final List<? extends AnimationManager> all = findAll();

        private List<? extends AnimationManager> findAll() {
            final ListableBeanFactory beanFactory = getBean(AnimationManager.Registry.class).beanFactory;
            assert beanFactory != null;
            return beanFactory
                .getBeansOfType(AnimationManager.class)
                .values()
                .stream()
                .toList();
        }
    }
}
