package house.x1337.app.smb3.ui.editor.level.tile.palette;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.ui.tile.Tile;
import house.x1337.app.smb3.service.TileService;
import house.x1337.app.smb3.ui.editor.level.tile.palette.core.WrapLayout;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import java.awt.Dimension;
import java.awt.Font;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static java.awt.Font.BOLD;
import static java.lang.Integer.MAX_VALUE;
import static javax.swing.BorderFactory.createCompoundBorder;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createMatteBorder;
import static javax.swing.BoxLayout.Y_AXIS;
import static javax.swing.UIManager.getColor;

@Singleton
@RequiredArgsConstructor
public class VirtualTilesSection extends JPanel {
    private final TileService tileService;

    @PostConstruct
    void init() {
        final JLabel header = new JLabel("VIRTUAL");
        header.setFont(header.getFont().deriveFont(BOLD, 11f));
        header.setForeground(getColor("Label.disabledForeground"));
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(MAX_VALUE, 26));
        header.setBorder(createCompoundBorder(
            createMatteBorder(0, 0, 1, 0, getColor("Separator.foreground")),
            createEmptyBorder(8, 2, 2, 2))
        );

        final JPanel grid = new JPanel(getBean(WrapLayout.class));
        grid.setAlignmentX(LEFT_ALIGNMENT);
        grid.setBorder(createEmptyBorder(2, 0, 8, 0));

        for (final Tile tile : tileService.getVirtualTiles()) {
            final String tooltip = tile.getDescription() != null
                ? tile.getDescription()
                : tile.getType().getLabel();
            grid.add(TileButton.fromTile(tile).withTooltip(tooltip));
        }

        setLayout(new BoxLayout(this, Y_AXIS));
        setAlignmentX(LEFT_ALIGNMENT);
        add(header);
        add(grid);
    }
}
