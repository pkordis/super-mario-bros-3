# Pipe Portals

A **pipe portal** is an invisible marker over a pipe's lip that makes the pipe a way in (`IN`), a way out (`OUT`), or
both (`IN_AND_OUT`). It replaces the ROM's per-tileset enterable-pipe tile ids (`TILE1_PIPETB1..4`, `TILE3_PIPETB5`,
`TILE1_PIPEH1_B`, gated by `PipeTile_EnableByTileset`, dasm prg008.asm:6097).

## Model

- `VirtualObjectType` gives the footprint (`rows` × `columns`), the editor border `colour` and a `label`:
  - `PIPE_PORTAL_HORIZONTAL`: 1 × 2, the lip of an upright or upside-down pipe.
  - `PIPE_PORTAL_VERTICAL`: 2 × 1, the lip of a side pipe.
- `PipePortal` (`model.game.portal`) holds `id`, `description`, `type` (`PipePortalType`), `virtualObjectType`,
  `levelSceneId`, `row` and `column`.
- **Anchor:** `row` / `column` is always the portal's **lowest leftmost** cell. Row 0 is the top of the scene, so the
  footprint covers rows `row - rows + 1` to `row` and columns `column` to `column + columns - 1`.
  `PipePortalCapabilities.cells()` / `occupies()` / `overlaps()` work this out; nothing else should.
- A portal is never written into a layer's `Tile[][]`. It belongs to the NPC layer only in the editor's sense: that
  is the layer it is placed and drawn on.

## Placement rules

`game.level.pipe.PipePortalPlacementRules`: every cell inside the scene, every cell over a `PIPE_TERMINATION_PART`
tile on the static environment layer (`PIPE_BODY_PART` does not count), and no overlap with another portal.
`validate(...)` returns the first broken rule as a sentence for the editor. The runtime re-checks
`isOverPipeTermination` because the pipe can be erased after the portal was placed.

## Storage

- One `PipePortalRecord` per portal in the `pipePortals` collection (Mongo and Nitrite), keyed to its scene by
  `levelSceneId`. Portals are **not** embedded in the `levelScenes` document.
- `LevelSceneService` carries them with the scene:
  - `save` calls `PipePortalService.replaceAll(sceneId, portals)`: delete the scene's records, then insert the
    current list. Removing a portal in the editor therefore deletes its record on the next save.
  - `findLastEdited` and `findPage` attach each scene's portals. `findPage` must do this too, because the browse
    window's inline title/description edit saves the scene it holds, and an empty list would wipe its portals.
  - `delete` deletes the scene's portals.
- The converter writes the id of the scene being saved onto each record, not the portal's own `levelSceneId`, so a
  portal placed before the scene's first save still lands under the right scene.

## Editor

- **Palette:** the third `LeftPane` tab, `VirtualObjectsPalettePanel`, holds one `VirtualObjectButton` per
  `VirtualObjectType`. The stamp is the real footprint at `TILE_SIZE` (64 px) per cell, transparent, with a 4 px
  border in the type's colour. Buttons are `LayerGatedToggleButton`s owned by the NPC layer, so arming one switches to
  that layer, and leaving it releases the stamp. `ActiveLayerMenu.activate` re-syncs this panel too.
- **Placing:** `LevelSceneEditorTab.paintTile` checks an armed virtual object first. The clicked cell is the anchor.
  `PipePortalPlacementRules.validate` runs before anything is asked; a broken rule is shown through
  `PipePortalPrompt.warn`. Otherwise `PipePortalPrompt.ask` opens `PlacePipePortalWindow` (type defaults to `IN`,
  description optional) and **Place** adds the portal to `tab.pipePortals`.
- **No drag placement:** `LevelSceneEditorGrid` ignores drags while a virtual object is armed, or a drag would open a
  window per cell.
- **Removing:** right-click with the NPC layer active removes the portal under the cursor and leaves the NPC tiles
  alone; elsewhere right-click erases as usual.
- **Dialogs behind a bean:** the tab reaches the window and the warning only through `PipePortalPrompt`
  (`getBean`), so tests stub it instead of opening Swing windows.
- **Drawing:** after the tiles, grid lines and markers, each portal gets a 4 px rectangle in its colour around all its
  cells. Portals are drawn only while the NPC layer is visible.
- **Tab ↔ scene:** `LevelSceneEditorTabFactory.fromLevelSceneEditor` / `fromScene` copy the list both ways (a fresh
  list each time, so tab edits never leak into a loaded scene). `newScene` and `clearScene` empty it.
