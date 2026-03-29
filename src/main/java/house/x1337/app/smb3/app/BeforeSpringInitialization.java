package house.x1337.app.smb3.app;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.theme.Flatlaf;
import jakarta.annotation.PostConstruct;

@Singleton
public class BeforeSpringInitialization {
    @PostConstruct
    void run() {
        // Step 1: Initialize the FlatLaf look and feel
        Flatlaf.initialize();
    }
}
