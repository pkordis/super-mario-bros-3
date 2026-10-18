package house.x1337.app.smb3.game.object.level.enemy;

import house.x1337.app.smb3.bean.StaticBeanFactory;
import house.x1337.app.smb3.game.collision.StaticEnvironmentCollisionGrid;
import house.x1337.app.smb3.game.engine.GameEngine;
import house.x1337.app.smb3.game.level.scene.LevelScene;
import house.x1337.app.smb3.game.object.level.enemy.animator.ParaGoombaAnimator;
import house.x1337.app.smb3.game.player.level.LevelScenePlayer;
import house.x1337.app.smb3.model.game.LevelSceneDimensions;
import house.x1337.app.smb3.model.game.Offset;
import house.x1337.app.smb3.model.game.player.PlayerPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;
import static java.lang.Math.min;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The Para-Goomba's hop/float cycle, as {@code ObjNorm_ParaGoomba} defines it (dasm prg004.asm:3011):
 * three little {@code -$10} hops, then a big {@code -$30} one followed by an {@code $50}-tick cooldown,
 * all under a net {@code +$02}-a-frame fall rather than the ground Goomba's {@code +$03}.
 */
class ParaGoombaHopTest {
    private static final double TOLERANCE = 1e-9;
    private static final int COLUMNS = 24;
    private static final int ROWS = 12;
    private static final int WALK_FRAME_COUNT = 2;

    private static final int LITTLE_HOP_YVEL = -0x10;
    private static final int BIG_HOP_YVEL = -0x30;
    private static final int BIG_HOP_COOLDOWN_TICKS = 0x50;

    /** {@code Objects_Var5 & $3F} - the beat the grounded re-face lands on. */
    private static final int REFACE_PERIOD_TICKS = 0x40;

    /** The floor, with the row the Para-Goomba rests on between hops directly above it. */
    private static final int FLOOR_ROW = 10;
    private static final int REST_ROW = FLOOR_ROW - 1;
    private static final double REST_ROW_PIXEL_Y = REST_ROW * (double) TILE_SPRITE_SIZE;

    /** A ceiling one row above the resting head, so the very first hop meets it. */
    private static final int CEILING_ROW = REST_ROW - 1;

    private static final int START_COLUMN = 10;

    private MockedStatic<StaticBeanFactory> staticBeanFactory;
    private StaticEnvironmentCollisionGrid collisionGrid;
    private GameEngine gameEngine;
    private boolean[][] solids;
    private boolean[][] oneWayPlatforms;

    @BeforeEach
    void prepare() {
        solids = new boolean[ROWS][COLUMNS];
        oneWayPlatforms = new boolean[ROWS][COLUMNS];
        for (int column = 0; column < COLUMNS; column++) {
            solids[FLOOR_ROW][column] = true;
        }

        collisionGrid = mock(StaticEnvironmentCollisionGrid.class);
        when(collisionGrid.isGroundSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isSolid(invocation.getArgument(0), invocation.getArgument(1)));
        when(collisionGrid.isWallOrCeilingSolidTile(anyInt(), anyInt()))
            .thenAnswer(invocation -> isSolidToHeadAndWalls(
                invocation.getArgument(0),
                invocation.getArgument(1)
            ));
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.empty());

        final LevelScene levelScene = mock(LevelScene.class);
        when(levelScene.getDimensions()).thenReturn(new LevelSceneDimensions(COLUMNS, ROWS));

        gameEngine = mock(GameEngine.class);
        when(gameEngine.getLevelScene()).thenReturn(levelScene);
        when(gameEngine.getCollisionGrid()).thenReturn(collisionGrid);

