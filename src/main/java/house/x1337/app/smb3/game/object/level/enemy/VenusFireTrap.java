package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode;
import house.x1337.app.smb3.enumeration.enemy.PiranhaPlantState;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.enemy.animator.VenusFireTrapAnimator;
import house.x1337.app.smb3.game.object.level.enemy.family.PiranhaPlantFamilyEnemy;
import house.x1337.app.smb3.game.object.level.projectile.PiranhaFireballMotionManager;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.WorldOffset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import house.x1337.app.smb3.model.service.LevelObjectData;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.VENUS_FIRE_TRAP;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode.GREEN;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantState.HIDDEN;
import static house.x1337.app.smb3.model.game.Dimensions.fullTile;
import static house.x1337.app.smb3.model.game.motion.FixedPointVelocity.STILL;
import static java.lang.Math.floor;

@Data
@Prototype
@RequiredArgsConstructor
public final class VenusFireTrap implements PiranhaPlantFamilyEnemy {
    public static final int FAR_X_SPEED = 14;
    public static final int FAR_Y_SPEED = 5;
    public static final int FIREBALL_X_OFFSET_PIXELS = 3;
    public static final int FIRE_STATE_WAIT_TICKS = 96;
    public static final int FIRST_SPIT_TIMER = 64;
    public static final int NEAR_RANGE_LEFT_PIXELS = 80;
    public static final int NEAR_RANGE_WIDTH_PIXELS = 160;
    public static final int NEAR_X_SPEED = 11;
    public static final int NEAR_Y_SPEED = 11;
    public static final int PLANT_HEIGHT_PIXELS = 32;
    public static final int RIGHTMOST_CHECKED_COLUMN_PIXELS = 16;
    public static final int SCREEN_HEIGHT_PIXELS = 240;
    public static final int SCREEN_WIDTH_PIXELS = 256;
    public static final int SECOND_SPIT_TIMER = 16;
    public static final int WORLD_ONE = 1;
    public static final int WORLD_ONE_SPIT_TIMER = 40;
    private static final Dimensions PART_DIMENSIONS = fullTile("VenusFireTrap");

    private final VenusFireTrapAnimator animator = getBean(VenusFireTrapAnimator.class);
    private final LevelObjectType type = VENUS_FIRE_TRAP;
    private final Dimensions spriteDimensions = PART_DIMENSIONS;
    private final GameEngine gameEngine;
    private final Coordinates offset;

    private FixedPointVelocity velocity = STILL;
    private Geometry spriteGeometry;
    private Geometry stemGeometry;
    private PiranhaPlantMode mode = GREEN;
    private PiranhaPlantState state = HIDDEN;
    private PixelPosition position;
    private Reward pendingScoreReward;
    private boolean expired;
    private boolean headFlippedHorizontally;
    private boolean headFlippedVertically;
    private boolean killed;
    private boolean spawnedIntoScene;
    private double originY;
    private int animationCounter;
    private int headFrameIndex;
    private int stateTimer;

    /**
     * The fireball's velocity toward the player.
     *
     * @param playerDx     {@code Player X - plant X}, in whole pixels
     * @param playerHigher whether {@code Player Y} is above the plant's top
     * @return the 4.4 fixed-point velocity, X negated for a player on the left, Y for one above
     */
    public static FixedPointVelocity aimAt(final int playerDx, final boolean playerHigher) {
        final int fromRangeLeft = playerDx + NEAR_RANGE_LEFT_PIXELS;
        final boolean near = fromRangeLeft >= 0 && fromRangeLeft < NEAR_RANGE_WIDTH_PIXELS;
        final int xSpeed = near ? NEAR_X_SPEED : FAR_X_SPEED;
        final int ySpeed = near ? NEAR_Y_SPEED : FAR_Y_SPEED;
        return new FixedPointVelocity(
            playerDx < 0 ? -xSpeed : xSpeed,
            playerHigher ? -ySpeed : ySpeed
        );
    }

    @PostConstruct
    void init() {
        position = PixelPosition.ofTile(offset).plus(CENTRE_ON_PIPE_PIXELS, PIPE_TOP_BELOW_STARTER_PIXELS);
        originY = position.y();
    }

    @Override
    public void configure(final LevelObjectData data) {
        mode = data
            .getEnum(PiranhaPlantMode.class, MODE_ATTRIBUTE)
            .orElse(GREEN);
    }

    @Override
    public int getRiseDistancePixels() {
        return mode.getRiseDistancePixels();
    }

    @Override
    public DimensionsPixels getBoundsPixels() {
        return new DimensionsPixels(BOUND_BOX_WIDTH, mode.getBoundBoxHeightPixels());
    }

    @Override
    public int getStateWaitTicks() {
        return FIRE_STATE_WAIT_TICKS;
    }

    @Override
    public void faceThePlayer() {
        closestPlayer().ifPresent(player -> {
            headFlippedHorizontally = playerDx(player) >= 0;
            headFlippedVertically = isPlayerHigher(player);
        });
    }

    @Override
    public void attackWhileOut() {
        setAnimationCounter(0);
        if (isSpitTick(getStateTimer())) {
            spitFire();
        }
    }

    public boolean isSpitTick(final int stateTimer) {
        if (gameEngine.getPlayerData().getWorld() == WORLD_ONE) {
            return stateTimer == WORLD_ONE_SPIT_TIMER;
        }
        return stateTimer == FIRST_SPIT_TIMER || stateTimer == SECOND_SPIT_TIMER;
    }

    public void spitFire() {
        final Optional<LevelScenePlayer> target = closestPlayer();
        if (target.isEmpty() || isAnySpriteOffScreen()) {
            return;
        }
        final LevelScenePlayer player = target.get();
        getBean(PiranhaFireballMotionManager.class).spawn(
            gameEngine,
            position.plusX(FIREBALL_X_OFFSET_PIXELS),
            aimAt(playerDx(player), isPlayerHigher(player))
        );
    }

    public boolean isAnySpriteOffScreen() {
        final AxisAlignedBoundingBox screen = gameEngine.getCameraState().getActiveObjectRegion(0);
        final int dx = (int) floor(position.x()) - (int) floor(screen.left());
        final int dy = (int) floor(position.y()) - (int) floor(screen.top());
        final boolean horizontallyOff = dx < 0 || dx + RIGHTMOST_CHECKED_COLUMN_PIXELS >= SCREEN_WIDTH_PIXELS;
        final boolean verticallyOff = dy + PART_SIZE_PIXELS < 0 || dy + PLANT_HEIGHT_PIXELS >= SCREEN_HEIGHT_PIXELS;
        return horizontallyOff || verticallyOff;
    }

    @Override
    public void refreshHeadFrame() {
        animator.applyCurrentFrame(this);
    }

    @Override
    public void placeHead(final Geometry head, final WorldOffset offset) {
        head.setLocalScale(headFlippedHorizontally ? -1f : 1f, headFlippedVertically ? -1f : 1f, 1f);
        placePart(
            head,
            headFlippedHorizontally ? offset.x() + spriteDimensions.width() : offset.x(),
            headFlippedVertically ? offset.y() + spriteDimensions.height() : offset.y(),
            offset.z()
        );
    }

    private Optional<LevelScenePlayer> closestPlayer() {
        return getCollisionGrid().findClosestPlayerTo(position.x(), position.y());
    }

    private int playerDx(final LevelScenePlayer player) {
        return (int) floor(player.getPosition().getX()) - (int) floor(position.x());
    }

    private boolean isPlayerHigher(final LevelScenePlayer player) {
        return floor(player.getPosition().getY()) < floor(position.y());
    }
}
