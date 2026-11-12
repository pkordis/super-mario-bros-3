# Game settings — Knowledge Base

How generic game configuration is stored, edited and applied (SMB3-50), and the research behind each setting.

---

## 1. Architecture

| Concern | Type |
|---|---|
| The settings, one field each, initial value = hardcoded default | `model/settings/GameSettings` |
| Settings in effect, load at editor start, save | `service/GameSettingsService` (`@Singleton`) |
| Storage, one record for all settings | `repository/GameSettingsRepository` + `mongo/MongoGameSettingsRepository`, `nitrite/NitriteGameSettingsRepository` |
| Editor UI | File > Settings... = `ui/editor/level/menu/file/SettingsMenuItem` → modal `SettingsWindow` |
| Startup load | `LevelSceneEditorRunner.run()`, right after `ConfigurationService.loadPhysics()` |

- **Record:** collection `gameData`, one record `settings`, one field per setting named after it; enums stored by
  constant name, e.g. `{ _id: "settings", timerPace: "STANDARD" }`.
- **MongoDB** keys it on `_id: "settings"` and saves with `replaceOne(..., upsert)`. The collection name is
  `spring.data.mongodb.collection.game-data` (default `gameData`), bean `gameDataMongoCollection` in `MongoDBConfig`.
  `MongoDBConfig` now has **two** `MongoCollection<Document>` beans; repositories pick theirs by constructor parameter
  name (`configurationMongoCollection`, `gameDataMongoCollection`), which needs `-parameters` (on by default with the
  Spring Boot parent). `MongoGameDataCollectionWiringTest` guards this.
- **Nitrite cannot store `_id: "settings"`.** Verified on Nitrite 5.4.2: inserting a document whose `_id` is
  `"settings"` throws `InvalidIdException: Invalid _id found settings` — `_id` is reserved for Nitrite's own numeric
  ids. The record uses `id: "settings"` instead (same as the physics record's `id: "physics"`), and Nitrite's own
  underscore-prefixed bookkeeping fields (`_id` and the like) are filtered out on load.
- **Save writes the whole state**, defaults included, replacing the record (Mongo: replace; Nitrite: remove + insert),
  so a dropped setting does not linger.
- **Fallback is per field:** no record → all defaults; a missing field, an unknown enum name or a value of the wrong
  type → that setting's default, with a warning. Loading never fails the editor.
- **Threading:** the window saves on the Swing thread, the game reads on the jME thread. The service swaps a whole
  `GameSettings` through a `volatile` field and hands out copies (`getSettings()`), never changing one in place.

### Adding a setting

1. A field on `GameSettings` with its default as the initial value (and in `copy()` / the all-args constructor).
2. Read and write it in `GameSettingsService.toGameSettings` / `toFields`, under its own field name.
3. One row in `SettingsWindow.buildFormPanel()`, read in the constructor and written in `save()`.
4. The game code that uses it reads `GameSettingsService.getSettings()`.

Out of scope so far: the physics tuner's values stay in `configuration` / `physics`; there is no
`application.properties` or command-line override for game settings.

---

## 2. Settings

### 2.1 Timer pace — `timerPace` (`TimerPace.REAL_TIME` default, `STANDARD`)

The checkbox "Standard level timer: one step every 41 frames instead of every second".

- `REAL_TIME`: one step every `SIMULATION_FPS` (60) ticks, one real-world second per timer unit.
- `STANDARD`: one step every 41 ticks, the first step included — a 300 timer runs out about 32% sooner.

dasm evidence for the 41-frame pace:

- `StatusBar_Fill_Time` (prg026.asm:2700-2745) runs once per level frame, from `StatusBar_UpdateValues`
  (prg026.asm:3577-3582) in the level loop (prg030.asm:2531).
- It decrements `Level_TimerTick`; while the result is ≥ 0 (`BPL`) nothing else happens. When it goes negative it is
  reloaded with `$28` and the timer drops by 1: one step every `$28 + 1` = 41 frames.
- It does nothing while `Level_TimerEn` (bits 0-6) is set or `Player_InPipe` is non-zero (prg026.asm:2705-2708).
- `Level_TimerTick` is loaded with `$28` at level start (prg030.asm:592-594), so the first step also comes after 41
  frames.

Applied in `GameEngine.buildLevel` when the timer restarts (`start.restartTimer()`): a fresh start (new test session,
restart after a death) takes the pace saved last; a run carried through a pipe keeps the pace it started with.
`PlayerTimer` holds the pace (default `REAL_TIME`) and compares `frameTick` with `pace.getTicksPerStep()`.
