package house.x1337.app.smb3.bean;

import house.x1337.app.smb3.annotation.Singleton;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.BeanFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@RequiredArgsConstructor
public class StaticBeanFactory {
    private static final Map<Class<?>, Object> SINGLETONS_CACHE = new ConcurrentHashMap<>();
    private static StaticBeanFactory INSTANCE = null;

    @PostConstruct
    void init() {
        if (INSTANCE == null) {
            INSTANCE = this;
        }
    }

    private final BeanFactory beanFactory;

    public static <T> T getBean(
        final Class<T> clazz,
        final Object... args
    ) {
        if (SINGLETONS_CACHE.containsKey(clazz)) {
            @SuppressWarnings("unchecked")
            final T instance = (T) SINGLETONS_CACHE.get(clazz);
            return instance;
        }
        final T instance = INSTANCE.beanFactory.getBean(clazz, args);
        if (clazz.getAnnotation(Singleton.class) != null) {
            SINGLETONS_CACHE.put(clazz, instance);
        }
        return instance;
    }
}
