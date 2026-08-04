package house.x1337.app.smb3.display;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;

import static java.awt.DisplayMode.REFRESH_RATE_UNKNOWN;
import static java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment;
import static lombok.AccessLevel.PRIVATE;

@Slf4j
@NoArgsConstructor(access = PRIVATE)
public final class MonitorRefreshRate {
    public static final int FALLBACK_HERTZ = 60;
    private static final int MINIMUM_PLAUSIBLE_HERTZ = 24;

    public static int ofDefaultScreen() {
        if (GraphicsEnvironment.isHeadless()) {
            log.warn("Headless environment: using fallback refresh rate of {} Hz", FALLBACK_HERTZ);
            return FALLBACK_HERTZ;
        }

        try {
            return of(getLocalGraphicsEnvironment().getDefaultScreenDevice());
        } catch (final RuntimeException e) {
            log.warn(
                "Could not query the default screen device; using fallback refresh rate of {} Hz",
                FALLBACK_HERTZ,
                e
            );
            return FALLBACK_HERTZ;
        }
    }

    public static int of(final GraphicsDevice device) {
        final DisplayMode displayMode = device == null ? null : device.getDisplayMode();
        final int hertz = displayMode == null ? REFRESH_RATE_UNKNOWN : displayMode.getRefreshRate();

        if (hertz < MINIMUM_PLAUSIBLE_HERTZ) {
            log.warn(
                "Monitor {} reports refresh rate {}; using fallback of {} Hz",
                device == null ? "<unknown>" : device.getIDstring(),
                hertz,
                FALLBACK_HERTZ
            );
            return FALLBACK_HERTZ;
        }

        log.info("Monitor {} refresh rate: {} Hz", device.getIDstring(), hertz);
        return hertz;
    }
}
