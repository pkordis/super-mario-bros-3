package house.x1337.app.smb3.game.object.level.enemy;

import com.jme3.scene.Geometry;
import house.x1337.app.smb3.annotation.Prototype;
import house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.object.level.LevelObjectType;
import house.x1337.app.smb3.game.object.level.enemy.animator.KoopaTroopaAnimator;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.Dimensions;
import house.x1337.app.smb3.model.game.DimensionsPixels;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.collision.AxisAlignedBoundingBox;
import house.x1337.app.smb3.model.game.motion.FixedPointVelocity;
import house.x1337.app.smb3.model.game.motion.PixelPosition;
import house.x1337.app.smb3.model.service.LevelObjectData;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static house.x1337.app.smb3.bean.StaticBeanFactory.getBean;
import static house.x1337.app.smb3.enumeration.LevelObjectTypeMultiTiled.KOOPA_TROOPA;
import static house.x1337.app.smb3.enumeration.enemy.EnemyMode.MODE_ATTRIBUTE;
import static house.x1337.app.smb3.enumeration.enemy.KoopaTroopaMode.RED;
import static house.x1337.app.smb3.model.game.Dimensions.doubleTileHeight;
import static house.x1337.app.smb3.model.game.motion.FixedPointVelocity.STILL;

/**
 * The Koopa Troopa - {@code OBJ_GREENTROOPA = $6C} and {@code OBJ_REDTROOPA = $6D}, selected by
 * {@link #configure(LevelObjectData)} (dasm prg004.asm:69-70).
 *
 * <p>Both colours walk exactly as a Goomba does - facing is direction, {@code ±$08} a frame, gravity
 * {@code $03} capped at {@code $40}, about-face into a wall - and differ in <b>one branch</b>: the red
 * one will not walk off a ledge, the green one will. In the ROM that branch is the whole difference
 * between the two handlers: {@code $6D} runs {@code ObjNorm_RedTroopa} (prg004.asm:3788), which is
 * {@code ObjNorm_GroundTroop} plus {@code PRG004_B283}, while {@code $6C} runs
 * {@code ObjNorm_GroundTroop} itself - the same handler the Goomba uses. One class carries both, with
 * the mode gating that single branch in {@link #guardAgainstLedge()}.
 *
 * <p>This is a deliberate exception to {@code kb/dynamic-entities.md}'s "a separate {@code ObjNorm_*}
 * means a separate class" rule, and the reason it is safe here is worth stating: the Para-Goomba earned
 * its own class because {@code ObjNorm_ParaGoomba} is a wholly different handler <em>and</em> a
 * different draw routine. The two troopas share their draw ({@code Troopa_Draw}), their init
 * ({@code ObjInit_GroundTroop}), their bound box, their sprite geometry and every line of their motion
 * bar one conditional. Splitting them would mean duplicating all of that to express a single
 * {@code if}.
 *
 * <p>Deliberately <b>not collidable yet</b>: {@link #isHittable()} is hard {@code false}, so its manager
 * never puts it in the broadphase and nothing can reach it or be reached by it. Stomping into a shell,
 * hurting the player and the tail wham are all one state machine ({@code ObjState_Shelled}, dasm
 * prg000.asm:2151) and land together rather than half-built here.
 */
@Getter
@Prototype
@RequiredArgsConstructor
public final class KoopaTroopa implements KoopaTroopaFamilyEnemy {
    private static final DimensionsPixels BOUNDS_PIXELS = new DimensionsPixels(BOUND_BOX_WIDTH, BOUND_BOX_HEIGHT);
    private static final Dimensions SPRITE_DIMENSIONS = doubleTileHeight("KoopaTroopa");

    /**
     * {@code Object_ApplyXVel} is called <b>twice</b> after a ledge about-face (dasm prg004.asm:3845):
     * one step cancels the move that cleared the edge, the second takes a normal step back inland.
     */
    private static final int LEDGE_RECOVERY_STEPS = 2;

    private final KoopaTroopaAnimator animator = getBean(KoopaTroopaAnimator.class);
    private final LevelObjectType type = KOOPA_TROOPA;
    private final Dimensions spriteDimensions = SPRITE_DIMENSIONS;
    private final DimensionsPixels boundsPixels = BOUNDS_PIXELS;
    private final GameEngine gameEngine;
    private final Offset offset;

    private Geometry spriteGeometry;
    private KoopaTroopaMode mode = RED;

    @Setter
    private boolean expired;
    private boolean facingRight;
    private boolean grounded;
    private boolean ledgeTurned;
    private boolean spawnedIntoScene;
    private int walkFrameIndex;
    private int walkFrameTicks;
    private PixelPosition position;
    private FixedPointVelocity velocity = STILL;

