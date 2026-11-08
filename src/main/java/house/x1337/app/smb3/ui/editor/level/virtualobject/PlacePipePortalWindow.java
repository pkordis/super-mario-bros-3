package house.x1337.app.smb3.ui.editor.level.virtualobject;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.PipePortalType;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.model.game.Coordinates;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.Optional;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.PipePortalType.IN;
import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.SOUTH;
import static java.awt.Dialog.ModalityType.APPLICATION_MODAL;
import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.NORTHWEST;
import static java.awt.GridBagConstraints.WEST;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createEtchedBorder;
import static javax.swing.BorderFactory.createTitledBorder;

/**
 * Asks for a new pipe portal's type and description before it is placed. Modal: {@link #setVisible(boolean)} returns
 * once the window is closed, and {@link #getResult()} then holds what was entered, or nothing if the author cancelled.
 */
@Prototype
public final class PlacePipePortalWindow extends JDialog {
    private final JComboBox<PipePortalType> typeCombo = new JComboBox<>(PipePortalType.values());
    private final JTextArea descriptionArea = new JTextArea(4, 28);
    private final VirtualObjectType virtualObjectType;
    private final Coordinates anchor;

    private PipePortalDetails result;

    public PlacePipePortalWindow(
        final Window owner,
        final VirtualObjectType virtualObjectType,
        final Coordinates anchor
    ) {
        super(owner, "Place Pipe Portal", APPLICATION_MODAL);
        this.virtualObjectType = virtualObjectType;
        this.anchor = anchor;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setContentPane(buildContent());
        pack();
        setLocationRelativeTo(owner);
    }

    public static PlacePipePortalWindow launch(
        final Window owner,
        final VirtualObjectType virtualObjectType,
        final Coordinates anchor
    ) {
        return getBean(PlacePipePortalWindow.class, owner, virtualObjectType, anchor);
    }

    /** @return what the author entered on Place; empty when the window was cancelled or closed */
    public Optional<PipePortalDetails> getResult() {
        return Optional.ofNullable(result);
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
            "Pipe Portal Properties",
            TitledBorder.LEADING,
            TitledBorder.TOP
        ));

        final GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.anchor = WEST;
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

        form.add(new JLabel("Object:"), labelGbc);
        final String placement = "%s  (col %d / row %d)"
            .formatted(virtualObjectType.getLabel(), anchor.x(), anchor.y());
        form.add(new JLabel(placement), fieldGbc);

        labelGbc.gridy++;
        fieldGbc.gridy++;
        form.add(new JLabel("Type:"), labelGbc);
        typeCombo.setRenderer(getBean(PipePortalTypeCellRenderer.class));
        typeCombo.setSelectedItem(IN);
        form.add(typeCombo, fieldGbc);

        labelGbc.gridy++;
        fieldGbc.gridy++;
        labelGbc.anchor = NORTHWEST;
        form.add(new JLabel("Description:"), labelGbc);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        final JScrollPane descriptionScroll = new JScrollPane(descriptionArea);
        descriptionScroll.setPreferredSize(new Dimension(260, 80));
        form.add(descriptionScroll, fieldGbc);

        return form;
    }

    private JPanel buildButtonPanel() {
        final JButton placeButton = new JButton("Place");
        final JButton cancelButton = new JButton("Cancel");

        placeButton.addActionListener(event -> place());
        cancelButton.addActionListener(event -> dispose());

        placeButton.setPreferredSize(new Dimension(90, 28));
        cancelButton.setPreferredSize(new Dimension(90, 28));
        getRootPane().setDefaultButton(placeButton);

        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        panel.add(placeButton);
        panel.add(cancelButton);
        return panel;
    }

    private void place() {
        final String description = descriptionArea.getText().trim();
        result = new PipePortalDetails(
            (PipePortalType) typeCombo.getSelectedItem(),
            description.isEmpty() ? null : description
        );
        dispose();
    }
}
