# Invisible blocks (SMB3-92)

A reward block whose level object data has `invisible = true` is neither drawn nor solid until it is revealed. It is a
question block in every other respect: same reward, same bounce, and an empty block afterwards.

## What it does

| Contact | Result |
|---|---|
| Player's head, while rising | Revealed: dispenses its reward, becomes the empty block (drawn, solid) and bounces. The rise stops as under a question block. |
| Tail attack | Revealed the same way. |
| Anything else | The block does not exist: walking into it, landing on it, falling or standing in its cell, enemies, items, a kicked shell. |

The flag is read by `Block.configure` from `LevelObjectData.getBoolean`, which takes a boolean or the text `true` that
the editor's data table stores. It is honoured on the question block, the reward brick and the switch spawners. On a
note block or a breakable brick it is logged and disregarded.

## How it is built

- `LevelObject.isHidden()` is the one question the rest of the code asks. `Block` answers with the flag and clears it
  when it is struck, so the replaced object no longer claims to be hidden.
- Not drawn: the collision grid does not register a hidden object with its animator
  (`StaticEnvironmentCollisionGridCapabilities.toCollisionGrid`), and the cell the level baked from the tile is erased:
  `GameEngine.buildLevel` calls `StaticEnvironmentCollisionGrid.eraseHiddenObjects` once the grid exists, which is after
  the layers are baked. It clears the layer that painted the tile.
  The editor still shows the tile, which is how an author finds the block.
- Not solid: `Block.isCollidable()` is false while hidden, so the player's probes, the walkers' and the items' tile
  tests all read the cell as open.
- Head hit: a rising, airborne player has no solid tile at the head probe, so `StaticEnvironmentCollisionGrid`
  (`isHeadInHiddenObject`) treats a hidden object at that probe as a ceiling for that tick. The normal ceiling path
  then stops the rise and calls `onCollisionFromBelow`. A falling or grounded player never strikes it.
- Tail: `resolveTailAttack` looks the tile up regardless of solidity, so `Block.onTailAttack` is reached unchanged.
- Kicked shell: `Block.onKickedObjectStrike` returns early while hidden.

## Original game

- Invisible coin block, invisible 1-up and invisible note sit below the first solid tile of their quadrant in every
  tileset attribute table, so every solidity check reads them as air. Objects use the same table.
- Bump handling treats them as "only when the player moves upward" entries that answer to hit-enable bit `%0010`, the
  head probe. When the player rises the two ground probes both read the head point (6 px below the sprite top, centre
  column), and the side probes carry other bits, so only the head hits. Moving down or at velocity 0 they are not
  considered.
- The tail attack and a kicked shell enter the same routine with the all-bits slot, so both reveal it whatever the
  player's velocity. **This port reveals it for the head and the tail only: a shell passes through** (ticket decision).
- The original's results: invisible coin block gives a coin, a 1-up or nothing and leaves the metal empty block, the
  invisible note leaves a normal note block. Here the contents come from the record's `reward`, as for a question block.
  The invisible note block and the coin-heaven note are not implemented (see `note-block.md`).
- A bump is skipped while another bounce is running, as for every block.
- Sources: prg030 `Tile_Attributes_TS*`, prg008 `Level_DoBumpBlocks`, `Level_ActionTiles*`, `LATP_*`/`LATR_*`,
  `Player_TailAttack_HitBlocks`, `Object_BumpOffBlocks`, `TileAttrAndQuad_OffsFlat`.

## Limits

- Background tile substitution of the original does not apply here.
