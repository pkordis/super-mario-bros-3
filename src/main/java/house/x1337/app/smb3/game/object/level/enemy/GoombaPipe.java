package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.enemy.GoombaMode;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.enemy.base.EnemyLevelObject;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import house.x1337.app.smb3.model.service.LevelObjectData;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.GOOMBA_PIPE_RIGHT_NORMAL;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static house.x1337.app.smb3.enumeration.enemy.GoombaMode.NORMAL;
import static house.x1337.app.smb3.model.game.Coordinates.of;
import static house.x1337.app.smb3.model.game.motion.FixedPointVelocity.STILL;

/**
 * An invisible dispenser placed beside a side pipe's lip. On a steady pulse it sends a Goomba sliding out to the right,
 * from the cell left of its own, on three pulses in four and only while the player is on that side. It is never
 * deleted off screen. See kb/dynamic-entities.md, "Goomba pipe".
 */
@Data
@Prototype
@RequiredArgsConstructor
public final class GoombaPipe implements EnemyLevelObject {
    static final int FIRST_PULSE_TICKS = 96;
    static final int PULSE_TICKS = 112;
    static final int PULSES_PER_CYCLE = 4;
    static final int SPAWN_RISE_PIXELS = 3;
    private static final Dimensions SPRITE_DIMENSIONS = Dimensions.fullTile("GoombaPipe");
    private static final DimensionsPixels BOUNDS_PIXELS = new DimensionsPixels(TILE_SPRITE_SIZE, TILE_SPRITE_SIZE);

    private final LevelObjectType type = GOOMBA_PIPE_RIGHT_NORMAL;
    private final Dimensions spriteDimensions = SPRITE_DIMENSIONS;
    private final DimensionsPixels boundsPixels = BOUNDS_PIXELS;
    private final FixedPointVelocity velocity = STILL;
    private final GameEngine gameEngine;
    private final Coordinates offset;

    private Geometry spriteGeometry;
    private GoombaMode mode = NORMAL;
    private PixelPosition position;
    private boolean expired;
    private boolean spawnedIntoScene;
    private int pulseTimer = FIRST_PULSE_TICKS;
    private int pulseInCycle;

    @PostConstruct
    void init() {
        position = PixelPosition.ofTile(offset);
    }

    @Override
    public void configure(final LevelObjectData data) {
        mode = data
            .getEnum(GoombaMode.class, MODE_ATTRIBUTE)
            .orElse(NORMAL);
    }

    @Override
    public void spawnIntoScene() {
        spawnedIntoScene = true;
    }

    @Override
    public boolean isHittable() {
        return false;
    }

    @Override
    public boolean isSlotless() {
        return true;
    }

    @Override
    public void hurt(final LevelScenePlayer levelScenePlayer) {
        // Nothing: the dispenser is not there to be touched.
    }

    /** Counts down to the next pulse; the pulse is spent even when no Goomba comes out. */
    @Override
    public void motionUpdate() {
        if (expired || gameEngine.getLevelCompletion().isUnderway()) {
            return;
        }
        if (pulseTimer > 0) {
            pulseTimer--;
        }
        if (pulseTimer > 0) {
            return;
        }
        pulseTimer = PULSE_TICKS;
        pulseInCycle = (pulseInCycle + 1) % PULSES_PER_CYCLE;
        if (pulseInCycle != 0) {
            dispense();
        }
    }

    private void dispense() {
        final Coordinates lip = of(offset.x() - 1, offset.y());
        final PixelPosition start = PixelPosition.ofTile(lip).plusY(-SPAWN_RISE_PIXELS);
        if (!isPlayerRightOf(start)) {
            return;
        }
        final Goomba goomba = getBean(Goomba.class, gameEngine, lip);
        goomba.setMode(mode);
        goomba.slideOutOfAPipe(start, true);
        getActiveObjectGrid().spawnEnemy(goomba);
    }

    private boolean isPlayerRightOf(final PixelPosition start) {
        return getCollisionGrid()
            .findClosestPlayerTo(start.x(), start.y())
            .map(player -> player.getPosition().getX() >= start.x())
            .orElse(false);
    }
}