        // Only the body cycle length matters to motion; repainting a sprite that was never built is a
        // no-op on the stub, which is what keeps motionUpdate free of the scene graph.
        final ParaGoombaAnimator animator = mock(ParaGoombaAnimator.class);
        when(animator.walkFrameCount(any())).thenReturn(WALK_FRAME_COUNT);
        staticBeanFactory = mockStatic(StaticBeanFactory.class);
        staticBeanFactory
            .when(() -> StaticBeanFactory.getBean(ParaGoombaAnimator.class))
            .thenReturn(animator);
    }

    @AfterEach
    void tearDown() {
        staticBeanFactory.close();
    }

    @Test
    @DisplayName("Hops three little hops and then a big one, over and over")
    void hopCycleIsThreeLittleHopsThenABigOne() {
        // Prepare
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();

        // Execute - long enough for two full cycles including both cooldowns
        final List<Integer> launches = recordHopLaunches(paraGoomba, 600);

        // Verify - Var4 counts 1, 2, 3, then the 4th resets it and launches the big hop
        assertThat(launches)
            .as("Three -$10 hops then a -$30 one, repeating")
            .startsWith(
                LITTLE_HOP_YVEL,
                LITTLE_HOP_YVEL,
                LITTLE_HOP_YVEL,
                BIG_HOP_YVEL,
                LITTLE_HOP_YVEL,
                LITTLE_HOP_YVEL,
                LITTLE_HOP_YVEL,
                BIG_HOP_YVEL
            );
    }

    @Test
    @DisplayName("The big hop carries it far higher than a little one")
    void bigHopClimbsFurtherThanALittleOne() {
        // Prepare
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();

        // Execute - at a net +$02/frame, -$10 buys (16+14+...+2)/16 px of climb and -$30 buys
        // (48+46+...+2)/16. Measured from the launch, since Object_HitGround has just re-aligned it.
        tickUntilLaunchOf(paraGoomba, LITTLE_HOP_YVEL, 50);
        final double littleHopFrom = paraGoomba.getPosition().y();
        final double littleHopApex = apexOverNextTicks(paraGoomba, 20);

        tickUntilLaunchOf(paraGoomba, BIG_HOP_YVEL, 300);
        final double bigHopFrom = paraGoomba.getPosition().y();
        final double bigHopApex = apexOverNextTicks(paraGoomba, 40);

        // Verify
        assertThat(littleHopFrom - littleHopApex)
            .as("A little hop rises 4.5 px")
            .isCloseTo(4.5, within(TOLERANCE));
        assertThat(bigHopFrom - bigHopApex)
            .as("The big hop rises 37.5 px, over two tiles")
            .isCloseTo(37.5, within(TOLERANCE));
    }

    @Test
    @DisplayName("The big hop is followed by an $50-tick cooldown before hopping resumes")
    void bigHopIsFollowedByAnEightyTickCooldown() {
        // Prepare - run up to the tick the big hop launches on
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();
        final int bigHopTick = tickUntilLaunchOf(paraGoomba, BIG_HOP_YVEL, 300);

        // Execute - the next launch of any size
        final int nextHopTick = bigHopTick + tickUntilLaunchOf(paraGoomba, LITTLE_HOP_YVEL, 300);

        // Verify - Objects_Timer is run down a tick at a time (dasm prg000.asm:1855), and the hop test
        // only passes once it has reached zero, so the gap is the timer itself
        assertThat(nextHopTick - bigHopTick)
            .as("$50 ticks from the big hop to the next one")
            .isEqualTo(BIG_HOP_COOLDOWN_TICKS);
        assertThat(paraGoomba.getHopCounter())
            .as("And the cycle starts over from the first little hop")
            .isEqualTo(1);
    }

    @Test
    @DisplayName("It waits out the cooldown sitting on the ground rather than in the air")
    void cooldownIsSpentGrounded() {
        // Prepare
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();
        tickUntilLaunchOf(paraGoomba, BIG_HOP_YVEL, 300);

        // Execute - the big hop's whole flight, then a few ticks more
        tick(paraGoomba, 50);

        // Verify
        assertThat(paraGoomba.isGrounded()).as("Back down, waiting").isTrue();
        assertThat(paraGoomba.getPosition().y())
            .as("Feet flush with the floor it launched from")
            .isCloseTo(REST_ROW_PIXEL_Y, within(TOLERANCE));
        assertThat(paraGoomba.getHopCooldownTicks()).as("Still counting down").isPositive();
    }

    @Test
    @DisplayName("Falling accelerates by a net $02 a frame and settles at $3F, not the Goomba's $40")
    void floatFallRateIsNetTwoPerFrame() {
        // Prepare - dropped into open sky so there is room to reach terminal velocity
        removeFloor();
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, 0);
        paraGoomba.faceClosestPlayer();

        // Execute & Verify - Object_Move adds OBJECT_FALLRATE = $03, then the handler's DEC takes one back
        tick(paraGoomba, 1);
        assertThat(paraGoomba.getVelocity().y()).as("$03 - $01").isEqualTo(2);

        tick(paraGoomba, 30);
        assertThat(paraGoomba.getVelocity().y())
            .as("31 frames x $02, still one step under the cap")
            .isEqualTo(62);

        tick(paraGoomba, 1);
        assertThat(paraGoomba.getVelocity().y())
            .as("OBJECT_MAXFALL caps at $40 inside Object_Move, and the DEC lands after it")
            .isEqualTo(63);

        tick(paraGoomba, 20);
        assertThat(paraGoomba.getVelocity().y()).as("And stays there").isEqualTo(63);
    }

    @Test
    @DisplayName("Hitting a ceiling drops the rise to $01 instead of reversing it")
    void ceilingContactSetsYVelocityToOne() {
        // Prepare - a ceiling directly above the resting head, so the first little hop meets it at once
        solidRow(CEILING_ROW);
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();

        // Execute - tick 1 launches the hop, tick 2 runs into the ceiling
        tick(paraGoomba, 1);
        assertThat(paraGoomba.getVelocity().y()).as("Launched").isEqualTo(LITTLE_HOP_YVEL);
        tick(paraGoomba, 1);

        // Verify - DetStat & $08 overwrites whatever the move and the float left behind
        assertThat(paraGoomba.isCeilingContact()).isTrue();
        assertThat(paraGoomba.getVelocity().y()).as("YVel = $01").isEqualTo(1);
        assertThat(paraGoomba.getPosition().y())
            .as("Head stopped flush under the ceiling tile")
            .isCloseTo(REST_ROW_PIXEL_Y, within(TOLERANCE));
    }

    @Test
    @DisplayName("About-faces at a wall without ever entering it, keeping facing as the one direction")
    void wallContactFlipsFacing() {
        // Prepare - a wall two tiles to the left of a Para-Goomba walking left into it
        final int wallColumn = START_COLUMN - 2;
        solidColumn(wallColumn);
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();
        assertThat(paraGoomba.isFacingRight()).as("An unflipped ground troop walks left").isFalse();

        // Execute - 80 ticks is 40 px, far more than the 16 px of clearance to the wall's face
        tick(paraGoomba, 80);

        // Verify - Object_FlipFace flips the bit only; XVel is reloaded from it every frame, so the
        // next step is already rightward without anything negating a velocity
        assertThat(paraGoomba.isFacingRight()).as("Turned around").isTrue();
        assertThat(paraGoomba.getVelocity().x()).as("Walking right at GroundTroop_XVel").isEqualTo(8);
        assertThat(paraGoomba.getPosition().x())
            .as("Never overlaps the wall: its left edge stays at or past the wall's right edge")
            .isGreaterThanOrEqualTo((wallColumn + 1) * (double) TILE_SPRITE_SIZE);
    }

    @Test
    @DisplayName("A panel top is no ceiling: the hop passes straight through it")
    void aPanelTopIsNotACeiling() {
        // Prepare - the same geometry as the ceiling test, but the row is a jump-through panel. The ROM
        // takes the ceiling test from Tile_AttrTable+4 (dasm prg000 @ PRG000_C59A), the tier the panels
        // sit below, so to an object rising into one it is thin air - exactly what the player sees.
        panelRow(CEILING_ROW);
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();

        // Execute - the ceiling test would have caught the hop on its second tick
        tick(paraGoomba, 2);

        // Verify
        assertThat(paraGoomba.isCeilingContact()).as("Nothing to bump into").isFalse();
        assertThat(paraGoomba.getPosition().y())
            .as("Rose past the panel's own row instead of stopping under it")
            .isLessThan(REST_ROW_PIXEL_Y);
    }

    @Test
    @DisplayName("A panel top is no wall either: it walks through the column, not into it")
    void aPanelTopIsNotAWall() {
        // Prepare - the wall test reads the same Tile_AttrTable+4 tier (@ PRG000_C559)
        final int panelColumn = START_COLUMN - 2;
        for (int row = 0; row < ROWS; row++) {
            solids[row][panelColumn] = true;
            oneWayPlatforms[row][panelColumn] = true;
        }
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();
        assertThat(paraGoomba.isFacingRight()).as("An unflipped ground troop walks left").isFalse();

        // Execute
        tick(paraGoomba, 80);

        // Verify
        assertThat(paraGoomba.isFacingRight()).as("Never turned around").isFalse();
        assertThat(paraGoomba.getPosition().x())
            .as("Walked on past the panel column")
            .isLessThan(panelColumn * (double) TILE_SPRITE_SIZE);
    }

    @Test
    @DisplayName("A panel top is still a floor: it lands on one it falls onto")
    void aPanelTopIsStillAFloor() {
        // Prepare - no real floor at all, just a panel a few rows below where it starts
        removeFloor();
        final int panelRow = FLOOR_ROW;
        panelRow(panelRow);
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW - 4);
        paraGoomba.faceClosestPlayer();

        // Execute
        tickUntilGrounded(paraGoomba, 200);

        // Verify - Object_HitGround aligns the feet to the panel's top, from the Tile_AttrTable tier
        assertThat(paraGoomba.isGrounded()).isTrue();
        assertThat(paraGoomba.getPosition().y())
            .as("Resting with its feet on the panel")
            .isCloseTo((panelRow - 1) * (double) TILE_SPRITE_SIZE, within(TOLERANCE));
    }

    @Test
    @DisplayName("Re-faces the player on the 64th tick, but only because that tick is a grounded one")
    void refacesThePlayerEverySixtyFourGroundedTicks() {
        // Prepare - a one-tile corridor, so the Para-Goomba touches the floor on most ticks and the
        // 64th lands inside the big hop's cooldown. The player only appears after it is already walking
        // left, so a turn can only have come from the 64-tick re-face.
        solidRow(CEILING_ROW);
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();
        assertThat(paraGoomba.isFacingRight()).isFalse();
        givenPlayerAtPixelX((COLUMNS - 2) * (double) TILE_SPRITE_SIZE);

        // Execute
        tick(paraGoomba, 63);

        // Verify - Var5 & $3F is non-zero on every one of those ticks
        assertThat(paraGoomba.isFacingRight()).as("Still walking away after 63 ticks").isFalse();

        // Execute - the 64th
        tick(paraGoomba, 1);

        // Verify
        assertThat(paraGoomba.isFacingRight()).as("GroundTroop_FlipTowardsPlayer on the beat").isTrue();
    }

    @Test
    @DisplayName("A 64-tick beat that falls mid-flight is ignored, and the next grounded one is taken")
    void doesNotRefaceWhileAirborne() {
        // Prepare - ride the big hop, which is long enough in the air to swallow a whole beat, and only
        // then put a player on the far side. Var5 keeps ticking through the flight, so a turn before
        // landing could only have come from the clock alone.
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        paraGoomba.faceClosestPlayer();
        tickUntilLaunchOf(paraGoomba, BIG_HOP_YVEL, 300);
        givenPlayerAtPixelX((COLUMNS - 2) * (double) TILE_SPRITE_SIZE);

        // Execute - up to and onto the next beat, still mid-flight
        tick(paraGoomba, REFACE_PERIOD_TICKS - paraGoomba.getRefaceClock() % REFACE_PERIOD_TICKS);

        // Verify - the beat landed, but DetStat & $04 was clear, so nothing happened
        assertThat(paraGoomba.getRefaceClock() % REFACE_PERIOD_TICKS).as("On the beat").isZero();
        assertThat(paraGoomba.isGrounded()).as("And in mid-air for it").isFalse();
        assertThat(paraGoomba.isFacingRight()).as("So it kept walking away").isFalse();

        // Execute - land, then run on to the next beat, which this time is a grounded one
        tickUntilGrounded(paraGoomba, 100);
        tick(paraGoomba, REFACE_PERIOD_TICKS - paraGoomba.getRefaceClock() % REFACE_PERIOD_TICKS);

        // Verify
        assertThat(paraGoomba.isFacingRight()).as("Turned towards the player on a grounded beat").isTrue();
    }

    @Test
    @DisplayName("It does not bump off other objects, unlike the ground Goomba")
    void doesNotBounceOffOtherObjects() {
        // Prepare - dasm prg000.asm:413 gives OBJ_PARAGOOMBA plain OAT_BOUNDBOX01, where OBJ_GOOMBA at
        // :412 is OAT_BOUNDBOX01 | OAT_BOUNCEOFFOTHERS. Two Para-Goombas walk straight through one
        // another; the broadphase only steers objects that opt in on both sides.
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        final Goomba goomba = new Goomba(gameEngine, Offset.of(START_COLUMN, REST_ROW));

        // Execute & Verify
        assertThat(paraGoomba.bouncesOffOtherObjects()).isFalse();
        assertThat(goomba.bouncesOffOtherObjects()).as("Where the ground Goomba does").isTrue();
    }

    @Test
    @DisplayName("Its collision box is the Goomba's inset box, and stomp depth still measures the sprite top")
    void collisionBoxMatchesTheGoombas() {
        // Prepare - the same Object_BoundBox row 01 both objects select
        final ParaGoomba paraGoomba = paraGoombaAt(START_COLUMN, REST_ROW);
        final Goomba goomba = new Goomba(gameEngine, Offset.of(START_COLUMN, REST_ROW));
        goomba.init();

        // Verify
        assertThat(paraGoomba.getBounds()).isEqualTo(goomba.getBounds());
        assertThat(paraGoomba.getStompReferenceTop())
            .as("Measured from Objects_Y, not from the inset box top")
            .isCloseTo(paraGoomba.getPosition().y(), within(TOLERANCE));
        assertThat(paraGoomba.getBounds().top())
            .as("The box really is inset, so the two are genuinely different")
            .isGreaterThan(paraGoomba.getStompReferenceTop());
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /**
     * Ticks and collects the Y velocity of every hop launch. A launch is a grounded tick that leaves the
     * Para-Goomba rising; a grounded tick spent waiting out the cooldown leaves it falling instead.
     */
    private List<Integer> recordHopLaunches(final ParaGoomba paraGoomba, final int ticks) {
        final List<Integer> launches = new ArrayList<>();
        for (int tick = 0; tick < ticks; tick++) {
            paraGoomba.motionUpdate();
            if (paraGoomba.isGroundContact() && paraGoomba.getVelocity().y() < 0) {
                launches.add(paraGoomba.getVelocity().y());
            }
        }
        return launches;
    }

    /**
     * Ticks until the next launch of the given strength.
     *
     * @return the number of ticks it took
     */
    private int tickUntilLaunchOf(
        final ParaGoomba paraGoomba,
        final int hopYVelocity,
        final int limit
    ) {
        for (int tick = 1; tick <= limit; tick++) {
            paraGoomba.motionUpdate();
            if (paraGoomba.isGroundContact() && paraGoomba.getVelocity().y() == hopYVelocity) {
                return tick;
            }
        }
        throw new AssertionError("No hop of " + hopYVelocity + " within " + limit + " ticks");
    }

    /** @return the highest point (smallest Y) reached over the next {@code ticks} ticks */
    private double apexOverNextTicks(final ParaGoomba paraGoomba, final int ticks) {
        double apex = paraGoomba.getPosition().y();
        for (int tick = 0; tick < ticks; tick++) {
            paraGoomba.motionUpdate();
            apex = min(apex, paraGoomba.getPosition().y());
        }
        return apex;
    }

    /** Ticks until the Para-Goomba is back on the ground. */
    private void tickUntilGrounded(final ParaGoomba paraGoomba, final int limit) {
        for (int tick = 0; tick < limit; tick++) {
            paraGoomba.motionUpdate();
            if (paraGoomba.isGrounded()) {
                return;
            }
        }
        throw new AssertionError("Never landed within " + limit + " ticks");
    }

    private ParaGoomba paraGoombaAt(final int column, final int row) {
        final ParaGoomba paraGoomba = new ParaGoomba(gameEngine, Offset.of(column, row));
        paraGoomba.init();
        return paraGoomba;
    }

    private void tick(final ParaGoomba paraGoomba, final int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            paraGoomba.motionUpdate();
        }
    }

    private void givenPlayerAtPixelX(final double pixelX) {
        final PlayerPosition position = new PlayerPosition();
        position.setX(pixelX);
        position.setY(REST_ROW_PIXEL_Y);

        final LevelScenePlayer player = mock(LevelScenePlayer.class);
        when(player.getPosition()).thenReturn(position);
        when(collisionGrid.findClosestPlayerTo(anyDouble(), anyDouble())).thenReturn(Optional.of(player));
    }

    private void solidRow(final int row) {
        for (int column = 0; column < COLUMNS; column++) {
            solids[row][column] = true;
        }
    }

    /** A jump-through row: stood on from above, thin air to heads and sides (a {@code PANEL_WALKABLE_TOP}). */
    private void panelRow(final int row) {
        for (int column = 0; column < COLUMNS; column++) {
            solids[row][column] = true;
            oneWayPlatforms[row][column] = true;
        }
    }

    private void solidColumn(final int column) {
        for (int row = 0; row < ROWS; row++) {
            solids[row][column] = true;
        }
    }

    private void removeFloor() {
        for (int column = 0; column < COLUMNS; column++) {
            solids[FLOOR_ROW][column] = false;
        }
    }

    /**
     * Mirrors {@code StaticEnvironmentCollisionGrid#isGroundSolidTile} — the ROM's
     * {@code Tile_AttrTable[0..3]} tier: outside the level is solid, sky is not, and a jump-through
     * panel counts.
     */
    private boolean isSolid(final int column, final int row) {
        if (row < 0) {
            return false;
        }
        if (row >= ROWS || column < 0 || column >= COLUMNS) {
            return true;
        }
        return solids[row][column];
    }

    /**
     * Mirrors {@code StaticEnvironmentCollisionGrid#isWallOrCeilingSolidTile} — the ROM's
     * {@code Tile_AttrTable[4..7]} tier, which the jump-through panels sit below.
     */
    private boolean isSolidToHeadAndWalls(final int column, final int row) {
        if (row < 0 || row >= ROWS || column < 0 || column >= COLUMNS) {
            return isSolid(column, row);
        }
        return solids[row][column] && !oneWayPlatforms[row][column];
    }
}
