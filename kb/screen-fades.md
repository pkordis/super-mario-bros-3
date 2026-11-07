# Screen Fades

The ROM has one palette fade engine (dasm prg026.asm:1997-2240) that every context reuses with different timing.
The port's counterpart is `game.engine.fade.FadingEngine` (SMB3-39).

## What the ROM does

- `Palette_PrepareFadeOut` / `Palette_PrepareFadeIn` load `Fade_Level = steps` and `Fade_Tick = framesPerStep`, over
  **all 32 palette entries**: the four background palettes (the status bar's included) and the four sprite palettes.
  The status bar fades with the level.
- `Palette_DoFadeOut` / `Palette_DoFadeIn` run once per frame and decrement `Fade_Tick` **before** testing it, so the
  steps land on frames `framesPerStep`, `2 × framesPerStep`, ...
- Out: each step subtracts `$10` from every entry, clamped to `$0F` black; four steps reach black from any colour.
- In: preparing writes `Pal_Data - $30` at once (three rows dark, black where that underflows); each step adds `$10`
  until the target, so the colours are back after three steps and the fourth changes nothing.
- `Palette_FadeIn` / `Palette_FadeOut` loop VSync + step until `Fade_Level == 0`, so **nothing else runs** during a
  fade. `HandleLevelJunction` waits one more VSync after a fade-out to commit the black palette, then turns the
  display off.

| Profile         | Steps × frames | Blocking | Source                                                     |
|-----------------|----------------|----------|------------------------------------------------------------|
| `STANDARD`      | 4 × 4          | yes      | prg026 `Palette_FadeIn` / `Palette_FadeOut`; junctions, level entry/exit, map, bonus, game over, 2P Vs |
| `TOAD_AND_KING` | 11 × 4         | no       | prg027 `Palette_*TK`; the King's room and letter (7 steps hold) |
| `TITLE`         | 4 × 5          | no       | prg024 `Title_PalFadeIn` (precomputed palettes)            |

Not ported yet: `FadeOut_Cancel` (the Spade game skips the next fade-out), the map's entry border wipe
(`Map_EnterLevel_Effect`), mono flashes.

## The port

- `FadeProfile` is an enum of the profiles above (`getSteps()`, `getFramesPerStep()`, `isBlocking()`); a new context
  with its own timing is a new constant.
- `FadingEngine` (`@Singleton`, no jME types): `fadeOut(profile, onComplete)`, `fadeIn(profile, onComplete)`,
  `tick()`, `getDarkness()`, `isFading()`, `isHaltingGameplay()`, `reset()`.
  - Darkness is the share of the four brightness rows the colours are down: out `min(4, steps done) / 4`, in
    `max(0, 3 - steps done) / 4`. A standard fade-out reads 0, ¼, ½, ¾, 1 on ticks 1-3, 4-7, 8-11, 12-15, 16+; a
    fade-in reads ¾ from the moment it starts, then ½, ¼, 0 from tick 12.
  - A fade-out completes on tick `steps × framesPerStep + 1` (the committing VSync) and the screen is then **held
    black** until `fadeIn` or `reset`. A fade-in completes on its last step.
  - `onComplete` runs once, inside `tick()`, after the state is settled, so it may start the next fade. Work that
    swaps scenes must be `enqueue`d, not done inside the tick.
  - Starting a fade replaces the running one; the replaced one's callback never runs.
- `GameEngine` ticks it **first** in each 60 Hz tick, outside the halt branch, and `isGameplayHalted()` includes
  `isHaltingGameplay()`: a blocking fade (running or held black) freezes the timer, the active objects and
  non-halting players. Score captions still advance through `MotionManager::updateWhileHalted`, as in other halts;
  the ROM runs nothing at all. `simpleInitApp` resets it, since the singleton outlives every engine.
  `GameEngine.getFadingEngine()` is how gameplay code reaches it.
- `FadeOverlayState` draws the darkness as a black `Unshaded` quad at that alpha, in a post view with **its own
  camera over the whole window** (the game camera stops above the HUD). The camera sets its own view direction
  rather than trusting the clone's. The quad is culled at darkness 0, and the node is settled after every change,
  since it is outside `rootNode`.
- **Post-view order** is creation order: `ForemostSpriteOverlay.prepare` builds the foremost sprites' view in
  `simpleInitApp`, then `FadeOverlayState` and `FrameCaptureState` build theirs when they initialise, in attach order.
  So the fade darkens the tail-flash sprites, and frame captures (key 9) record the fade.
- The hues are not exact: the NES darkens each entry within its own colour, a black overlay dims everything evenly.
  Exact colour would need a palette shader.
