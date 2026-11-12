package house.x1337.app.smb3.ui.editor.level.menu.file;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.model.settings.GameSettings;
import house.x1337.app.smb3.service.GameSettingsService;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import static house.x1337.app.smb3.enumeration.TimerPace.STANDARD;
import static house.x1337.app.smb3.enumeration.TimerPace.REAL_TIME;
import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.SOUTH;
import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.WEST;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createEtchedBorder;
import static javax.swing.BorderFactory.createTitledBorder;

@Prototype
public final class SettingsWindow extends JDialog {
    private final GameSettingsService gameSettingsService;
    private final GameSettings settings;

    private final JCheckBox standardTimerCheckBox = new JCheckBox(
        "Standard level timer: one step every 41 frames instead of every second"
    );

    public SettingsWindow(
        final Frame owner,
        final GameSettingsService gameSettingsService
    ) {
        super(owner, "Settings", true);
        this.gameSettingsService = gameSettingsService;
        this.settings = gameSettingsService.getSettings();
        standardTimerCheckBox.setSelected(settings.getTimerPace() == STANDARD);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setContentPane(buildContent());
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildContent() {
        final JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(createEmptyBorder(12, 12, 12, 12));
        root.add(buildFormPanel(), CENTER);
        root.add(buildButtonPanel(), SOUTH);
        return root;
    }

    private JPanel buildFormPanel() {
        final JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(createTitledBorder(
            createEtchedBorder(),
            "Game",
            TitledBorder.LEADING,
            TitledBorder.TOP
        ));

        final GridBagConstraints rowGbc = new GridBagConstraints();
        rowGbc.anchor = WEST;
        rowGbc.fill = HORIZONTAL;
        rowGbc.weightx = 1.0;
        rowGbc.insets = new Insets(6, 8, 5, 8);
        rowGbc.gridx = 0;
        rowGbc.gridy = 0;

        form.add(standardTimerCheckBox, rowGbc);
        return form;
    }

    private JPanel buildButtonPanel() {
        final JButton saveButton = new JButton("Save");
        final JButton cancelButton = new JButton("Cancel");

        saveButton.addActionListener(e -> save());
        cancelButton.addActionListener(e -> dispose());

        saveButton.setPreferredSize(new Dimension(90, 28));
        cancelButton.setPreferredSize(new Dimension(90, 28));

        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        panel.add(saveButton);
        panel.add(cancelButton);
        return panel;
    }

    private void save() {
        settings.setTimerPace(standardTimerCheckBox.isSelected() ? STANDARD : REAL_TIME);
        gameSettingsService.save(settings);
        dispose();
    }
}
