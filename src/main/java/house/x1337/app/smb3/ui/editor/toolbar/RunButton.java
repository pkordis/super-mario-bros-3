package house.x1337.app.smb3.ui.editor.toolbar;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.ui.editor.tab.EditorTabSystem;
import house.x1337.app.smb3.ui.editor.tab.core.BaseEditorTab;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.DefaultResourceLoader;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static java.awt.event.KeyEvent.VK_F9;
import static javax.swing.KeyStroke.getKeyStroke;

/**
 * Runs the active tab through the {@link TestRunTarget} that handles it, such as Run Level for a level scene, and
 * is disabled while no target does. {@link #SHORTCUT} triggers it from anywhere in the editor window.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor
public class RunButton extends JButton {
    public static final KeyStroke SHORTCUT = getKeyStroke(VK_F9, 0);
    static final String ICON_RESOURCE = "classpath:ui/toolbar/run.png";
    static final String IDLE_LABEL = "Run";
    static final String SHORTCUT_ACTION = "run-active-tab";

    private final EditorTabSystem editorTabSystem;
    private final List<TestRunTarget> targets;

    @PostConstruct
    void init() {
        loadIcon().ifPresent(this::setIcon);
        setFocusable(false);
        addActionListener(event -> runActiveTab());
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(SHORTCUT, SHORTCUT_ACTION);
        getActionMap().put(SHORTCUT_ACTION, new AbstractAction() {
            @Override
            public void actionPerformed(final ActionEvent event) {
                runActiveTab();
            }
        });
        editorTabSystem.addChangeListener(event -> refresh());
        refresh();
    }

    void refresh() {
        final Optional<TestRunTarget> target = activeTarget();
        setText(target.map(TestRunTarget::getLabel).orElse(IDLE_LABEL));
        setToolTipText(target.map(found -> found.getLabel() + " (F9)").orElse("Nothing to run in the active tab"));
        setEnabled(target.isPresent());
    }

    void runActiveTab() {
        final Optional<BaseEditorTab> activeTab = editorTabSystem.getActiveTab();
        activeTab.ifPresent(tab -> targetFor(tab).ifPresent(target -> target.run(tab)));
    }

    private Optional<TestRunTarget> activeTarget() {
        return editorTabSystem.getActiveTab().flatMap(this::targetFor);
    }

    private Optional<TestRunTarget> targetFor(final BaseEditorTab tab) {
        return targets.stream().filter(target -> target.handles(tab)).findFirst();
    }

    private Optional<ImageIcon> loadIcon() {
        try (final InputStream input = new DefaultResourceLoader().getResource(ICON_RESOURCE).getInputStream()) {
            final BufferedImage image = ImageIO.read(input);
            return Optional.ofNullable(image).map(ImageIcon::new);
        } catch (final IOException e) {
            log.error("Failed to load the Run button icon: {}", ICON_RESOURCE, e);
            return Optional.empty();
        }
    }
}
