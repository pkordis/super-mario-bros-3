package house.x1337.app.smb3.app;

import house.x1337.app.smb3.annotation.Singleton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

@Singleton
@RequiredArgsConstructor
public class ApplicationTerminator {
    @Getter
    private final WindowListener windowListener = new WindowAdapter() {
        @Override
        public void windowClosing(final WindowEvent e) {
            terminate();
        }
    };

    public void terminate() {
        System.exit(0);
    }
}
