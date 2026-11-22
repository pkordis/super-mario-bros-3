package house.x1337.app.smb3.ui.editor.level.enemy.edit;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.ui.editor.GameEditorWindow;
import house.x1337.app.smb3.ui.editor.level.enemy.create.EnemyTilesGridPanel;
import house.x1337.app.smb3.ui.editor.level.enemy.create.EnemyTilesGridPanel.Tool;
import jakarta.annotation.PostConstruct;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.NORTH;
import static java.awt.BorderLayout.SOUTH;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createTitledBorder;

/**
 * An enemy's parts, to pick a new rendering starter or clear pixels to transparent. Save stores the changes and closes
 * the window; Cancel leaves the enemy as it was.
 */
@Prototype
public final class EditEnemyWindow extends JDialog {
    private final EnemyStampEditing enemyStampEditing = getBean(EnemyStampEditing.class);
    private final EnemyTilesGridPanel gridPanel = getBean(EnemyTilesGridPanel.class);
    private final JLabel hint = new JLabel();
    private final EnemyStamp enemy;
    private int[][][] pixels;

    public EditEnemyWindow(
        final GameEditorWindow parent,
        final EnemyStamp enemy
    ) {
        super(parent);
        this.enemy = enemy;
    }

    @PostConstruct
    void init() {
        setTitle("Edit Enemy - " + enemy.getName());
        setModal(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        pixels = enemyStampEditing.editablePixelsOf(enemy);
        gridPanel.render(pixels, enemy.getRenderingStarter());

        final JPanel tiles = new JPanel(new BorderLayout());
        tiles.setBorder(createTitledBorder("Tiles"));
        final JPanel centered = new JPanel(new FlowLayout(FlowLayout.CENTER));
        centered.add(gridPanel);
        tiles.add(new JScrollPane(centered), CENTER);

        final JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(createEmptyBorder(10, 10, 10, 10));
        root.add(buildToolChoice(), NORTH);
        root.add(tiles, CENTER);
        root.add(buildFooter(), SOUTH);
        setContentPane(root);
        pack();
        setLocationRelativeTo(getParent());
    }

    private JPanel buildToolChoice() {
        final JRadioButton renderingStarter = new JRadioButton("Rendering starter", true);
        final JRadioButton pixelRemover = new JRadioButton("Pixel remover");
        final ButtonGroup group = new ButtonGroup();
        group.add(renderingStarter);
        group.add(pixelRemover);
        renderingStarter.addActionListener(event -> use(Tool.RENDERING_STARTER));
        pixelRemover.addActionListener(event -> use(Tool.PIXEL_REMOVER));
        use(Tool.RENDERING_STARTER);

        final JPanel choice = new JPanel(new FlowLayout(FlowLayout.LEFT));
        choice.add(renderingStarter);
        choice.add(pixelRemover);
        choice.add(hint);
        return choice;
    }

    private void use(final Tool tool) {
        gridPanel.setTool(tool);
        hint.setText(tool == Tool.RENDERING_STARTER
            ? "Click a tile to anchor the enemy by it."
            : "Click or drag over pixels to make them transparent.");
    }

    private JPanel buildFooter() {
        final JButton save = new JButton("Save");
        save.addActionListener(event -> {
            if (enemyStampEditing.save(this, enemy, gridPanel.getRenderingStarter(), pixels)) {
                dispose();
            }
        });
        final JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        final JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(save);
        footer.add(cancel);
        return footer;
    }
}
