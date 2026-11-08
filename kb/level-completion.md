# Level Completion

The end of a level: the end-level card the player touches, and what follows it. The feature is ticketed as
SMB3-42 to SMB3-49; this file grows with each of them.

## What the ROM does (dasm)

- The card is an ordinary level object, `OBJ_ENDLEVELCARD = $41` (smb3.asm), 16 × 16
  (`OA1_PAL1 | OA1_HEIGHT16 | OA1_WIDTH16`, prg002.asm:183), so it covers one tile.
- Its three faces are frames 0, 1 and 2: Mushroom, Flower, Star (`EndLevelCard_Untouched`, prg002.asm:5388-5406;
  `Player_GetCard`, prg031.asm:2883-2889).
- The Flower is drawn with sprite palette 2 and the other two with palette 1 (`EndLevelCard_AttributeByFrame`,
  prg002.asm:5336). The right half is the left half mirrored (`EndLevelCard_Draw`, prg002.asm:6294-6318).

## Assets

`src/main/resources/sprites/object/card/level_completion/{mushroom,flower,star}.png`, one 16 × 16 PNG per face. They
are the in-level card, used by the runtime card (SMB3-44). The status-bar cards are different art (SMB3-43). The
editor does not draw them.

## Editor placeholder (SMB3-42)

- **Type:** `VirtualObjectType.LEVEL_COMPLETION_CARD` (yellow, 1 × 1, "Level Completion Card"), of kind
  `VirtualObjectKind.LEVEL_COMPLETION_CARD`. The kind is what the editor dispatches on, so a card never reaches the pipe
  portal code (`PipePortalPlacementRules`, `PipePortalPrompt`, `PipePortal`). `TileType.LEVEL_COMPLETION` is
  unrelated: it is for the decoration tiles of the level-completion area.
- **Palette:** `VirtualObjectsPalettePanel` builds one button per type, so the card has its own yellow 1 × 1 stamp
  with no card-specific code.
- **Placing:** `LevelSceneEditorTab.paintTile` → `placeLevelCompletionCard`. NPC layer only, any cell inside the scene,
  no prompt and no rules. A scene has at most one card, so placing it again moves it. It may share a cell with a pipe
  portal.
- **Removing:** right-click on its cell with the NPC layer active. `eraseTile` tries the card before the pipe portals
  (the card is drawn on top), so on a shared cell the first right-click removes the card and the second the portal.
- **Drawing:** `LevelSceneEditorGrid.drawLevelCompletionCard`, a 4 px yellow border on its cell after the pipe
  portals, only while the NPC layer is visible. Like every 4 px editor marker it bleeds one antialiased pixel past
  the cell edge.
- **Storage:** like the spawn point, one `Coordinates` field on the scene (`x` = column, `y` = row) rather than a
  collection of its own.
  - The tab holds `levelCompletionCard`, `null` for none (`hasLevelCompletionCard()`); `resetData` (new and
    cleared scenes) and `fromImport` reset it.
  - `LevelScene` and `LevelSceneRecord` hold it as `Coordinates`, `null` for none. `LevelSceneEditorTabFactory`
    opens a scene whose card has a negative component with no card.
  - `LevelSceneConverter` copies it both ways, and `LevelSceneRecordNormalizer` must copy it too: it rebuilds the
    record field by field, so a field it forgets is dropped on every save.
  - Mongo stores it as a nested `levelCompletionCard: {x, y}` document (the driver's record codec). Scenes saved
    without a card have no such field and read as `null`.
- **Runtime:** nothing yet. SMB3-44 turns the placeholder into the card the player collects.
