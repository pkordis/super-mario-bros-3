package house.x1337.app.smb3.ui.editor.level.scene.tester.input.automation;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.annotation.Singleton;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

import static javax.swing.SwingUtilities.invokeLater;

/**
 * Embeddable panel version of the Input Automation tool. Allows the user to define
 * a sequence of automated key inputs fed into the game engine.
 */
@Prototype
public final class InputAutomationPanel extends JPanel {

    private final List<InputEntryPanel> entries = new ArrayList<>();
    private final JPanel entriesContainer;
    private final JCheckBox loopCheckBox;
    private final JButton runStopButton;

    private volatile boolean running = false;
    private Thread automationThread;

    /** Optional callback invoked on the EDT after automation stops, used to restore focus. */
    private Runnable onStoppedCallback;

    public InputAutomationPanel() {
        super(new BorderLayout(4, 4));
        setBorder(BorderFactory.createTitledBorder("Input Automation"));

        // --- Entries scroll area ---
        entriesContainer = new JPanel();
        entriesContainer.setLayout(new BoxLayout(entriesContainer, BoxLayout.Y_AXIS));

        final JScrollPane scrollPane = new JScrollPane(entriesContainer);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        // --- Bottom controls ---
        final JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

        final JButton addButton = new JButton("+ Add Entry");
        addButton.setFocusable(false);
        addButton.addActionListener(e -> addEntry());

        loopCheckBox = new JCheckBox("Loop", true);
        loopCheckBox.setFocusable(false);

        runStopButton = new JButton("Run");
        runStopButton.setFocusable(false);
        runStopButton.addActionListener(e -> toggleRunStop());

        bottomPanel.add(addButton);
        bottomPanel.add(loopCheckBox);
        bottomPanel.add(runStopButton);
        add(bottomPanel, BorderLayout.SOUTH);

        // Start with one entry
        addEntry();
    }

    private void addEntry() {
        final InputEntryPanel entry = new InputEntryPanel(this::removeEntry);
        entries.add(entry);
        entriesContainer.add(entry);
        entriesContainer.revalidate();
        entriesContainer.repaint();
    }

    private void removeEntry(final InputEntryPanel entry) {
        entries.remove(entry);
        entriesContainer.remove(entry);
        entriesContainer.revalidate();
        entriesContainer.repaint();
    }

    private void toggleRunStop() {
        if (running) {
            stop();
        } else {
            start();
        }
    }

    private void start() {
        if (entries.isEmpty()) {
            return;
        }
        running = true;
        runStopButton.setText("Stop");

        final List<InputEntry> snapshot = new ArrayList<>();
        for (final InputEntryPanel panel : entries) {
            snapshot.add(panel.toInputEntry());
        }
        final boolean loop = loopCheckBox.isSelected();

        automationThread = new Thread(() -> {
            try {
                do {
                    for (final InputEntry entry : snapshot) {
                        if (!running) {
                            releaseAll();
                            return;
                        }
                        applyKeys(entry, true);
                        Thread.sleep(entry.durationMs());
                        applyKeys(entry, false);
                    }
                } while (loop && running);
            } catch (final InterruptedException ignored) {
                // Thread interrupted — clean up
            } finally {
                releaseAll();
                running = false;
                invokeLater(() -> {
                    runStopButton.setText("Run");
                    if (onStoppedCallback != null) {
                        onStoppedCallback.run();
                    }
                });
            }
        }, "InputAutomation");
        automationThread.setDaemon(true);
        automationThread.start();
    }

    /** Stops the automation thread. Call when the host container is disposed. */
    public void stop() {
        running = false;
        if (automationThread != null) {
            automationThread.interrupt();
            automationThread = null;
        }
        runStopButton.setText("Run");
    }

    /**
     * Sets an optional callback invoked on the EDT after automation stops.
     * Typically used to restore keyboard focus to the game panel.
     */
    public void setOnStoppedCallback(final Runnable callback) {
        this.onStoppedCallback = callback;
    }

    private static void applyKeys(final InputEntry entry, final boolean pressed) {

    }

    private static void releaseAll() {
    }

    // --- Inner record for snapshot data ---

    private record InputEntry(boolean up, boolean down, boolean left, boolean right,
                              boolean z, boolean x, long durationMs) {
    }

    // --- Inner panel representing a single entry row ---

    private static final class InputEntryPanel extends JPanel {
        private final JCheckBox upBox = new JCheckBox("↑");
        private final JCheckBox downBox = new JCheckBox("↓");
        private final JCheckBox leftBox = new JCheckBox("←");
        private final JCheckBox rightBox = new JCheckBox("→");
        private final JCheckBox zBox = new JCheckBox("Z");
        private final JCheckBox xBox = new JCheckBox("X");
        private final JSpinner durationSpinner;

        InputEntryPanel(final java.util.function.Consumer<InputEntryPanel> onRemove) {
            setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
            setBorder(BorderFactory.createEtchedBorder());
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

            add(upBox);
            add(downBox);
            add(leftBox);
            add(rightBox);
            add(Box.createHorizontalStrut(8));
            add(zBox);
            add(xBox);
            add(Box.createHorizontalStrut(12));

            add(new JLabel("ms:"));
            durationSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 99999, 10));
            durationSpinner.setPreferredSize(new Dimension(70, 24));
            add(durationSpinner);

            add(Box.createHorizontalStrut(8));
            final JButton removeButton = new JButton("✕");
            removeButton.setMargin(new java.awt.Insets(0, 4, 0, 4));
            removeButton.addActionListener(e -> onRemove.accept(this));
            add(removeButton);
        }

        InputEntry toInputEntry() {
            return new InputEntry(
                upBox.isSelected(),
                downBox.isSelected(),
                leftBox.isSelected(),
                rightBox.isSelected(),
                zBox.isSelected(),
                xBox.isSelected(),
                ((Number) durationSpinner.getValue()).longValue()
            );
        }
    }
}

