package house.x1337.app.smb3.ui.editor.level.scene.menu.file;

import house.x1337.app.smb3.annotation.Singleton;
import jakarta.annotation.PostConstruct;

import javax.swing.JMenuItem;

import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.KeyEvent.VK_N;
import static javax.swing.KeyStroke.getKeyStroke;

@Singleton
public class NewLevelSceneMenuItem extends JMenuItem {
    @PostConstruct
    void init() {
        setText("New Level Scene");
        setAccelerator(getKeyStroke(VK_N, CTRL_DOWN_MASK));
        addActionListener(e -> promptNewScene());
    }

    private void promptNewScene() {
        System.out.println();
    }
}
