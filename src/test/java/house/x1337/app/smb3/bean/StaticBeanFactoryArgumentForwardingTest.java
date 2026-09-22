package house.x1337.app.smb3.bean;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards how bean lookups are forwarded to Spring.
 *
 * <p>Spring reads a non-null constructor-argument array as <em>explicit</em> arguments, so forwarding an
 * empty varargs array asks for a no-argument constructor instead of autowiring. A bean with dependencies
 * that has not been instantiated yet then fails to resolve — and because an already-created singleton is
 * returned before constructor resolution is reached, the failure depends on bean creation order and
 * surfaces only on some startups. Hence: no arguments means no arguments passed.
 */
class StaticBeanFactoryArgumentForwardingTest {

    private static BeanFactory beanFactory;

    /** A bean with a dependency, i.e. one with no no-argument constructor. */
    private static class NeedsDependencies {
    }

    /**
     * Installed once: {@code init()} claims the static instance only while it is unset, so every test here
     * has to share the one bean factory it was given.
     */
    @BeforeAll
    static void installFactory() {
        beanFactory = mock(BeanFactory.class);
        new StaticBeanFactory(beanFactory).init();
    }

    @BeforeEach
    void prepare() {
        reset(beanFactory);
    }

    @Test
    @DisplayName("A lookup with no arguments autowires rather than demanding a no-argument constructor")
    void noArgumentsAreForwardedAsNone() {
        // Prepare
        final NeedsDependencies bean = new NeedsDependencies();
        when(beanFactory.getBean(NeedsDependencies.class)).thenReturn(bean);

        // Execute
        final NeedsDependencies resolved = StaticBeanFactory.getBean(NeedsDependencies.class);

        // Verify
        assertThat(resolved).isSameAs(bean);
        verify(beanFactory).getBean(NeedsDependencies.class);
        verify(beanFactory, never()).getBean(NeedsDependencies.class, new Object[0]);
    }

    @Test
    @DisplayName("Explicit arguments are still forwarded, so prototypes can be constructed with them")
    void explicitArgumentsAreForwarded() {
        // Prepare
        final NeedsDependencies bean = new NeedsDependencies();
        when(beanFactory.getBean(NeedsDependencies.class, "an-argument")).thenReturn(bean);

        // Execute
        final NeedsDependencies resolved = StaticBeanFactory.getBean(NeedsDependencies.class, "an-argument");

        // Verify
        assertThat(resolved).isSameAs(bean);
        verify(beanFactory).getBean(NeedsDependencies.class, "an-argument");
    }
}