    /**
     * Only the position may be derived here. An enemy is constructed while {@code GameEngine.setupLevel}
     * is still building the collision grid, so there is no scene graph, no players and nothing to ask
     * about the world yet - all of which is why {@link #spawnIntoScene()} exists.
     */
    @PostConstruct
    void init() {
        position = PixelPosition.ofTile(offset);
    }

    /**
     * Picks the variant. For this enemy the placement attribute is doing more than naming a sprite
     * folder: it also decides whether {@link #guardAgainstLedge()} fires, because colour is what tells
     * {@code $6C} and {@code $6D} apart in the ROM's handler table.
     *
     * <p>Falls back to {@link KoopaTroopaMode#RED}, which is the more forgiving of the two to be wrong
     * about - a troopa that turns at an edge it should have walked off stays visible and reportable,
     * whereas the other way round it quietly leaves the level.
     */
    @Override
    public void configure(final LevelObjectData data) {
        mode = data
            .getEnum(KoopaTroopaMode.class, MODE_ATTRIBUTE)
            .orElse(RED);
    }

    /**
     * The troopa's collision box, which is the ROM's {@code Object_BoundBox} row 01 - the very same row
     * the Goomba and Para-Goomba use ({@code OAT_BOUNDBOX01}, dasm prg000.asm:407).
     *
     * <p>Worth seeing clearly, because it is far smaller than the troopa looks: the sprite spans
     * {@code y-11 .. y+15}, 27 px tall, but the box is 8 px tall at {@code y+2 .. y+10} - squarely on the
     * shell. The ROM lets the player pass through a troopa's head and its feet without any contact at
     * all, and that is reproduced here rather than corrected.
     *
     * @return the box spanning X {@code [x+1, x+14)}, Y {@code [y+2, y+10)}
     */
    @Override
    public AxisAlignedBoundingBox getBounds() {
        return AxisAlignedBoundingBox.ofSize(
            position.plus(BOUND_BOX_LEFT_OFFSET, BOUND_BOX_TOP_OFFSET),
            BOUNDS_PIXELS
        );
    }

    @Override
    public void spawnIntoScene() {
        if (spawnedIntoScene) {
            return;
        }
        spawnedIntoScene = true;
        faceClosestPlayer();

        spriteGeometry = animator.buildSprite(this);
        gameEngine.getRootNode().attachChild(spriteGeometry);

        erasePlacementFromBakedTexture();
        positionSprite();
    }

    /**
     * Non-collidable for now, which this one flag is enough to express: its manager only inserts a
     * hittable instance into the broadphase, so nothing can reach it and it reaches nothing - no stomp,
     * no body hit, no tail strike, no object-to-object bump.
     *
     * @return {@code false} until the shell state exists for it to be stomped into
     */
    @Override
    public boolean isHittable() {
        return false;
    }

    /**
     * No-op while the troopa is non-collidable. The body hit that reaches this in the ROM
     * ({@code Object_HoldKickOrHurtPlayer}, dasm prg000.asm:4042) has to choose between hurting the
     * player, being picked up and being kicked, so it arrives with the shell rather than before it.
     */
    @Override
    public void hurt(final LevelScenePlayer levelScenePlayer) {
        // Deliberately empty - see the class Javadoc.
    }

    /**
     * {@code ObjInit_GroundTroop} (dasm prg004.asm:3753) is the troopa's entire init: face the nearest
     * player and nothing else. Identical to the Goomba's, which is why they share the table entry - and
     * shared by both colours, which is why it is not gated on the mode.
     */
    void faceClosestPlayer() {
        final double center = position.x() + BODY_SIZE_PIXELS / 2.0;
        facingRight = gameEngine
            .getCollisionGrid()
            .findClosestPlayerTo(position.x(), position.y())
            .map(closestPlayer -> closestPlayer.getPosition().getX() + BODY_SIZE_PIXELS / 2.0 >= center)
            .orElse(false);
    }

    /**
     * One frame of the troopa's normal handler, in the ROM's order: advance the frame clock, reload the X
     * velocity from the facing, move, resolve the wall, then the ledge, then apply gravity.
     *
     * <p>{@code Object_DeleteOffScreen}, which both handlers open with, is deliberately not ported: the
     * camera activation window freezes an off-screen enemy rather than destroying it, which is the
     * project's standing replacement for it and the precedent the Para-Goomba set.
     * {@code GroundTroop_BumpOffOthers} and {@code Object_HandleBumpUnderneath} both need the broadphase,
     * so both wait for the shell.
     */
    @Override
    public void motionUpdate() {
        if (expired) {
            return;
        }

        // Facing IS direction: XVel is reloaded from the flip bit every frame (dasm prg004.asm:3811).
        velocity = velocity.directedX(facingRight, WALK_SPEED_FIXED_POINT);

        advanceWalkFrame();
        moveHorizontally();
        moveVertically();
        guardAgainstLedge();
        applyGravity();
        positionSprite();

        if (hasFallenOffLevel()) {
            setExpired(true);
        }
    }

