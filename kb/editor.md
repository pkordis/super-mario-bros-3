# Game editor — Knowledge Base

How the editor window is put together, how it hosts one kind of editor per tab, and the level scene features that
live in it (SMB3-72). The editor started as a level scene editor only; it is now a shell any editor (world maps
next) can plug into.

---

## 1. Shell versus level scene

| Package | Holds |
|---|---|
| `ui.editor` | `GameEditorWindow`, `EditorStartup` |
| `ui.editor.tab` | `EditorTabSystem`, `core.BaseEditorTab`, `core.BaseEditorTabSystem` (close buttons) |
| `ui.editor.menu` | `EditorMenuBar`, `FileMenu`, `FileMenuContribution`, `TilesMenu`, `GameEditorWindowMenuItem`, `file.SettingsMenuItem`/`SettingsWindow`/`ExitMenuItem` |
| `ui.editor.pane` | `EditorContentPane` (the window's content: palettes left, `MiddlePane` right), `MiddlePane` (tabs, plus the right area the tester puts its debugger in) |
| `ui.editor.toolbar` | `EditorToolBar`, `EditorToolBarSection`, `TestRunSection`, `RunButton`, `TestRunTarget` (see the toolbar, section 3) |
| `ui.editor.component` | Widgets with no level scene concept: `CustomDataTablePanel`, `WrapLayout`, `ScrollableFlowPanel`, `FontCapabilities` |
| `ui.editor.level.*` | Everything that only works on level scenes; `level.tile.edit` holds the tile form and its saving (section 7) |

- **Naming rule:** shell classes have no `LevelScene` prefix. Level scene classes keep it, or a level domain name;
  inside `ui.editor.level` the package already says they are level scene code, so names like `TilePalettePanel` or
  `ActiveLayerMenu` stay as they are.
- Still level-specific although shown for every tab: the palettes (`level.pane.LevelScenePalettesPane`) on the left
  of `EditorContentPane`. Making side panels follow the active tab type waits for a second editor.
- `runner/GameEditorRunner` shows the window. `GameEditorWindow.init()` runs every `EditorStartup` bean once the
  window is built; `LevelSceneTabs` uses it to reopen the last edited scene.

## 2. Tabs

- `EditorTabSystem` holds any `BaseEditorTab`. A tab gives its `resolveTitle()` and may override `onClose()`.
  - `addTab` installs the close button and selects the tab; `updateTitle(tab)` (via `tab.updateTabTitle()`) rewrites
    both the tab title and the label beside the close button.
  - `getActiveTab()` / `getActiveTab(Class)` return `Optional`; `getTabs(Class)` lists the tabs of one kind.
  - Closing (`closeTabAt`, also the close button) removes the tab first, then calls `onClose()`.
  - Removing the active tab always fires the change listeners (`removeTabAt` override). Plain `JTabbedPane` does not
    when the next tab slides into the same index, which would leave menus showing the closed tab's state.
- `level.tab.LevelSceneTabs` is the level scene view of the tabs:
  - `getActiveTab()` is the active `LevelSceneEditorTab` or `null` (tester tab, no tab); `hasActiveTab()`.
  - Tracking: a tab holding a stored scene is tracked by scene id, so `open(LevelScene)` focuses it instead of opening
    a second one. `SaveLevelSceneWindow` tracks a tab once its scene is saved. New and imported scenes
    (`openNew`, `openUnsaved`) stay untracked until then. `LevelSceneEditorTab.onClose()` calls `forget`.
  - The tester tab: `GameEngineTesterTab` (title "Level Scene Tester") stops its `LevelSceneTesterSession` in
    `onClose()`; `addTesterTab` / `removeTesterTab`.
  - `addActiveTabListener` and `enableOnlyWhileActive(component)` are how level scene menus and palettes follow the
    active tab.

## 3. Menus

```
File                                 rebuilt on every active-tab change
  Save Level Scene      Ctrl+S       only while a level scene tab is active
  ---
  Settings...
  ---
  Exit
Tiles
  Handle Unresolved...                every stored tile without a type, in the review window (SMB3-79)
Level
  Scene >
    New Level Scene...  Ctrl+N
    Browse Scenes...
    Import from PNG...  Ctrl+I
    ---
    Clear Level Scene   Ctrl+Shift+C   disabled without an active level scene tab
    Set Size...                        disabled without an active level scene tab
    ---
    Active Layer >                     disabled without an active level scene tab
    Layer Visibility >                 disabled without an active level scene tab
    ---
    Test Level Scene    F9             disabled without an active level scene tab; also the toolbar's Run Level
  Objects >
    Edit Interactive > Single Tiled...
    ---
    Enemies > Create from Image...
    ---
    Create Custom...
    ---
    Pipe Portal Linkage
```

- **Rule:** only what the active tab can use is available. File *hides* items that do not apply; Level > Scene
  *disables* them. That is why their click handlers no longer check for a missing tab.
- **File contributions:** an editor adds File items with a `FileMenuContribution` bean (`appliesTo(activeTab)`,
  `getItems()`); `FileMenu` gets them all injected. The level scene one is `LevelSceneFileMenuContribution`
  (Save Level Scene). The menu is rebuilt on each active-tab change rather than when it opens, so `Ctrl+S` is only
  bound while Save is in the menu.
- **Plugging in another editor** (e.g. world maps): a `BaseEditorTab` subclass, a `FileMenuContribution` for its
  File items, its own root menu in `EditorMenuBar`, a `TestRunTarget` if its tabs can be run (see the toolbar
  below), and an `EditorStartup` if it reopens anything at start.

### Toolbar (SMB3-76)

A bar under the menu bar for the items used most, split into sections by intention. `GameEditorWindow` puts
`toolbar.EditorToolBar` (a `JToolBar`, not floatable) at the top of its content panel, `EditorContentPane` below it.

- **Sections:** each is an `EditorToolBarSection` bean (`getOrder()`, `getTools()`). The bar sorts them by order,
  lowest first, and puts a separator between each; there are no captions. Adding a section means adding one bean.
- **Test Run** (`TestRunSection`, order 0) holds `RunButton`, the only section so far. Planned follow-ups: File
  (Save), Scene (New, Browse, Set Size), Layers (an active-layer selector).
- **Run button:** runs the active tab through the first `TestRunTarget` bean that `handles` it, and takes that
  target's label ("Run Level") and a tooltip with the shortcut ("Run Level (F9)"). With no target for the active
  tab (no tab, the Level Scene Tester tab, any other kind) it reads "Run" and is disabled. It follows every
  active-tab change through `EditorTabSystem.addChangeListener`.
- **Run targets:** `TestRunTarget` (`handles(activeTab)`, `getLabel()`, `run(activeTab)`). The level scene one is
  `level.toolbar.LevelSceneTestRunTarget`, which holds what used to be in `TestLevelSceneMenuItem`: it refuses a
  scene without a rendering starter or a spawn point with a warning, otherwise starts a `LevelSceneTesterSession`.
  Level > Scene > Test Level Scene calls the same target. A world map editor adds a target labelled "Run Map".
- **F9** (`RunButton.SHORTCUT`) is bound on the Run button with `WHEN_IN_FOCUSED_WINDOW`, so it works anywhere in
  the window. Test Level Scene has it as its accelerator too, which shows it in the menu. Swing hands a window-wide
  key to component bindings before menu bar accelerators, so the button takes F9 and the menu item never runs a
  second time; a disabled button does not take it, and the menu item is disabled then as well.
- **Icon:** `src/main/resources/ui/toolbar/run.png`, a 24 x 24 green play triangle on a transparent background,
  read once when the button is built. Edit the PNG to restyle it; Swing greys it out itself while disabled.

## 4. Level scene size

`level.menu.scene.LevelSceneSizeRules` (the bounds), `LevelSceneSizeDialog` (columns and rows, shared by New and Set
Size), `LevelSceneResizeRefusal` (the error window).

- **Bounds:** at least the viewport, 16 columns by 15 rows; at most 65535 a side and 1,000,000 tiles in all. The dialog
  refuses anything else and says why. So the widest scene is 65535×15 and the tallest 16×62500.
- **Why the tile cap:** 65535×65535 is about 4.29 billion cells per layer. A saved layer is one flat `int[]` indexed by
  `row * columns + column`, and Java arrays and `int` indexes stop at about 2.1 billion; in memory it would be about
  17 GB per layer, six layers per scene. A million cells is about 24 MB of tile references.
- **New Level Scene...** asks for the size (16×15 filled in) and opens a blank "Untitled Scene" tab with every layer
  (`LevelSceneTabs.openNew` → `LevelSceneEditorTab.newScene(rows, columns)`).
- **Set Size...** starts from the active scene's size. The **bottom-left corner stays fixed**: rows are added or
  removed at the top, columns at the right (`LevelSceneEditorTab.resizeScene`). Every tile row, the spawn point, the
  rendering starter, the card and each pipe portal move down by the rows added (up by the rows removed), so nothing
  moves relative to the ground. The PNG import grid, when the tab has one, gets the same shift and crop.
- **A shrink over content is refused.** `LevelSceneEditorTab.findContentCutBy(columns, rows)` lists every cell in the
  area that would go: non-empty tiles per layer (the card and pipe portal cells count as the NPC layer's), then the
  spawn point (whichever of its two cells are cut) and the rendering starter, a group each. The error window lists them as `col=… row=…`, in the current
  coordinates, in a scrolling list; OK only closes it and the size stays.
  - An imported scene usually has its Air layer filled with the dominant sky tile everywhere
    (`LevelSceneImportRouter`), so shrinking one is refused until those Air tiles are erased.
- **Painting:** `LevelSceneEditorGrid.paintComponent` draws only the cells inside the graphics clip
  (`visibleCells(clip)`), grid lines included, so a large scene repaints as fast as the part on screen.

## 5. Clear Level Scene

Empties every layer and removes the spawn point, the rendering starter, the card and the pipe portals
(`LevelSceneEditorTab.clearScene`). The scene keeps its id, title, description, music, size and tracking, so saving
afterwards overwrites the stored scene. `newScene` still resets both content and identity. Before SMB3-72 Clear also
dropped the id, so saving created a second scene.

## 6. Spawn point and rendering starter

Since SMB3-75 both are virtual objects, like the level completion card and the pipe portals, and there are no
virtual tiles any more: `TileType` keeps only `NULL` (the empty cell) in the `VIRTUAL` category, and the VIRTUAL
palette group, Tiles > Configure Virtual Tiles... and its window are gone.

| `VirtualObjectType` | Colour | Rows x columns | Drawn as |
|---|---|---|---|
| `SPAWN_POINT` | `Color.GREEN` | 2 x 1 | the standing player (`SpawnPointSprite`) under a 4 px border |
| `RENDERING_STARTER` | `Color.BLUE` | 1 x 1 | a 4 px border |

- **One per scene, NPC layer only.** Like the card: a click on the NPC layer places it, placing it again moves it,
  a right-click on one of its cells removes it, and both are hidden with the NPC layer. Another active layer places
  and removes nothing. The palette buttons show the footprint only, no sprite.
- **The spawn point's cells.** The scene stores the spawn point as one cell, the player's upper cell:
  `LevelScenePlayerCapabilities.initializePosition` puts the player's Y at that cell's top edge, and the feet are
  32 px lower. So the box covers the stored cell and the one below it, which is where the player appears at the
  start. A click places the box's lower cell, as for every virtual object, so the stored cell is the clicked row
  minus one, and a click on the top row places nothing. Example: in a 27-row scene with ground on row 25, the
  author clicks row 24 and the scene stores row 23; the player starts on rows 23 and 24, feet on the ground.
- **Bottom-row spawn points are dropped on load.** A stored spawn point on the bottom row has no cell below it.
  `LevelSceneEditorTabFactory.fromScene` drops it (`LevelSceneEditorTab.spawnPointFits`), so Test Level Scene asks
  for a new one, and the next save persists the removal. The game itself still reads whatever is stored.
- **The sprite.** `SpawnPointSprite` reads `sprites/player/mario/level/normal/still.png` (24 x 32 px, the figure
  14 x 27 px on the left), trims it to its opaque pixels, mirrors it, and draws it at `TILE_SCALE`, centred and
  standing on the box's bottom edge. The border is drawn over it.
- **Right-click order** on a cell holding several: the card, the rendering starter, the spawn point, then a pipe
  portal, the order they are drawn top down.
- **Stored tile records.** The old virtual tiles were stored in the tile repository (ids 1 and 2, type by name),
  and a record whose type no longer exists cannot be read. `dev-toolbox/db/delete_virtual_tile_records.py`
  deletes them from Mongo; run it once before starting the editor on this code (see `kb/dev-toolbox.md`).

## 7. Editing tiles (SMB3-79)

- **Where.** Right click on a Tiles palette tile opens its context menu (`TileContextMenu.forTile`, set on each
  `TileButton` by `TilePalettePanel.addTile`), whose one item, Edit, opens `EditTileWindow` for that tile. Left click
  still arms the tile. Tiles > Handle Unresolved... opens `NewTilesReviewWindow` with
  `TileService.getUnresolvedTiles()`, every stored tile without a type, or says there is none.
- **One form.** `TilePropertiesForm` (ID, Type, Description, the `TileProcessingPanel` pixel editor, Set) serves both
  windows. Set is enabled only once a type is chosen. `changes()` hands back a `TileChanges` (type, description, the
  edited pixels). The review window puts its list ("Tiles Without a Type") beside the form and closes once the list is
  empty; the edit window shows the form alone, is modal, and closes on Set.
- **One save.** `TileEditing.save` applies the changes, stores the tile (`TileService.updateTile`), shows it in the
  palette as it now is (`TilePalettePanel.showTile`: out of its old group, which goes if it empties, back in with a
  fresh button under its type) and refreshes every open level scene tab: `refreshTile`, plus `reRouteImportedLayers`
  for the tabs whose PNG import holds the tile (re-routing rebuilds a tab's layers from its import, so it is not run
  on the others). The review window no longer needs the tab it was opened for. `TileWindows` opens every window and
  dialog of tile editing, so the rest can be tested without them.
- **The layer move.** When the new type's owning layer (`TileType.getLevelSceneLayerOwningType()`) differs from the
  old type's, each cell holding the tile in any other layer is cleared to `NULL_TILE` and the same cell of the new
  layer gets the tile, overwriting what it held. It runs on every stored scene (`LevelSceneService.moveTile`: each
  record normalized, rewritten and upserted with its `updatedAt` kept, so the scene reopened at start does not change)
  and on every open tab (`LevelSceneEditorTab.moveTile`). Before anything changes, the author confirms a message with
  both layers and the cells changed in saved scenes and in open tabs (`countTileCellsOutside`); cancelling changes
  nothing, not even the type. No confirmation when no cell would move. A type within the same layer moves nothing; a
  tile getting its first type moves nothing either, as unresolved tiles are never stored in a scene's layers.

### Linking tiles to level objects (SMB3-80)

- **Many tiles, one object.** A level object (`LevelObjectRecord`) has the id of the tile it was made from. Other
  tiles, such as the same block in another palette or with a few different pixels, can be linked to it through the
  nullable `TileRecord.levelObjectId`. Records stored before the field existed read it as `null`.
- **Lookup.** `LevelObjectService.findByTile` returns the linked record, and falls back to the record with the
  tile's own id when there is no link or the link names a missing record. `findAllByTiles` (used by the collision
  grid) and `getUnclassifiedIds` go through it. A single-tiled object is drawn with the pixels of the tile painted in
  its cell (`LevelObjectRecordCapabilities.toLevelObject(gameEngine, offset, tile)`), so each linked tile keeps its
  look.
- **Resolved.** A tile is unresolved (`Tile.isUnresolved`) when it has neither a type nor a link. A re-import of
  exactly the same pixels reuses the tile by `sha256`, so a linked tile never reaches the review window again, and
  Handle Unresolved skips it.
- **The form.** `TilePropertiesForm` shows the id and the pixel editor, then two sections separated by "or", each
  with a radio button heading it: Tile properties (type, description, Set) and Associate with existing level object
  (`LevelObjectAssociationPanel`: dropdown, description, data table, Save). Only the selected section is enabled. A
  linked tile opens on the second one. `TilePropertiesForm.save` saves from whichever is active.
- **The dropdown.** `- (none)`, then every single-tiled level object once (`getSingleTiledLevelObjects`, by type
  label, then id). Choosing one shows its description and data, both editable. Left as they are, the button reads
  Link; once either differs (`LevelObjectAssociationPanel.isModified`, data compared as the table's trimmed text), it
  reads Create New and Link. With None it reads Unlink, and is only enabled for a linked tile.
- **Saving** (`TileEditing.associate`). The tile keeps its description and takes the type of the level object's own
  tile, or Interactive Object - Single when that tile has none. Link stores the level object's id in the tile. Create
  New and Link stores a new level object under the tile's own id (the chosen object's type, the edited description
  and data) and links the tile to it; the chosen object is left as it was. If other tiles are linked to the record
  the new one would replace, the author confirms first. The layer move confirmation and everything after it work as
  for Set, and the new level object is only stored once both are confirmed. Set in Tile properties clears a link.

## 8. Enemies and objects on the grid (SMB3-83)

Nothing records which object owns which cell: a stamped enemy is just its part tiles in the NPC layer, and the save
format is unchanged. Every footprint is worked out from the layer data, as the game does.

### Removing an enemy

- **A right click on any part removes the whole enemy** (`LevelSceneEditorTab.eraseTile` → `removeEnemyAt`, NPC
  layer only, after the virtual objects). `EnemyStampService.findPlacementHolding(layer, cell)` looks, for every
  stamp holding the clicked tile at part (pc, pr), at the renderer cell `cell - (pc, pr) + renderingStarter`; a
  candidate counts when the layer holds the stamp's rendering starter part there. Every cell that still holds one of
  that placement's parts (`EnemyPlacement.heldCells`) becomes `NULL_TILE`, so the parts go from the layer data, the
  save, and the game's baked NPC layer.
- **Ties:** parts are de-duplicated by sha256, so two stamps can share a part, or a rendering starter. The placement
  whose parts are all in place wins (`EnemyPlacement.isWhole`, parts off the scene not counted), then the one with
  the most parts in place, then the first in `EnemyStampService.findAll` order.
- **Orphan parts** (no rendering starter part where it should be, e.g. left by a removal before SMB3-83) are not
  repaired; a right click clears that cell only. A click on an empty cell inside an enemy's grid removes nothing.
- **Shared with the game.** `EnemyLevelObject.erasePlacementFromBakedTexture` uses the same lookup
  (`findPlacementAt(layer, rendererCell)` and `heldCells`) to take a spawned enemy's parts out of the baked texture.
  Before SMB3-83 it took the first stamp with that rendering starter tile.

### Hover preview

`PlacementPreview`, owned by `LevelSceneEditorGrid`. While a palette item is selected, the item is drawn at 50%
alpha (`AlphaComposite.SRC_OVER`) on the cell under the pointer, exactly where a left click would place it:

| Selected | Shown | Layer |
|---|---|---|
| Tile | the tile on the cell | only one it `isPaintableOn` |
| Enemy | the assembled stamp (`EnemyTilesAssembler.assemble`, cached per stamp), its rendering starter part on the cell | NPC |
| Spawn point | the sprite and green border on the cell and the one above; nothing on the top row | NPC |
| Pipe portal | the red border, the cell as its lower-left one (top row `y - rows + 1`) | NPC |
| Card, rendering starter | the yellow / blue border on the cell | NPC |

- Nothing shows outside the scene, while the active layer is hidden, or once the pointer has left the grid
  (`mouseExited`). It is painted after the virtual objects and clipped to the scene.
- **Repaints:** `refreshPreview()` repaints the union of the area last painted and the new one, only. It runs when
  the hovered cell changes (`mouseMoved`, `mouseDragged`, `mousePressed`), when the selection changes
  (`PaletteSelectionService.addSelectionListener`, registered by the tab in `init` and removed in `onClose`), and when
  the active layer changes (`LevelSceneEditorTab.setActiveLayer`). Moving inside one cell repaints nothing.
- An enemy's ramp offset (SMB3-84) is not in the preview yet.

### Overlapping objects are refused

- **Objects:** enemies, by their whole grid, empty parts included (`EnemyPlacement.bounds`), on the NPC layer; and
  interactive objects (`TileType.isInteractiveObject`: `OBJECT_INTERACTIVE_SINGLE` / `_PART`), one cell each, on the
  Interactive Objects layer. A multi-tiled level object will block with its whole footprint once it can be placed.
- **The rule:** anything placed on one of those two layers, an enemy stamp or a tile, is refused when its footprint
  (the stamp's whole grid, or the one cell) overlaps an object of that layer (`LevelSceneEditorTab.refusesOverlap`).
  Enemies are found by `EnemyStampService.findPlacementsOverlapping(layer, area)`: for each stamp, every rendering
  starter cell whose grid could reach the area is checked for the stamp's rendering starter part. To replace an
  object, remove it first with a right click.
- **Not objects:** Air, the decoration layers and Static Environment still overwrite, and so do orphan enemy parts.
  Virtual objects keep their own rules (one per scene, `PipePortalPlacementRules`).
- **Feedback** (`BlockerFlash`): a 4 px border of black and yellow diagonal stripes (a `TexturePaint`, 4 px stripes)
  just inside each blocker's box, painted last. On for 250 ms, off for 250 ms, for 2 seconds, driven by a
  `javax.swing.Timer`; the blink state is read off the clock, so it stays exact whatever the timer's lag. A new
  refusal adds its blockers and restarts the 2 seconds.
- **Drags:** `mousePressed` starts a gesture. Within it, each blocker blinks at most once, and an object placed
  during the gesture (`LevelSceneEditorGrid.notePlacement`) never blinks: dragging an enemy across the grid is
  refused where it would overlap the copies just stamped, without flashing them.

## 9. Known gaps

- Test Level Scene can start a second tester session while one runs, by switching back to a level scene tab; the
  editor then holds two tester tabs' worth of state (`LevelSceneTabs` keeps only the last tester tab).
- Growing a scene leaves the new Air cells empty; it does not extend an imported scene's sky.
- A right-button drag probably paints instead of erasing: `MouseEvent.getButton()` is normally `NOBUTTON` during a
  drag (`SwingUtilities.isRightMouseButton(e)` would fix it). A right click only erases while a palette item is
  selected (`handle` returns early). Both noted on SMB3-83, not verified at runtime.
