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
are the in-level card, used by the runtime card (SMB3-44). The editor does not draw them.

`src/main/resources/hud/card/{mushroom,flower,star}.png` are the status-bar cards (SMB3-43), different art: see
`kb/sprites_extraction.md` §9 for how they were extracted. `LevelCompletionCardType` carries both paths per card.

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

## Held cards and the status bar (SMB3-43)

- **ROM storage:** `Inventory_Cards` (smb3.asm:2729), three bytes per player: `0` empty, `1` Mushroom, `2` Flower,
  `3` Star. The in-level card passes its frame + 1 (prg002.asm:6251-6253); `Player_GetCard`'s header comment, which
  says 0-2, is wrong. The cards are not reset between levels.
- **ROM adding:** `Player_GetCard` (prg031.asm:2883-2924) puts the card in the first empty slot. With all three full
  it copies slot 2 into 1 and slot 3 into 2, then writes the card into slot 3. It has no side effects; the status-bar
  redraw is `Player_GetCardAndUpdate`'s fall-through into `StatusBar_Update_Cards`. The in-level card calls
  `Player_GetCard` directly and checks for three matching cards right after (prg002.asm:6270-6290).
- **ROM drawing:** `StatusBar_DrawCardPiece` (prg031.asm:2810-2876) writes four BG tiles per slot from `CardUL`,
  `CardUR`, `CardLL`, `CardLR` (prg031.asm:2761-2764): empty `` x 4, Mushroom `-`, Flower
  `,,,` (`` is skipped), Star `-`. The slots start at nametable columns 22, 25 and 28 of rows
  25-26 (`CardVStartU`/`L`), which are x = 168, 192, 216 and y = 16 in `hud_base.png`.
- **Port:**
  - `enumeration.LevelCompletionCardType`: `MUSHROOM`, `FLOWER`, `STAR` in frame order; the ROM's stored value is
    `ordinal() + 1`.
  - `PlayerData`: three slots, `null` for empty. `addCard` follows `Player_GetCard`, `clearCards` empties them,
    `getCards()` returns an unmodifiable copy. `PlayerData.State` holds the cards, so a change re-renders the HUD
    and an unchanged set (a fourth Flower rotating into three Flowers) does not.
  - `PlayerData` belongs to the tester session's `GameEngine` and survives `buildLevel`, so the cards survive R,
    deaths and pipe trips. A new tester session starts with none.
  - `HeadsUpDisplayFontRenderer` loads the three status-bar PNGs into `CARD_CACHE`;
    `HeadsUpDisplayRenderer.renderCards` blits each held card over its box. An empty slot keeps the base image's box,
    which is already the `` tiles.
- **Not yet:** collecting a card (SMB3-44), and clearing the cards after a level with three plus the three-card bonus.
