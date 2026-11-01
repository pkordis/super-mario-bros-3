package house.x1337.app.smb3.game.object.level.enemy.family;

import house.x1337.app.smb3.game.object.level.enemy.base.GroundWalkingEnemy;

import static house.x1337.app.smb3.GameConstants.TILE_SPRITE_SIZE;

public interface GoombaFamilyEnemy extends GroundWalkingEnemy {
    int BIG_HOP_COOLDOWN_TICKS = 80;
    int BIG_HOP_Y_VELOCITY_FIXED_POINT = -48;
    int BODY_RIGHT_SPRITE_OFFSET_PIXELS = TILE_SPRITE_SIZE / 2;
    int CEILING_BOUNCE_Y_VELOCITY_FIXED_POINT = 1;
    int HOPS_PER_CYCLE = 4;
    int LITTLE_HOP_Y_VELOCITY_FIXED_POINT = -16;
    int OBJECT_CLOCK_WRAP = 255;
    int REFACE_PERIOD_MASK = 63;
    int RISING_WING_FLAP_BONUS = 2;
    int SPRITE_SIZE_PIXELS = TILE_SPRITE_SIZE;
    int WING_FLAP_PHASE_BIT = 8;
    int WING_LIFT_FIXED_POINT = 1;
    int WING_LIFT_PIXELS = 10;
    int WING_OUTWARD_OFFSET_PIXELS = 2;
}
