package house.x1337.app.smb3.model.service;

/** How many stored scenes, and cells across them, a tile's move to another layer changes. */
public record TileMoveSummary(
    int scenes,
    int cells
) {
}
