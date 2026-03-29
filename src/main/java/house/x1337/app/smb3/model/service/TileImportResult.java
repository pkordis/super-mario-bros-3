package house.x1337.app.smb3.model.service;

import house.x1337.app.smb3.model.ui.tile.Tile;

import java.util.List;

public record TileImportResult(
    Tile[][] grid,
    int rows,
    int columns,
    List<Tile> newTiles
) {}
