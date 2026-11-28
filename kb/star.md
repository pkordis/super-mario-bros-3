# Star (Starman) — Knowledge Base

SMB3-91. The star is a reward that question, brick and note blocks hold (`ItemType.STARMAN`). Collecting it makes
the player invincible for a few seconds. The behaviour is the original's, read from its player, object and sound
code; numbers are given in this project's units (pixels, 16ths of a pixel per frame, frames).

---

## 1. The star item

The star is its own item, `Starman` (`ItemType.STARMAN`, `LevelObjectTypeSingleTiled.STARMAN`, 1000 points), with
its own `StarmanMotionManager`. It shares what a power-up shares with the mushroom, not its class: the rise out of the
block and its clipping, the 16 collect protection frames, the terrain probes of `SlopeFollowingCapable` and the
`RewardLevelObject` collecting. `RewardDispensingLevelObject.onStarmanDispensed` puts it out of a block, above the
block or, for a note block landed on, below it. Unlike a leaf, a star is never swapped for a mushroom for a small player.

`StarmanAnimator` flips it between `sprites/reward/starman/star_normal.png` (black outline, white) and `star_red.png`
(red outline, pink) every 2 ticks while it moves.
| Phase | What happens |
|-------|--------------|
| Hold and rise | The mushroom's: 16 frames hidden in the block, then 15 px at 1 px every 3 frames (61 frames in all). |
| Launch | The frame the rise ends: X velocity 16 away from the player (1 px/frame, kept), Y velocity -56 (3.5 px/frame up). |
| Dropped out of the bottom | Y velocity 16 and the same X velocity, no hold, no rise. |
| Every tick | Gravity +3 per frame up to 64, then move. |
| Landing | Y velocity -64 (4 px/frame up), on every landing, flat or ramp. It never rests. |
| Ceiling | Moving up into a solid tile above its centre: Y velocity 4. |
| Wall | The leading edge at mid-height in a solid tile: X velocity reversed. |

A bumped block under it changes nothing: the landing's -64 replaces the -48 a mushroom gets.
It is collected after its 16 protection frames, and it is deleted off screen like the other power-ups.

Collecting it (`Starman.onCollisionWith`) adds its 1000 points and calls `LevelScenePlayer.startStarInvincibility()`.
It does not grow the player and plays no power-up sound: the star music takes over.

## 2. The invincibility

`PlayerRuntimeState.starInvincibilityCounter` starts at 224 and goes down by one on every second frame (odd frames of
the level's frame counter), so it lasts 447 to 448 frames, about 7.5 s. A new star sets it back to 224.

| Counter | Effect |
|---------|--------|
| 32 or more | The star music plays. The palette changes every frame. |
| reaches 32 | `StarInvincibilityMusicEnded`: the level's music (or the P-switch's) plays again. |
| below 32 | The last 64 frames: the palette changes every 4 frames. |
| 0 | Over. Palette 0. |

The state does not depend on the suit. While it runs:

- `isHurtable()` is false, so nothing hurts the player (enemies, projectiles, the piranha plants' fireballs).
- Touching an enemy kills it: `ActiveObjectGrid.dispatchDirectionalPlayerCollision` sends it to
  `onStruckByInvinciblePlayer`. An enemy that a rammed object can kill (`isKilledByRammedObject`) is struck as a kicked
  shell is, knocked away from the player at 8 sixteenths of a pixel per frame, for 100 points with the shell-kick
  sound. This comes before the stomp, so a jump on an enemy kills it and the player does not bounce. Anything else
  (a hidden piranha plant) gets the ordinary overlap, which the invincible player shrugs off.
- A death or the end-level card ends it (`LevelScenePlayer.endStarInvincibility`), without a music cue of their own
  to wait for: the death and the card play their jingles.

Not carried over: a pipe into another scene keeps the counter, but `LevelMusic.start` begins the new scene with the
scene's own music, so the star music is not resumed there.

## 3. Music

`LevelMusic` wants `P_SWITCH` while the P-switch window is open, otherwise `STAR` while the star's music runs,
otherwise the scene's track. `StarInvincibilityStarted` (every collection, so a second star restarts the track) and
`StarInvincibilityMusicEnded` (the counter reaching 32, a death, the card) drive it. When the P-switch ends the star
music comes back only if the music has not ended meanwhile.

## 4. Colours

The player sprites have three colours: red (clothes), skin and black (hair, outline, shoes). The star cycles through
four palettes; `StarSpritePalette` makes a recoloured copy of a sprite texture once and swaps it into the quads
(`LevelScenePlayer.advanceAnimation` calls it after the animator, so the swap works whatever the animator did):

| Palette | Red becomes | Skin becomes | Black becomes |
|---------|-------------|--------------|---------------|
| 0 | red | skin | black |
| 1 | black | white | red |
| 2 | black | white | green |
| 3 | black | skin | amber |

The palette is chosen by the level frame counter: `counter & 3` while the counter is 32 or more, `(counter & 15) >> 2`
below that (`PlayerRuntimeState.starPalette`). Palette 0 is among them, so the normal colours show a quarter of the
time, and at the end a quarter of the time slowly. Green and white are the colours of the other reward sprites;
amber is (252, 152, 56).

## 5. The air flip

A jump started during the invincibility sets `airFlipping` (`LevelScenePlayerMoveCapable.handleVerticalMovement`) when
the player is not small, is not carrying something and the P-meter is not full. It ends on landing. Small Mario keeps
his jump pose.

`BaseLevelScenePlayerAnimator.renderSomersault` draws it, before any other air pose, in `NormalAnimator` and
`RaccoonAnimator`: with `beat = frameCounter >> 1`, the frame is `somersault[beat & 3]`, and for `beat & 7 >= 4`
the sprite is turned upside down. It is mirrored when moving left, and mirrored once more in the upside-down half, so
the roll keeps its direction. The 24 px wide frames are centred on the 16 px body.

## 6. Sprites

`dev-toolbox/image/generate_star_sprites.py` writes `sprites/reward/starman/star_normal.png`, `star_red.png` and the four
`somersault_N.png` of the normal and raccoon suits from the CHR banks; `extract_player_frame.py` renders a single
player frame (checked pixel for pixel against `jumping.png` of both suits). The four somersault frames are six
8x16 sprites each, three across and two down, from the suit's bank at page offset 1 (bank 85 for big Mario, bank 1
for raccoon). The star is the sprite pair from bank 4, mirrored, in two palettes.

## 7. Not done

The star from the world map, the big question block, the coin-or-star block (a coin unless the player is already
invincible), the goomba's shoe and the frog and statue suits, which have no counterpart here.
