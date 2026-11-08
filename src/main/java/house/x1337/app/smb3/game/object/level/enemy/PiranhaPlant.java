package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.Reward;
import house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode;
import house.x1337.app.smb3.enumeration.enemy.PiranhaPlantState;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.enemy.animator.PiranhaPlantAnimator;
import house.x1337.app.smb3.game.object.level.enemy.family.PiranhaPlantFamilyEnemy;
import house.x1337.app.smb3.model.game.Coordinates;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import house.x1337.app.smb3.model.service.LevelObjectData;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.PIRANHA_PLANT;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantMode.GREEN;
import static house.x1337.app.smb3.enumeration.enemy.PiranhaPlantState.HIDDEN;
import static house.x1337.app.smb3.model.game.Dimensions.fullTile;
import static house.x1337.app.smb3.model.game.motion.FixedPointVelocity.STILL;

@Data
@Prototype
@RequiredArgsConstructor
public final class PiranhaPlant implements PiranhaPlantFamilyEnemy {
    private static final Dimensions PART_DIMENSIONS = fullTile("PiranhaPlant");

    private final PiranhaPlantAnimator animator = getBean(PiranhaPlantAnimator.class);
    private final LevelObjectType type = PIRANHA_PLANT;
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
    private boolean killed;
    private boolean spawnedIntoScene;
    private double originY;
    private int animationCounter;
    private int headFrameIndex;
    private int stateTimer;

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
    public void refreshHeadFrame() {
        animator.applyCurrentFrame(this);
    }
}
