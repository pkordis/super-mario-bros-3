package house.x1337.app.smb3.ui.editor.menu.file;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.service.GameSettingsService;
import house.x1337.app.smb3.ui.editor.menu.GameEditorWindowMenuItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;

@Singleton
@RequiredArgsConstructor
public class SettingsMenuItem extends GameEditorWindowMenuItem {
    private final GameSettingsService gameSettingsService;

    @PostConstruct
    void init() {
        setText("Settings...");
        addActionListener(e -> openSettings());
    }

    private void openSettings() {
        final SettingsWindow settingsWindow = getBean(
            SettingsWindow.class,
            getParentFrame(),
            gameSettingsService
        );
        settingsWindow.setVisible(true);
    }
}
