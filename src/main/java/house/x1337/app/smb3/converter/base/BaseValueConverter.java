package house.x1337.app.smb3.converter.base;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

public abstract class BaseValueConverter<T> implements Converter<String, T>, BeanFactoryPostProcessor {
    @Override
    public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory) throws BeansException {
        final Environment environment = beanFactory.getBean(Environment.class);
        if (environment instanceof ConfigurableEnvironment configurableEnvironment) {
            configurableEnvironment.getConversionService().addConverter(this);
        }
    }
}
