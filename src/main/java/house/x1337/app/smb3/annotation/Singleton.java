package house.x1337.app.smb3.annotation;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

@Target({
    METHOD,
    TYPE
})
@Retention(RUNTIME)
@Scope(SCOPE_SINGLETON)
@Component
public @interface Singleton {
}
