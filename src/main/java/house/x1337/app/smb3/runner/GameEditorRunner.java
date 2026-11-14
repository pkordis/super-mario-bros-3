package house.x1337.app.smb3.runner;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.service.ConfigurationService;
import house.x1337.app.smb3.service.GameSettingsService;
import house.x1337.app.smb3.ui.editor.GameEditorWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.concurrent.atomic.AtomicBoolean;

import static java.awt.GraphicsEnvironment.isHeadless;
import static javax.swing.SwingUtilities.invokeLater;

@Singleton
@RequiredArgsConstructor
public class GameEditorRunner implements ApplicationRunner {
    private final AtomicBoolean launched = new AtomicBoolean(false);
    private final GameEditorWindow gameEditorWindow;
    private final ConfigurationService configurationService;
    private final GameSettingsService gameSettingsService;

    @Override
    public void run(final ApplicationArguments args) {
        if (isHeadless()) {
            return;
        }

        configurationService.loadPhysics();
        gameSettingsService.load();

        invokeLater(() -> {
            if (launched.compareAndSet(false, true)) {
                gameEditorWindow.setVisible(true);
            }
        });
    }
}
