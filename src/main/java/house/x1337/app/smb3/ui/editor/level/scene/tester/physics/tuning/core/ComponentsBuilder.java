package house.x1337.app.smb3.ui.editor.level.scene.tester.physics.tuning.core;

import house.x1337.app.smb3.game.Physics;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.ChangeListener;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import static java.awt.Component.LEFT_ALIGNMENT;
import static java.awt.Font.SANS_SERIF;
import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.LINE_START;
import static java.awt.GridBagConstraints.NONE;
import static java.lang.Math.clamp;
import static javax.swing.BorderFactory.createEmptyBorder;

public interface ComponentsBuilder extends RefreshCallbacksAware {
    Font SECTION_FONT = new Font(SANS_SERIF, Font.BOLD, 13);
    Font LABEL_FONT = new Font(SANS_SERIF, Font.PLAIN, 12);
    int SLIDER_WIDTH = 160;

    Physics getPhysics();
    void onValueChanged();

    default JPanel buildRow(
        final String label,
        final int min,
        final int max,
        final int step,
        final Physics.Parameter physicsParameter
    ) {
        final JPanel row = new JPanel(new GridBagLayout());
        row.setAlignmentX(LEFT_ALIGNMENT);
        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.gridy = 0;

        // Label
        final JLabel jLabel = new JLabel(label);
        jLabel.setFont(LABEL_FONT);
        gbc.gridx = 0;
        gbc.anchor = LINE_START;
        gbc.weightx = 0;
        row.add(jLabel, gbc);

        // Slider
        final int initialValue = Math.round(getPhysics().get(physicsParameter));
        final JSlider slider = new JSlider(SwingConstants.HORIZONTAL, min, max, clamp(initialValue, min, max));
        slider.setPreferredSize(new Dimension(SLIDER_WIDTH, slider.getPreferredSize().height));
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = HORIZONTAL;
        row.add(slider, gbc);

        // Spinner
        final SpinnerNumberModel model = new SpinnerNumberModel(clamp(initialValue, min, max), min, max, (double) step);
        final JSpinner spinner = new JSpinner(model);
        spinner.setPreferredSize(new Dimension(80, spinner.getPreferredSize().height));
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = NONE;
        row.add(spinner, gbc);

        // Bidirectional binding — guard against recursive events
        final boolean[] updating = {false};

        final ChangeListener sliderListener = e -> {
            if (updating[0]) {
                return;
            }
            updating[0] = true;
            final float val = slider.getValue();
            getPhysics().set(physicsParameter, val);
            model.setValue((double) slider.getValue());
            updating[0] = false;
            onValueChanged();
        };
        slider.addChangeListener(sliderListener);

        final ChangeListener spinnerListener = e -> {
            if (updating[0]) {
                return;
            }
            updating[0] = true;
            final float val = ((Number) model.getValue()).floatValue();
            getPhysics().set(physicsParameter, val);
            slider.setValue(Math.round(val));
            updating[0] = false;
            onValueChanged();
        };
        spinner.addChangeListener(spinnerListener);

        // Register refresh callback so reset / sync can push values back.
        getRefreshCallbacks().add(() -> {
            updating[0] = true;
            final int v = Math.round(getPhysics().get(physicsParameter));
            slider.setValue(clamp(v, min, max));
            model.setValue((double) clamp(v, min, max));
            updating[0] = false;
        });

        return row;
    }

    default JLabel buildSectionLabel(final String text) {
        final JLabel label = new JLabel(text);
        label.setFont(SECTION_FONT);

        final Color accent = UIManager.getColor("Component.accentColor");
        label.setForeground(accent != null ? accent : UIManager.getColor("Label.foreground"));
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setBorder(createEmptyBorder(4, 0, 4, 0));
        return label;
    }
}
