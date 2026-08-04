package house.x1337.app.smb3.game.object.level.brick;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.game.collision.CollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.brick.animation.management.BrickBlockBreakAnimationManager;
import house.x1337.app.smb3.game.object.level.brick.animation.management.BrickBlockAnimator;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.ImageResource;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeSingleTiled.BRICK_BLOCK_NO_REWARD;
import static house.x1337.app.smb3.game.LevelSceneCapabilities.LevelSceneLayerCapabilities.INTERACTIVE_OBJECTS;

/**
 * A breakable brick block that carries no reward item.
 *
 * <h2>Hit behaviour — ported from dasm {@code prg008.asm LATP_Brick}</h2>
 * <ul>
 *   <li><b>Large Mario</b> — the block is destroyed immediately. The tile is erased from
 *       the baked background texture and four flying fragments are spawned.</li>
 *   <li><b>Small Mario</b> — the block is not destroyed. Instead, a 10-frame bounce
 *       animation plays where the brick quickly shifts upward then back to its original
 *       position. The brick remains solid throughout.</li>
 * </ul>
 *
 * <h2>Bounce physics — ported from dasm {@code prg001.asm ObjNorm_BounceDU}</h2>
 * <p>When small Mario hits the brick from below:
 * <ul>
 *   <li>A position counter ({@code Level_BlkBump_Pos}) is set to 10 and decrements each frame.</li>
 *   <li>Y velocity is read from the {@code Bouncer_PUpVel} table at the current position index.</li>
 *   <li>Velocity values (in 4.4 fixed-point, 16ths of a pixel per frame):
 *       <pre>$00, -$40, -$40, -$30, -$20, -$10, $00, $10, $20, $30, $40</pre></li>
 *   <li>The brick rises ~4 pixels over the first few frames, pauses at the apex,
 *       then descends back to its resting position over 10 frames total.</li>
 *   <li>A bump sound ({@code SND_PLAYERBUMP}) is played when hit.</li>
 * </ul>
 *
 * <h2>Fragment physics — ported from dasm {@code prg007.asm BrickBusts_DrawAndUpdate}</h2>
 * <ul>
 *   <li>Four fragments arranged as two pairs: upper-left, upper-right, lower-left,
 *       lower-right.</li>
 *   <li>All four share one tile image ({@code fragment.png}) and cycle through
 *       four flip states every 2 frames (none → H → V → HV) to simulate tumbling.</li>
 *   <li>Initial Y velocity: {@code −6/16} game-units/frame (upward).</li>
 *   <li>Gravity: +{@code 1/16} game-units added to Y velocity every 4 frames.</li>
 *   <li>Lower pair extra descent: +{@code 2/16} game-units per frame on top of
 *       shared Y velocity.</li>
 *   <li>X separation: +{@code 1/16} game-units per frame; left pieces move left,
 *       right pieces move right.</li>
 *   <li>Termination: once every fragment has moved off-screen.</li>
 * </ul>
 */
@Getter
@Prototype
@RequiredArgsConstructor
public class BrickBlockWithoutReward implements BrickBlock {
    private final BrickBlockBreakAnimationManager animationManager = getBean(BrickBlockBreakAnimationManager.class);
    private final BrickBlockAnimator brickBlockAnimator = getBean(BrickBlockAnimator.class);
    private final LevelObjectType type = BRICK_BLOCK_NO_REWARD;
    private final ImageResource imageResource;
    private final Offset offset;

    public void triggerBreak(final GameEngine gameEngine) {
        final LevelSceneDimensions dimensions = gameEngine.getLevelScene().getDimensions();
        final Geometry interactiveObjectsLayerGeometry = gameEngine.getLayerGeometry(INTERACTIVE_OBJECTS);

        brickBlockAnimator.unregisterAt(offset);
        eraseFromBakedTexture(interactiveObjectsLayerGeometry, dimensions);
        animationManager.spawnBreak(
            gameEngine,
            offset
        );
    }

    /**
     * Zeroes the 16×16 RGBA pixel region for a tile in the baked
     * {@code "Layer-INTERACTIVE_OBJECTS"} texture and signals jme3 to re-upload it.
     *
     * <p>jme3 image row 0 is the bottom of the image (= bottom of the level = highest
     * tile-row index). The Y-flip formula is:
     * <pre>imgRow = (totalRows − 1 − tileRow) × TILE_SPRITE_SIZE + (TILE_SPRITE_SIZE − 1 − sprPixelRow)</pre>
     */
    @Override
    public void onCollisionFromBelow(final LevelScenePlayer levelScenePlayer) {
        final CollisionGrid collisionGrid = levelScenePlayer.getCollisionGrid();
        final GameEngine gameEngine = levelScenePlayer.getGameEngine();
        if (levelScenePlayer.isLarge()) {
            // Remove from collision grid so further probes treat it as empty
            collisionGrid.removeLevelObjectAt(offset);
            // Erase tile visually and spawn the four flying fragments
            triggerBreak(gameEngine);
        } else {
            // Small Mario bounce: brick stays intact but visually bounces
            // Ported from dasm prg001.asm ObjNorm_BounceDU / Bouncer_PUpVel
            animationManager.spawnBounce(gameEngine, offset);
        }
    }
}
