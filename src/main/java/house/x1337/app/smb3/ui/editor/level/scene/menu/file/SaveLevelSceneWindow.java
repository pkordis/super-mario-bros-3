package house.x1337.app.smb3.ui.editor.level.scene.menu.file;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.LevelScene;
import house.x1337.app.smb3.service.LevelSceneService;
import house.x1337.app.smb3.ui.editor.level.scene.LevelSceneEditorWindow;
import house.x1337.app.smb3.ui.editor.level.scene.tab.LevelSceneEditorTab;
import house.x1337.app.smb3.util.factory.LevelSceneEditorTabFactory;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.SOUTH;
import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.NORTHWEST;
import static java.awt.GridBagConstraints.WEST;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createEtchedBorder;
import static javax.swing.BorderFactory.createTitledBorder;
import static javax.swing.JOptionPane.WARNING_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;

@Prototype
public final class SaveLevelSceneWindow extends JDialog {
    private final LevelSceneService levelSceneService;
    private final LevelSceneEditorTab tab;

    private final JTextField titleField = new JTextField(28);
    private final JTextArea descriptionArea = new JTextArea(4, 28);

    public SaveLevelSceneWindow(
        final Frame owner,
        final LevelSceneService levelSceneService,
        final LevelSceneEditorTab tab
    ) {
        super(owner, "Save Scene", true);
        this.levelSceneService = levelSceneService;
        this.tab = tab;

        if (tab.getSceneTitle() != null) {
            titleField.setText(tab.getSceneTitle());
        }
        if (tab.getSceneDescription() != null) {
            descriptionArea.setText(tab.getSceneDescription());
        }

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setContentPane(buildContent());
        pack();
        setLocationRelativeTo(owner);
    }

    public static SaveLevelSceneWindow launch(
        final LevelSceneEditorWindow owner,
        final LevelSceneEditorTab activeTab
    ) {
        return getBean(
            SaveLevelSceneWindow.class,
            owner,
            getBean(LevelSceneService.class),
            activeTab
        );
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
            "Scene Properties",
            TitledBorder.LEADING,
            TitledBorder.TOP
        ));

        final GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.anchor = NORTHWEST;
        labelGbc.insets = new Insets(6, 8, 5, 6);
        labelGbc.gridx = 0;
        labelGbc.gridy = 0;

        final GridBagConstraints fieldGbc = new GridBagConstraints();
        fieldGbc.anchor = WEST;
        fieldGbc.fill = HORIZONTAL;
        fieldGbc.weightx = 1.0;
        fieldGbc.insets = new Insets(6, 0, 5, 8);
        fieldGbc.gridx = 1;
        fieldGbc.gridy = 0;

        // ID row (read-only)
        form.add(new JLabel("ID:"), labelGbc);
        final String sceneIdText = tab.getSceneId();
        final JLabel idLabel = new JLabel(abbreviate(sceneIdText, 28));
        idLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        idLabel.setToolTipText(sceneIdText);
        form.add(idLabel, fieldGbc);

        // Title row (required)
        labelGbc.gridy++;
        fieldGbc.gridy++;
        labelGbc.anchor = WEST;
        form.add(new JLabel("<html>Title: <font color='red'>*</font></html>"), labelGbc);
        form.add(titleField, fieldGbc);

        // ── Description row (optional, multi-line) ────────────────────────────
        labelGbc.gridy++;
        fieldGbc.gridy++;
        labelGbc.anchor = NORTHWEST;
        form.add(new JLabel("Description:"), labelGbc);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        final JScrollPane descScroll = new JScrollPane(descriptionArea);
        descScroll.setPreferredSize(new Dimension(260, 80));
        form.add(descScroll, fieldGbc);

        // ── Required-field legend ─────────────────────────────────────────────
        labelGbc.gridy++;
        labelGbc.anchor = WEST;
        labelGbc.gridwidth = 2;
        labelGbc.insets = new Insets(2, 8, 4, 8);
        final JLabel legend = new JLabel("<html><font color='red'>*</font> required field</html>");
        legend.setFont(legend.getFont().deriveFont(Font.ITALIC, 11f));
        form.add(legend, labelGbc);

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

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private void save() {
        final String title = titleField.getText().trim();
        if (title.isEmpty()) {
            showMessageDialog(
                this,
                "Please enter a title for the scene.",
                "Title Required",
                WARNING_MESSAGE
            );
            titleField.requestFocusInWindow();
            return;
        }

        final String description = descriptionArea.getText().trim();

        // Apply the entered metadata to the tab before building the LevelScene
        tab.setSceneTitle(title);
        tab.setSceneDescription(description.isEmpty() ? null : description);

        final LevelScene levelScene = LevelSceneEditorTabFactory.fromLevelSceneEditor(tab);
        levelScene.setTitle(title);
        levelScene.setDescription(description.isEmpty() ? null : description);

        levelSceneService.save(levelScene);

        // After save, update the tab's persisted ID and refresh the tab title
        tab.setSceneId(levelScene.getId());
        tab.updateTabTitle();

        dispose();
    }

    /**
     * Shortens {@code value} to at most {@code maxLength} characters by keeping
     * a prefix and suffix joined with an ellipsis.  Returns the original string
     * unchanged when it already fits.
     */
    private static String abbreviate(final String value, final int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        final int half = maxLength / 2;
        return value.substring(0, half) + "…" + value.substring(value.length() - half);
    }
}
