package house.x1337.app.smb3.model.game.enemy;

import house.x1337.app.smb3.model.game.Coordinates;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static java.lang.Math.floorDiv;
import static java.lang.Math.floorMod;

/**
 * An enemy placed in a level scene: its stamp, and where the top-left pixel of its rendering starter part is, in
 * level pixels. The editor draws the enemy there and the game spawns it there. See kb/editor.md, "Enemy placements".
 */
public record EnemyPlacement(
    String stampId,
    int x,
    int y
) {
    public Coordinates rendererCell() {
        return of(floorDiv(x, TILE_SPRITE_SIZE), floorDiv(y, TILE_SPRITE_SIZE));
    }

    /** How far, in pixels, the rendering starter sits right of and below the top-left of its cell. */
    public Coordinates offsetInCell() {
        return of(floorMod(x, TILE_SPRITE_SIZE), floorMod(y, TILE_SPRITE_SIZE));
    }

    public EnemyPlacement movedBy(final int dx, final int dy) {
        return new EnemyPlacement(stampId, x + dx, y + dy);
    }
}
