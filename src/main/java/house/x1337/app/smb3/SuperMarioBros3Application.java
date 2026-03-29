package house.x1337.app.smb3;

import house.x1337.app.smb3.app.BeforeSpringInitialization;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class SuperMarioBros3Application {
    static void main(final String... args) {
        new SpringApplicationBuilder(SuperMarioBros3Application.class)
            .sources(BeforeSpringInitialization.class)
            .headless(false)
            .properties("spring.main.web-application-type=none")
            .run(args);
    }
}
