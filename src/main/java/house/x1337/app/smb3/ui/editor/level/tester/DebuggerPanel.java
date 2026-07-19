package house.x1337.app.smb3.ui.editor.level.tester;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.ui.editor.level.tester.input.automation.InputAutomationPanel;
import house.x1337.app.smb3.ui.editor.level.tester.physics.tuning.PhysicsTunerPanel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import java.awt.Dimension;

import static javax.swing.BoxLayout.Y_AXIS;

@Prototype
@RequiredArgsConstructor
public final class DebuggerPanel extends JPanel {
    private static final int PREFERRED_WIDTH = 520;
    private static final int MIN_WIDTH = 450;

    private final PhysicsTunerPanel physicsTunerPanel;
    private final InputAutomationPanel inputAutomationPanel;

    @PostConstruct
    void init() {
        setLayout(new BoxLayout(this, Y_AXIS));
        setPreferredSize(new Dimension(PREFERRED_WIDTH, 0));
        setMinimumSize(new Dimension(MIN_WIDTH, 0));

        add(physicsTunerPanel);
        add(inputAutomationPanel);
    }

    public void dispose() {
        physicsTunerPanel.stopTimer();
        inputAutomationPanel.stop();
    }
}

