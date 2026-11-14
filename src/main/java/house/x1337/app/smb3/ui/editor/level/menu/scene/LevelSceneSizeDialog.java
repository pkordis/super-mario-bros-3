package house.x1337.app.smb3.ui.editor.level.menu.scene;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.ui.editor.GameEditorWindow;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.ParseException;
import java.util.Optional;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.SOUTH;
import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.WEST;
import static javax.swing.BorderFactory.createEmptyBorder;

/**
 * Asks for a level scene's size in tiles, refusing what {@link LevelSceneSizeRules} does not allow with the reason
 * shown in the dialog.
 */
@Prototype
public final class LevelSceneSizeDialog extends JDialog {
    private final JLabel errorLabel = new JLabel(" ");
    private LevelSceneSizeRules sizeRules;
    private JSpinner columnsSpinner;
    private JSpinner rowsSpinner;
    private LevelSceneDimensions chosenSize;

    public LevelSceneSizeDialog(final Frame owner) {
        super(owner, true);
    }

    public LevelSceneSizeDialog configure(
        final LevelSceneDimensions dimensions,
        final LevelSceneSizeRules sizeRules
    ) {
        this.sizeRules = sizeRules;
        this.columnsSpinner = buildSpinner(dimensions.columns());
        this.rowsSpinner = buildSpinner(dimensions.rows());

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setContentPane(buildContent());
        pack();
        setLocationRelativeTo(getParent());

        return this;
    }

    /**
     * Shows the dialog and waits for it to close.
     *
     * @return the size confirmed with OK, or nothing when the dialog was cancelled
     */
    public Optional<LevelSceneDimensions> ask() {
        setVisible(true);
        return Optional.ofNullable(chosenSize);
    }

    private JSpinner buildSpinner(final int value) {
        final JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, 1, Integer.MAX_VALUE, 1));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "0"));
        return spinner;
    }

    private JPanel buildContent() {
        final JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(createEmptyBorder(12, 12, 12, 12));
        root.add(buildForm(), CENTER);
        root.add(buildButtons(), SOUTH);
        return root;
    }

    private JPanel buildForm() {
        final JPanel form = new JPanel(new GridBagLayout());
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = WEST;
        constraints.insets = new Insets(4, 4, 4, 4);

        constraints.gridy = 0;
        constraints.gridx = 0;
        form.add(new JLabel("Columns:"), constraints);
        constraints.gridx = 1;
        constraints.fill = HORIZONTAL;
        form.add(columnsSpinner, constraints);

        constraints.gridy = 1;
        constraints.gridx = 0;
        constraints.fill = GridBagConstraints.NONE;
        form.add(new JLabel("Rows:"), constraints);
        constraints.gridx = 1;
        constraints.fill = HORIZONTAL;
        form.add(rowsSpinner, constraints);

        constraints.gridy = 2;
        constraints.gridx = 0;
        constraints.gridwidth = 2;
        form.add(new JLabel(sizeRules.describe()), constraints);

        constraints.gridy = 3;
        errorLabel.setForeground(new Color(200, 40, 40));
        form.add(errorLabel, constraints);
        return form;
    }

    private JPanel buildButtons() {
        final JButton okButton = new JButton("OK");
        final JButton cancelButton = new JButton("Cancel");
        okButton.addActionListener(e -> confirm());
        cancelButton.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(okButton);

        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        panel.add(okButton);
        panel.add(cancelButton);
        return panel;
    }

    private void confirm() {
        try {
            columnsSpinner.commitEdit();
            rowsSpinner.commitEdit();
        } catch (final ParseException e) {
            errorLabel.setText("Enter whole numbers of tiles.");
            pack();
            return;
        }
        final int columns = (Integer) columnsSpinner.getValue();
        final int rows = (Integer) rowsSpinner.getValue();
        final Optional<String> refusal = sizeRules.validate(columns, rows);
        if (refusal.isPresent()) {
            errorLabel.setText(refusal.get());
            pack();
            return;
        }
        chosenSize = new LevelSceneDimensions(columns, rows);
        dispose();
    }

    public static LevelSceneSizeDialog fromContext(
        final GameEditorWindow parent,
        final String title
    ) {
        final LevelSceneSizeDialog sceneSizeDialog = getBean(
            LevelSceneSizeDialog.class,
            parent
        );
        sceneSizeDialog.setTitle(title);
        return sceneSizeDialog;
    }
}
