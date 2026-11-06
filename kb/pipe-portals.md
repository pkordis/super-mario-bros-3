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