    private void advanceWalkFrame() {
        walkFrameTicks++;
        if (walkFrameTicks < TICKS_PER_WALK_FRAME) {
            return;
        }
        walkFrameTicks = 0;
        walkFrameIndex = (walkFrameIndex + 1) % animator.walkFrameCount(this);
        animator.applyCurrentFrame(this);
    }

    private void moveHorizontally() {
        final PixelPosition proposed = position.steppedX(velocity);
        if (isWallAhead(proposed)) {
            // Object_FlipFace: the facing bit alone, and the step is abandoned rather than taken into
            // the wall (dasm prg004.asm:3827).
            facingRight = !facingRight;
            return;
        }
        position = proposed;
    }

    private void moveVertically() {
        final PixelPosition proposed = position.steppedY(velocity);
        if (velocity.descending() && isGroundBelow(proposed)) {
            // Object_HitGround on the grounded path (dasm prg004.asm:3850-3857, PRG004_B29B → B29F).
            // Resolved from the proposed feet row rather than by flooring the current Y, so a troopa
            // falling fast enough to overshoot into the tile still lands on the surface and not a tile
            // above it.
            final int feetRow = proposed.plusY(BODY_SIZE_PIXELS).row();
            restOn((double) feetRow * TILE_SPRITE_SIZE - BODY_SIZE_PIXELS);
            return;
        }
        position = proposed;
        grounded = false;
    }

    /**
     * The red troopa's defining behaviour: {@code PRG004_B283} (dasm prg004.asm:3829), the branch that
     * "keeps a red troopa from marching himself right off an edge".
     *
     * <p>When the feet probe finds nothing, the troopa about-faces - negating the X velocity and flipping
     * the facing, as {@code Object_AboutFace} does - re-applies that velocity <b>twice</b>, and is then
     * snapped back onto the floor by {@code Object_HitGround}. The result is that it never descends below
     * the surface it is patrolling, which is what makes a red troopa look like it is respecting bounds:
     * nothing stores those bounds, they fall out of this probe running every frame.
     *
     * <p>A green troopa reaches none of it. {@code ObjNorm_GroundTroop} has no equivalent branch, so it
     * steps off the edge and falls exactly as a Goomba does - which is the whole behavioural difference
     * between the colours and the reason {@link #turnsAtLedges()} is consulted first.
     *
     * <p>{@code Objects_Var4} is a one-shot latch, cleared on every grounded frame. Without it a troopa
     * that is genuinely airborne - thrown, or bumped from below - would about-face once a frame all the
     * way down. With it, such a troopa about-faces once and then falls, which is the ROM's behaviour and
     * not a bug here.
     *
     * <p>Two faithful roughnesses to leave alone. The recovery steps are not collision-checked, so a
     * troopa on a platform barely wider than itself can be stepped into the wall behind it - harmless,
     * because the next frame's wall probe turns it straight back. And the snap is the ROM's literal
     * {@code Y & $F0}, so a troopa that was already mid-tile when it first found no ground is pulled up
     * to the tile boundary.
     */
    private void guardAgainstLedge() {
        if (!turnsAtLedges()) {
            return;
        }
        if (grounded) {
            ledgeTurned = false;
            return;
        }
        if (ledgeTurned) {
            return;
        }
        ledgeTurned = true;

        facingRight = !facingRight;
        velocity = velocity.negatedX();
        for (int step = 0; step < LEDGE_RECOVERY_STEPS; step++) {
            position = position.steppedX(velocity);
        }
        restOn((double) position.row() * TILE_SPRITE_SIZE);
    }

    /**
     * Which of the two handlers this troopa is running, expressed as the one thing they disagree about.
     *
     * <p>Kept here rather than on {@link KoopaTroopaMode} on purpose: an {@code EnemyMode} is a sprite
     * folder for every other enemy in the project, and encoding behaviour in one would invite the next
     * mode to do the same. The entity owns its own handler selection.
     *
     * @return {@code true} for {@code OBJ_REDTROOPA}, which runs {@code ObjNorm_RedTroopa}
     */
    private boolean turnsAtLedges() {
        return mode == RED;
    }

    /** {@code Object_HitGround} (dasm prg000.asm:872): sit on the given line and halt the fall. */
    private void restOn(final double groundAlignedY) {
        position = position.withY(groundAlignedY);
        velocity = velocity.withY(0);
        grounded = true;
    }

    private void applyGravity() {
        velocity = velocity.falling(GRAVITY_FIXED_POINT, MAX_FALL_FIXED_POINT);
    }
}
