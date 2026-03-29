package house.x1337.app.smb3.util.provider;

import house.x1337.app.smb3.model.ui.tile.Tile;

import java.util.Optional;

public interface TilesProvider {
    Optional<Tile> findById(int tileId);
}
