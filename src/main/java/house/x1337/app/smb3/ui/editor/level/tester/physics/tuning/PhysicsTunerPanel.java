package house.x1337.app.smb3.ui.editor.level.tester.physics.tuning;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.Physics;
import house.x1337.app.smb3.service.ConfigurationService;
import house.x1337.app.smb3.ui.editor.level.tester.physics.tuning.core.ComponentsBuilder;
import lombok.Getter;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

import static house.x1337.app.smb3.game.Physics.Parameter.FRICTION;
import static house.x1337.app.smb3.game.Physics.Parameter.GRAVITY;
import static house.x1337.app.smb3.game.Physics.Parameter.JUMP_SUSTAIN_TIME;
import static house.x1337.app.smb3.game.Physics.Parameter.JUMP_VELOCITY;
import static house.x1337.app.smb3.game.Physics.Parameter.RUN_ACCELERATION;
import static house.x1337.app.smb3.game.Physics.Parameter.RUN_AIR_RESISTANCE;
import static house.x1337.app.smb3.game.Physics.Parameter.RUN_MAX_SPEED;
import static house.x1337.app.smb3.game.Physics.Parameter.WALK_ACCELERATION;
import static house.x1337.app.smb3.game.Physics.Parameter.WALK_AIR_RESISTANCE;
import static house.x1337.app.smb3.game.Physics.Parameter.WALK_MAX_SPEED;
import static java.awt.BorderLayout.CENTER;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.Box.createVerticalStrut;

/**
 * Embeddable panel version of the Physics Tuner. Exposes every {@link Physics} field as a
 * labeled slider + spinner pair, organized by section.
 */
@Getter
@Prototype
public final class PhysicsTunerPanel extends JPanel implements ComponentsBuilder {
    private final List<Runnable> refreshCallbacks = new ArrayList<>();
    private final ConfigurationService configurationService;
    private JButton saveButton;
    private boolean dirty = false;
    private final Timer syncTimer;

    public static final int MAX_HEIGHT = 500;

    public PhysicsTunerPanel(final ConfigurationService configurationService) {
        super(new BorderLayout());
        this.configurationService = configurationService;

        setBorder(createTitledBorder("Physics Tuner"));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, MAX_HEIGHT));
        setPreferredSize(new Dimension(getPreferredSize().width, MAX_HEIGHT));

        final JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(createEmptyBorder(8, 8, 8, 8));

        buildGeneralSection(content);
        buildPlayerSection(content);
        buildButtonSection(content);

        final JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(null);
        add(scrollPane, CENTER);

        // Periodic sync: if settings are reset programmatically, update widgets.
        syncTimer = new Timer(500, e -> refreshAll());
        syncTimer.setRepeats(true);
        syncTimer.start();
    }

    /**
     * Marks the panel as dirty (controls have been touched) and enables the Save button.
     */
    public void markDirty() {
        dirty = true;
        if (saveButton != null) {
            saveButton.setEnabled(true);
        }
    }

    /**
     * Marks the panel as clean and disables the Save button.
     */
    private void markClean() {
        dirty = false;
        if (saveButton != null) {
            saveButton.setEnabled(false);
        }
    }

    /** Stops the internal sync timer. Call when the host container is disposed. */
    public void stopTimer() {
        syncTimer.stop();
    }

    private void buildButtonSection(final JPanel content) {
        final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonPanel.setAlignmentX(LEFT_ALIGNMENT);

        final JButton reloadButton = new JButton("Reload");
        reloadButton.setFocusable(false);
        reloadButton.addActionListener(e -> {
            configurationService.loadPhysics();
            refreshAll();
            markClean();
        });

        saveButton = new JButton("Save");
        saveButton.setFocusable(false);
        saveButton.setEnabled(false);
        saveButton.addActionListener(e -> {
            configurationService.savePhysics();
            markClean();
        });

        buttonPanel.add(reloadButton);
        buttonPanel.add(saveButton);
        content.add(buttonPanel);
    }

    private void buildPlayerSection(final JPanel content) {
        content.add(buildSectionLabel("Player - Horizontal Movement"));
        content.add(buildRow("Walk Acceleration (px/s²)", 0, 2500, 10, WALK_ACCELERATION));
        content.add(buildRow("Run Acceleration (px/s²)", 0, 1500, 10, RUN_ACCELERATION));
        content.add(buildRow("Walk Max Speed (px/s)", 0, 1200, 5, WALK_MAX_SPEED));
        content.add(buildRow("Run Max Speed (px/s)", 0, 2000, 5, RUN_MAX_SPEED));
        content.add(buildRow("Friction (px/s²)", 0, 1500, 10, FRICTION));
        content.add(buildRow("Air Resistance - Walk (px/s²)", 0, 800, 5, WALK_AIR_RESISTANCE));
        content.add(buildRow("Air Resistance - Run (px/s²)", 0, 800, 5, RUN_AIR_RESISTANCE));

        content.add(createVerticalStrut(12));

        content.add(buildSectionLabel("Player - Jump"));
        content.add(buildRow("Jump Velocity (px/s)", -1500, 0, 5, JUMP_VELOCITY));
        content.add(buildRow("Jump Sustain Time (ms)", 0, 1000, 10, JUMP_SUSTAIN_TIME));

        content.add(createVerticalStrut(16));
    }

    private void buildGeneralSection(final JPanel content) {
        content.add(buildSectionLabel("General"));
        content.add(buildRow("Gravity (px/s²)", 0, 7000, 10, GRAVITY));
        content.add(createVerticalStrut(12));
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(super.getMaximumSize().width, MAX_HEIGHT);
    }

    @Override
    public Physics getPhysics() {
        return Physics.get();
    }

    @Override
    public void onValueChanged() {
        markDirty();
    }
}

