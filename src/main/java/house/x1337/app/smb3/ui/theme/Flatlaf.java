package house.x1337.app.smb3.ui.theme;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Flatlaf {
    public static void initialize() {
        if (isOsDarkMode()) {
            FlatDarculaLaf.setup();
        } else {
            FlatIntelliJLaf.setup();
        }
        log.info("Flatlaf initialized");
    }

    private static boolean isOsDarkMode() {
        final String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                final String output;
                try (final Process process = new ProcessBuilder(
                    "reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme"
                ).start()) {
                    output = new String(process.getInputStream().readAllBytes());
                }
                return output.contains("0x0");
            } else if (os.contains("mac")) {
                final String output;
                try (Process process = new ProcessBuilder(
                    "defaults", "read", "-g", "AppleInterfaceStyle"
                ).start()) {
                    output = new String(process.getInputStream().readAllBytes()).trim();
                }
                return "dark".equalsIgnoreCase(output);
            } else {
                final String output;
                try (Process process = new ProcessBuilder(
                    "gsettings", "get", "org.gnome.desktop.interface", "color-scheme"
                ).start()) {
                    output = new String(process.getInputStream().readAllBytes()).trim();
                }
                return output.contains("dark");
            }
        } catch (final Exception ignored) {
            return false;
        }
    }
}
