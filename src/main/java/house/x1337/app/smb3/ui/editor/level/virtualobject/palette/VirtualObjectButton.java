package house.x1337.app.smb3.ui.editor.level.virtualobject.palette;

import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.LevelSceneLayerType;
import house.x1337.app.smb3.enumeration.VirtualObjectType;
import house.x1337.app.smb3.ui.editor.level.palette.LayerGatedToggleButton;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;

import static house.x1337.app.smb3.GameConstants.TILE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelSceneLayerType.NON_PLAYABLE_CHARACTERS;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;

@Getter
@Prototype
@RequiredArgsConstructor
public class VirtualObjectButton extends LayerGatedToggleButton {
    public static final LevelSceneLayerType OWNING_LAYER = NON_PLAYABLE_CHARACTERS;
    static final int BORDER_THICKNESS = 4;
    private static final Color CELL_LINE_COLOR = new Color(0, 0, 0, 40);

    private VirtualObjectType virtualObjectType;

    public static VirtualObjectButton fromType(final VirtualObjectType type) {
        final VirtualObjectButton button = getBean(VirtualObjectButton.class);
        button.virtualObjectType = type;
        button.setThumbnail(new ImageIcon(toThumbnail(type)));
        button.setMargin(new Insets(2, 2, 2, 2));
        button.setBaseTooltip("%s — %d x %d tiles, placed by its lower-left tile".formatted(
            type.getLabel(),
            type.getColumns(),
            type.getRows()
        ));
        return button;
    }

    @Override
    public LevelSceneLayerType getOwningLayer() {
        return OWNING_LAYER;
    }

    static BufferedImage toThumbnail(final VirtualObjectType type) {
        final int width = type.getColumns() * TILE_SIZE;
        final int height = type.getRows() * TILE_SIZE;
        final BufferedImage image = new BufferedImage(width, height, TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        graphics.setColor(CELL_LINE_COLOR);
        for (int column = 1; column < type.getColumns(); column++) {
            graphics.drawLine(column * TILE_SIZE, 0, column * TILE_SIZE, height - 1);
        }
        for (int row = 1; row < type.getRows(); row++) {
            graphics.drawLine(0, row * TILE_SIZE, width - 1, row * TILE_SIZE);
        }
        graphics.setColor(type.getColour());
        graphics.fillRect(0, 0, width, BORDER_THICKNESS);
        graphics.fillRect(0, height - BORDER_THICKNESS, width, BORDER_THICKNESS);
        graphics.fillRect(0, 0, BORDER_THICKNESS, height);
        graphics.fillRect(width - BORDER_THICKNESS, 0, BORDER_THICKNESS, height);
        graphics.dispose();
        return image;
    }
}
