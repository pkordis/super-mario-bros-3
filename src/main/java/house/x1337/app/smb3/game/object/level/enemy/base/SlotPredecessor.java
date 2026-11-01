package house.x1337.app.smb3.game.object.level.enemy.base;

/**
 * An enemy that when defeated, turns into another enemy in its own object slot. E.g. a Para-Goomba into a
 * Goomba, a Koopa ParaTroopa into a Koopa Troopa, etc.
 */
public interface SlotPredecessor extends EnemyLevelObject {
    boolean isFacingRight();
}
