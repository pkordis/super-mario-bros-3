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
| `ui.editor.level.*` | Everything that only works on level scenes |

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
Tiles                                empty for now (SMB3-75 removed Configure Virtual Tiles...)
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

## 7. Known gaps

- Test Level Scene can start a second tester session while one runs, by switching back to a level scene tab; the
  editor then holds two tester tabs' worth of state (`LevelSceneTabs` keeps only the last tester tab).
- Growing a scene leaves the new Air cells empty; it does not extend an imported scene's sky.
