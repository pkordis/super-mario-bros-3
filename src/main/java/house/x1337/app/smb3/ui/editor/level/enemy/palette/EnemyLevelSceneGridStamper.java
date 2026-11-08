package house.x1337.app.smb3.ui.editor.level.enemy.palette;

import house.x1337.app.smb3.annotation.Singleton;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.enemy.EnemyStamp;
import house.x1337.app.smb3.model.ui.tile.Tile;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.GameConstants.NULL_TILE;
import static house.x1337.app.smb3.model.game.Coordinates.of;

@Singleton
@RequiredArgsConstructor
public class EnemyLevelSceneGridStamper {
    public int stamp(
        final Tile[][] layerTiles,
        final EnemyStamp enemy,
        final Coordinates cell
    ) {
        if (!enemy.isWellFormed()) {
            return 0;
        }
        final int rows = layerTiles.length;
        final int columns = rows == 0 ? 0 : layerTiles[0].length;
        final int originRow = cell.y() - enemy.getRenderingStarter().y();
        final int originColumn = cell.x() - enemy.getRenderingStarter().x();

        int written = 0;
        for (int partRow = 0; partRow < enemy.getRows(); partRow++) {
            for (int partColumn = 0; partColumn < enemy.getColumns(); partColumn++) {
                final int targetRow = originRow + partRow;
                final int targetColumn = originColumn + partColumn;
                if (targetRow < 0 || targetRow >= rows || targetColumn < 0 || targetColumn >= columns) {
                    continue;
                }
                final Tile part = enemy.tileAt(of(partColumn, partRow));
                layerTiles[targetRow][targetColumn] = part != null ? part : NULL_TILE;
                written++;
            }
        }
        return written;
    }
}
