package house.x1337.app.smb3.ui.editor.level.tile.edit;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.ui.editor.GameEditorWindow;
import jakarta.annotation.PostConstruct;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.SOUTH;
import static javax.swing.BorderFactory.createEmptyBorder;

/** The tile form for one tile. Set or Save saves it and closes the window; Close leaves it as it was. */
@Prototype
public final class EditTileWindow extends JDialog {
    private final TilePropertiesForm form = getBean(TilePropertiesForm.class);
    private final Tile tile;

    public EditTileWindow(final GameEditorWindow parent, final Tile tile) {
        super(parent);
        this.tile = tile;
    }

    @PostConstruct
    void init() {
        final String type = tile.getType() == null ? "no type" : tile.getType().getLabel();
        setTitle("Edit Tile - " + type + " #" + tile.getId());
        setModal(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        final JButton closeButton = new JButton("Close");
        closeButton.addActionListener(event -> dispose());
        final JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(closeButton);

        final JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(createEmptyBorder(10, 10, 10, 10));
        root.add(form, CENTER);
        root.add(footer, SOUTH);
        setContentPane(root);

        form.load(tile);
        form.onSet(() -> {
            if (form.save(this, tile)) {
                dispose();
            }
        });
        pack();
        setLocationRelativeTo(getParent());
    }
}
