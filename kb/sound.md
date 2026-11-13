# Sound

Sound is pre-recorded `.ogg` files played through jME's own audio (OpenAL, `jme3-lwjgl3`), decoded by
`jme3-jogg`. This replaced the abandoned plan to emulate the APU and port the sound engine (SMB3-51 and its
children, closed as won't do). First wired in SMB3-67.

## Files

- `src/main/resources/sound/music/` (32 files): the songs plus the short jingles (hurry up, course clear,
  player down, ...). `MusicTrack` has one constant per file. Each constant also has a label, whether it loops, and a
  `GameContext`: the eight world map songs are `WORLD_*` constants (`WORLD_1_GRASS_LAND`, ...) in `MAP`; the file names
  stay `grass_land.ogg` and so on. Every other track is in `LEVEL_SCENE`.
- `src/main/resources/sound/effects/` (30 files). `SoundEffect` has one constant per file. `power_on.ogg` and
  `tail_wag.ogg` were added by the developer after SMB3-67, outside the numbered download. `twirl.ogg` is the world
  map's sound (`MAP`); the raccoon's tail plays `tail_wag.ogg`.
- Source: the developer's download in `~/Desktop/SMB3_resources/sounds`, named `NN - Koji Kondo - <Title>.ogg`.
  The rename table (track number to file name) is in the SMB3-67 description. Tracks 01-32 are music, 33-60 effects.
- `SoundAssetsTest` keeps the enums and the folders one to one.

What the files are (measured in SMB3-67):

- All 60 are Ogg **Vorbis**, stereo, 44 100 Hz. jME cannot decode Opus, so any file added later must be Vorbis too.
- The songs are 180 s each; the jingles 3.8-7.4 s.

### What the effect files contain (measured)

Measured on the PCM that jME's own decoder produces (the exact data handed to OpenAL), with
`dev-toolbox/audio/analyze_waveform.py`:

- **Lead-in:** every effect starts with 7-18 ms of exact digital silence, usually about 15 ms. The jump's first
  sample is at 15.4 ms, and it is at full level (-12 dBFS) within 1 ms after that.
- **Tail:** about 1 s of silence after the sound. The jump's tone ends at 549 ms of its 1683 ms.
- **A recorded pop** in `jump`, `super_jump` and `koopa_shell`: a separate 13 ms burst after the sound, 118 ms after
  the jump's tone (667-680 ms) and at 234 ms in the shell sound. It peaks at -26 dBFS: a step to about +0.05 full
  scale that decays smoothly back to zero. This is the faint click heard after the jump. It is in the recording,
  not in playback. The decoded length matches the Ogg's own sample count exactly, the file ends in exact zeros
  with no DC offset, and a lossy decoder cannot produce a burst out of a stretch of exact zeros.
- Trailing silence and the pops are cut when the effects load (see `SoundEffectTrimmer` below).
- **`power_on`** (the full P-meter) is different: 133.5 ms (5887 frames), Vorbis stereo 44 100 Hz, a steady tone at
  about -20 dBFS from the first sample to the last, with no lead-in and no tail. It is one cycle cut to loop, so
  the trimmer leaves it whole and the loop has no gap.

## `SoundPlayer`

`house.x1337.app.smb3.game.sound.SoundPlayer`, a `@Singleton` reached with `getBean(SoundPlayer.class)`.

- `play(SoundEffect)`: a new non-positional `AudioNode` copy of the effect's node each time (sharing its trimmed
  buffer), started with `play()`, so effects overlap (themselves included) and never stop the music. There is no
  channel or slot system: every sound overlaps every other. The copies sounding are kept (pruned of `Stopped` ones on
  each play) so a mute can stop them. Until SMB3-49 each play was `playInstance()` on the effect's one node, which
  jME cannot stop: `AudioNode.stop()` only stops what `play()` started, and `ALAudioRenderer.playSourceInstance`
  never binds the node to its channel, so an instance only ends when its buffer does.
  - **Exception: an effect that does not overlap itself** (`SoundEffect.isSelfOverlapping()` false; only `TAIL_WAG`
    today) plays on its loaded node with `play()`, and a play is ignored while that node's status is `Playing`. jME's
    `ALAudioRenderer.updateInRenderThread` sets a bound buffer node back to `Stopped` when the buffer finishes.
  - Why the tail wag (cross-checked in the original): the game asks for the wag sound on every new A press in the
    air and every B press that starts a swipe, with no timer. The original's sound engine drops a new wag request
    while the previous one still plays, and the request is lost, since the sound queues are cleared every frame. The
    wag sounds for 18 frames and ends on the 19th. Mashing therefore gives one wag per clip length, an even rhythm,
    instead of one per press. `tail_wag.ogg` trims to 0.320 s (19.2 frames), so the clip's own length gives the same
    spacing. A longer clip would not help: it would only space the sounds further apart.
  - Not copied: in the original the wag also shares a channel with the coin, item-rise, vine, cannon, text blip,
    power-up and 1-up sounds, so it is skipped while one of those plays and cut off when one starts.
- `loop(SoundEffect)` / `stopLoop(SoundEffect)`: at most one loop per effect, on its own non-positional node
  (`setLooping(true)`, `play()`) that shares the loaded effect's trimmed buffer. Both are no-ops when there is
  nothing to do (already looping, not looping, no engine), so callers can call one of them every tick. The loops
  are dropped with the effects on detach.
- `muteEffectsExcept(SoundEffect...)` / `unmuteEffects()` (SMB3-49): the mute stops every effect still sounding at
  once (the loops, the copies, the tail wag's node) and, until the unmute, `play` and `loop` do nothing for every
  effect but the ones named. The music is not muted. The level completion mutes on the touch with `CASINO_BEEP` let
  through; `GameEngine.buildLevel` unmutes on every run start, and `attach` (through `detach`) on every engine.
  `PowerMeterSound` needs nothing of its own: its `loop(POWER_ON)` is simply ignored while muted.
- **Every effect is loaded when an engine attaches**, not on its first play: decoded (`AudioKey` with
  `stream = false`), trimmed by `SoundEffectTrimmer`, and wrapped in a node. All 28 take about 256 ms in a cold JVM
  at engine start. Loading on first play instead cost the first jump about 50 ms.
- **`SoundEffectTrimmer`** keeps the PCM from the first sounding sample to the last one. A sample is silent at a
  magnitude of 10 or less (about -70 dBFS). Sounding stretches closer than 20 ms are one stretch. It also drops a
  pop: a final stretch of at most 20 ms that follows at least 50 ms of silence, repeated while one is left.
  - On the real files this removes the pops in `jump`, `super_jump` and `koopa_shell` (the shell's comes 84 ms
    after its second kick, hence the 50 ms gap rule), and nothing else. The power-down's three notes, 70 ms apart,
    stay.
  - The jump went from 1683 ms to 534 ms and now starts on its first sample. Its first 2 ms are the decoder's
    -57 dB pre-echo, then full level.
  - The original `.ogg` files are untouched, and new effects are trimmed the same way.
- `play(MusicTrack)` / `stopMusic()`: one track at a time, looped unless `MusicTrack.isLooping()` is false (the seven
  jingles). `DataType.Stream` in jME means a stream with the stream cache on (`CachedOggStream`), which is seekable,
  so the loop can rewind. An uncached stream cannot loop. `getCurrentTrack()` is the track last started, until
  `stopMusic()` or the engine stops; a jingle that has played out is still returned. Without an engine it stays null.
- **The end of a jingle** is published as `SoundPlayComplete(track)` from `update(timePerFrame)`, which
  `GameEngine.simpleUpdate` calls once per frame, before the frame's ticks, on the engine thread. The jingle in
  progress is a small `PlayingJingle`:
  - `AudibleJingle` (an audio renderer): ended once its node's status is `Stopped`. The renderer sets `Playing` in
    `playSource` and `Stopped` when the stream runs out.
  - `SilentJingle` (no renderer: headless engine, OpenAL off): ended once its length has passed, counted from the
    frame times given to `update`. The length is read by `OggTrackLength` from the file itself: the granule position
    of its last Ogg page (the sample-frame count) over the sample rate in the Vorbis identification header, cached
    per file. An unreadable file counts as 0 s, so a wait on it ends at once rather than never. `hurry_up.ogg` reads
    2.784 s, `player_down.ogg` and `course_clear.ogg` 4.238 s.
  - `stopMusic()`, or playing another track, drops the jingle in progress, so a jingle cut short publishes nothing.
    A looping track never gets one.
- **Lifecycle.** `GameEngine.simpleInitApp` calls `attach(this)`, which drops anything kept for a previous engine
  and subscribes to `GameEngineStopped` to drop this engine's nodes again. Nothing is stopped on detach: the
  engine's audio renderer stops its sources when it shuts down, and the event may arrive on a thread with no audio
  context. The bus is purged on `destroy()`, so the subscription is renewed by every `attach`.
- **No audio renderer** (headless context, `settings.setAudioRenderer(null)`): `attach` logs once and every call
  is a no-op. jME 3.6.1 itself only turns audio off quietly when the OpenAL native library fails to load
  (`ALAudioRenderer.initOpenAL` catches `UnsatisfiedLinkError` and sets `audioDisabled`; later play calls are
  ignored). Any other failure to open a device is not caught there. Nothing in the port handles that case yet.

jME rules that bite:

- `AudioNode` defaults to **positional**, and a positional node refuses stereo data with
  `IllegalStateException("Only mono audio is supported for positional audio nodes")`. Every node is made
  non-positional.
- jME finds the audio renderer through a thread-local (`AudioContext`) that is set on the engine's update thread
  only. Play calls must come from gameplay code on that thread, never from Swing.
- Tests can install a mocked `AudioRenderer` with `AudioContext.setAudioRenderer(...)` on the test thread and
  verify `playSource` / `stopSource` (see `SoundPlayerTest`). Reset it to `null` after. A mocked renderer never sets
  a node's status, so a test sets `Playing` itself on the nodes it wants a mute to find sounding.

## Latency (measured)

From the jump press to the jump being heard, before (SMB3-67 first cut) and after the fixes:

| Source | Before | After | Notes |
|---|---|---|---|
| Simulation tick | 0-17 ms | 0-17 ms | The press is read on the next 60 Hz tick. The jump's sprite waits for the same tick. |
| First play of an effect | ~50 ms, once | 0 | Effects are now loaded when the engine attaches. |
| Lead-in in the file | ~15 ms | 0 | Trimmed by `SoundEffectTrimmer`. |
| OpenAL Soft output buffer | **60 ms** | **22 ms** | Set up from inside the process by `OpenAlSoftConfiguration` (below). |
| Windows audio engine and hardware | not measured | not measured | Outside what OpenAL reports. Bluetooth adds roughly 100-200 ms. |

The renderer is OpenAL Soft 1.23.0, bundled with LWJGL 3.3.2, at 48 kHz with `ALC_REFRESH` 50. That means 20 ms
mixing periods, about three of them queued.

- jME's `AudioNode.playInstance` adds nothing: it binds the buffer and calls `alSourcePlay` straight away
  (`ALAudioRenderer.playSourceInstance`). `play()` on a fresh node, which effects use since SMB3-49, does the same.
- **The OpenAL buffer can be shrunk with an OpenAL Soft config file.** With `period_size = 480` and `periods = 2`
  under `[general]`, the measured source latency drops from 60 ms to **22 ms**. `period_size = 240` measured the
  same 22 ms, which is probably the Windows shared-mode floor.
  - OpenAL Soft reads that config only from a file: the one named by the `ALSOFT_CONF` environment variable, or a
    per-user `%APPDATA%\alsoft.ini` (which would apply to every OpenAL Soft program on the machine). It reads it
    once, when the library loads.
  - Asking at runtime does not work: `alcResetDeviceSOFT` with `ALC_REFRESH` 100, 200 or 400 left the refresh at 50.
- **What the project does: `OpenAlSoftConfiguration`, entirely inside the process.** `GameEngine.start()` calls
  `apply()` just before `super.start()`, which opens the audio device.
  - It writes the two settings to a temp file (`smb3-openal-*.ini`, deleted on exit).
  - It sets `ALSOFT_CONF` to that file in the process's own environment, through `NativeEnvironment`.
  - It runs once per process. A variable already set when the JVM started is left alone, so a config of one's own
    wins.
  - No file, Maven setting or IDE setting is needed, so it works the same from the IDE, from Maven and from the fat
    jar. An earlier version used a project-root `alsoft.ini` plus the variable set by the launcher; it was dropped
    for this reason.
  - A failure is logged as a warning, and sound then plays on OpenAL's defaults.
- **Why setting the variable from inside the process works.** `System.getenv` is a copy taken when the JVM starts,
  and Java has no setter. `NativeEnvironment.set` calls the OS through the Foreign Function API instead:
  `SetEnvironmentVariableW` (kernel32) on Windows, `setenv` (libc) elsewhere.
  - The `OpenAL.dll` LWJGL ships links the C runtime statically: it imports no `ucrtbase` / `api-ms-win-crt-*`.
    Its C runtime therefore copies the process environment (`GetEnvironmentStringsW`) only when the DLL loads,
    which is after the call. LWJGL loads the DLL when jME creates the device, and the variable stays set for every
    later engine in the same process.
  - Measured on two engines started one after the other in one JVM, with no variable set outside: refresh 100 and
    22 ms device latency on both.
  - `setenv` on Linux and macOS is not tried yet.
- **Native access.** The Foreign Function calls are restricted methods: the JDK warns now and will refuse them in a
  future release unless native access is enabled. `pom.xml` adds `Enable-Native-Access: ALL-UNNAMED` to the jar
  manifest (`maven-jar-plugin`), which the Spring Boot fat jar keeps. Runs from the IDE or from tests only print
  the warning, which LWJGL's own `System::load` already triggers.
  - If the JDK ever refuses, the `IllegalCallerException` is caught by `apply()`.
- The trade-off of the small buffer is a smaller safety margin: if the mixer thread misses a 10 ms period, the sound
  crackles. The fix then is `OpenAlSoftConfiguration.PERIODS = 3`, about 32 ms.

## Triggers

The mapping (SMB3-68) is a best guess from the file titles; tune it here and in the code together.

| Sound | Where | Condition |
|---|---|---|
| `JUMP` | `LevelScenePlayerMoveCapable.handleVerticalMovement` | A jump press that starts a jump from the ground. |
| `TAIL_WAG` | same method; `LevelScenePlayerActionCapable.handleTailAttack` | A mid-air press that wags the tail; a B press that starts a tail swipe. Not while the previous wag still sounds (see `play(SoundEffect)` above). (`TWIRL` belongs to the world map and is not played in a level.) |
| `ENEMY_STOMP` | `onCollisionFromAbove` of `Goomba`, `ParaGoomba`, `KoopaParaTroopa`, `KoopaTroopa` | Every stomp that bounces the player, including one that stops a kicked shell. |
| `KOOPA_SHELL` | `KoopaTroopaShellCapabilities.onKicked` | Every kick: on release, on contact, and the stomp on a resting shell (`kickWithoutPose`). Not when the kick crushes the shell; `crushUpward` plays it instead. |
| `KOOPA_SHELL` | `onKickedInto`, `onKilledByCarryContact`, each enemy's `onTailAttack`, `KoopaTroopa.onBumpedFromBelow` | One per enemy killed by a kicked shell, a carried shell or the tail, and a Koopa flipped by a bumped block. A ParaGoomba or KoopaParaTroopa delegates to its replacement, which plays it. |
| `BLOCK` | `LevelScenePlayer`, right after its collision pass | The head hits anything solid (`runtimeState.isHitCeiling()`). The reward sounds play on top. A super leaf and a P-switch have no sound of their own, so this bump is theirs. |
| `BLOCK` | `KoopaTroopaShellCapabilities.bumpOffBlocks` | A kicked shell rebounds off a wall (and so bumps any leaf out of the block it hits). |
| `BREAKING_BLOCK` | `BreakableBrickCapabilities.smashBrick` | Any brick broken. |
| `COIN` (+ `ONE_UP`) | `Coin.onCollisionWith`, `Block.onPlayerOverlap`, `Block.onCoinDispensed`, `CoinRewardMotionManager.spawnPoppedOutCoin` | Every coin. `PlayerData.addCoin()` returns true for the 100th, which also plays `ONE_UP`. The block coin plays outside the `whenCompleteAsync` callback, which runs on a pool thread. |
| `POWER_UP_BLOCK` | `RewardDispensingLevelObject.onSuperMushroomDispensed` | A mushroom (growing or 1-up, including the mushroom a leaf block gives a small player) comes out of a block. Not a leaf or a P-switch: `onSuperLeafDispensed` and `Block.onSwitchBlockDispensed` play nothing, and both are heard as the `BLOCK` bump. |
| `POWER_UP` / `TANOOKI` / `ONE_UP` | `LevelScenePlayer.onRewardConsumption` | `ONE_UP` for a 1-up mushroom, `TANOOKI` for the poof into a raccoon, `POWER_UP` otherwise (grow, points only). |
| `TANOOKI` / `POWER_DOWN` | `LevelScenePlayerRuntimeStateAware.getHurtBy` | Suit lost / shrink. |
| `POWER_DOWN` | `beginPipeEntry`, `beginPipeExit` | Into a pipe, and out of one when there is an exit to play (not for an erased pipe). |
| `CANNONBALL` | `SwitchBlock.onCollisionFromAbove` | The P-switch press ("Wham!"). |
| `CASINO_BEEP` | `LevelCompletion.stepTheTimeBonus` | Each time-bonus step. |
| `ONE_UP` | `EnemyMotionManager.postCollision`, `CoinRewardMotionManager.landPoppedOutCoin` | A kill-chain reward that carries a life. |
| `ONE_UP` | `LivesBonus.payOut` | Each life three cards pay out at the level's end (SMB3-48), the next one 48 ticks later, the 1-up sound's length. |
| `POWER_ON` (looped) | `PowerMeterSound` | Looped while the P-meter is full (the HUD's [P] flashes) and gameplay is not halted; stopped otherwise. A halt (death, pipe, suit change, blocking fade) silences it with the meter still full. It follows `PlayerPowerReached` / `PlayerPowerLost` and `GameplayHalted` / `GameplayResumed`. The card touch mutes it (SMB3-49), though the frozen meter stays full. |

**From the card touch to the level's end** (SMB3-49) only the course-clear jingle and the `CASINO_BEEP` steps are
heard, and from the lives payout on (SMB3-48) the `ONE_UP` of each life too (`muteEffectsExcept(CASINO_BEEP, ONE_UP)`):
every other effect is muted (`SoundPlayer.muteEffectsExcept`), the P ringing and the coins of the touch's
sweep included. This follows the original game as observed by the developer; dasm alone suggests the ringing would
go on over the jingle until the meter drained.

Not played: the skid (no file), the Venus Fire Trap's spit and picking up a shell (silent on
purpose), and everything belonging to features not built yet (pause, fireballs, vines, map, ...). A tail strike on
a block plays no bump (only `TAIL_WAG` at the swing's start), unlike the head; so a leaf or a P-switch knocked out
by the tail is silent. Adding a bump there must not go through `Block.onTailAttack`, which the kicked shell also reaches (through
`resolveTailAttack`) after playing its own `BLOCK`.

**The P-meter events.** `LevelScenePlayerDataAware` publishes `PlayerPowerReached` when the meter steps from 6 to 7
and `PlayerPowerLost` when a full meter drains a step or is emptied as its flight ends. `GameEngine.buildLevel`
publishes `PlayerPowerLost` too when a fresh start (`LevelRunStart.FRESH`) empties a full meter
(`PlayerData.resetPowerMeter()` returns whether it was full), so a death at full power leaves no loop behind. A pipe
trip keeps the charge, and the loop resumes on the `GameplayResumed` after the exit. `GameEngine` publishes
`GameplayHalted` / `GameplayResumed` on the tick `isGameplayHalted()` changes (`announceHaltChange`, in the level
branch of the tick, after the players have moved).

**How the sound player is reached.** Through `SoundPlayerAware.getSoundPlayer()`, a default method that returns
`getBean(SoundPlayer.class)`. `SoundPlayer` is a `@Singleton`, so `StaticBeanFactory` serves it from its singleton
cache after the first call: no class holds a sound player field. `SoundPlayerAware` is extended by the most base
types that play sounds, so nothing declares it directly any more:

- `GameEngineAware`: every level object (blocks, coins, the P-switch, every enemy), `LevelScenePlayer` and
  `LevelCompletion`.
- `MotionManager`: `CoinRewardMotionManager` and the enemy motion managers.
- `Player`: `MapPlayer`, which reaches neither of the other two.

`LevelMusic` and `PowerMeterSound` are the exception: they are built with their `SoundPlayer` (constructor
injection), which their tests hand a mock.

The head bump is played by `LevelScenePlayer`, right after its collision pass, when `runtimeState.isHitCeiling()`
is set. The collision grid plays nothing.

**Tests.** The sound player is looked up on every call, so a test must hand out the same mock every time:
`staticBeanFactory.when(() -> getBean(SoundPlayer.class)).thenReturn(soundPlayer)`, or a default answer that returns
one shared `soundPlayer` for `SoundPlayer.class` (a default answer of `mock(type)` would give each call a new mock,
and `verify(x.getSoundPlayer())` would check one that never played). A `mockStatic` without a default answer returns
`null`, which NPEs on the first sound. Verify on the object (`verify(goomba.getSoundPlayer()).play(ENEMY_STOMP)`) or
on the shared mock. A mocked `LevelScenePlayer` needs a `getSoundPlayer()` stub only when the test runs the player's
own code, such as `getHurtBy` or `handleVerticalMovement`.

## Level music

`house.x1337.app.smb3.game.sound.LevelMusic` (`@Singleton`) decides what plays during a level. It runs on events
only: it is not ticked, and is not a `MotionManager`.

- **What the level wants:** `P_SWITCH` while the P-switch window is open, otherwise the scene's `backgroundMusic`,
  otherwise silence.
- **Every build.** `GameEngine.buildLevel(LevelRunStart)` ends with `levelMusic.start(levelScene, start)`, after the
  grid rebuild has closed any P-switch window. It cuts any jingle and reads the window. A fresh start (test, death
  restart, R, restart after completion) plays the wanted track from its start, or stops the music. A carried-over
  start (`LevelRunStart.isCarryOver()`, through a pipe) keeps the track when `getCurrentTrack()` already is the one
  wanted.
- **The P-switch is announced.** `SwitchBlock` publishes `PowerSwitchActivated` right after `activate()`;
  `StaticEnvironmentCollisionGrid.tick()` publishes `PowerSwitchExpired` when `PowerSwitchTimeWindow.tick()` returns
  true (the tick its count reaches 0). `reset()` publishes nothing: its two callers are the grid rebuild, after
  which `start` reads the window, and the card touch, whose jingle silences the level anyway (an event there would
  restart the scene's music for one tick before `COURSE_CLEAR` replaced it). A second press while the window is
  open does not restart the P-switch music. The window itself still counts its 512 ticks: it is a gameplay timer.
- **Jingles** stop the music and play once. Each ends on its own `SoundPlayComplete`; a track's end that is not the
  jingle in progress is ignored.
  - `PlayerTimerHurryUp`, published by `PlayerTimer.tick()` on the step that brings the time to 100 (never for a
    run starting at 100 or less, nor through `takeOff`): `HURRY_UP`, then what the level wants, from its start.
    `hurry_up.ogg` was trimmed to 2.784 s, where its music ends; the original carried a thump and about 2 s of
    200 Hz buzzing after 2.817 s.
  - `PlayerDeathStarted` (both kinds): `PLAYER_DOWN`, then silence until the rebuild. The death ends on its end
    (see `PlayerDeathSequence` in `dynamic-entities.md`), so the jingle is heard in full.
  - `LevelCardCollected`: `COURSE_CLEAR`, or `VICTORY_FANFARE` when the card makes three of a kind
    (`LevelCardCollected.isThreeOfAKind()`, SMB3-48), then silence. `LevelCompletion` lets the time bonus start on
    the end of whichever of the two played.
  - While a jingle plays, a P-switch change only decides what plays after it.
- `GameEngine.simpleInitApp` calls `subscribe()` once per engine, after `SoundPlayer.attach`, since the bus is
  purged on `destroy()`. `PowerMeterSound`, `PlayerDeathSequence` and the engine's `LevelCompletion` subscribe there
  too.

## Background music per scene

`LevelScene.backgroundMusic` / `LevelSceneRecord.backgroundMusic`, a nullable `MusicTrack`; null is no music, and
every scene saved before SMB3-68 loads as null. Both databases store the enum by name (Mongo's `PojoCodecProvider`,
Nitrite's Jackson mapper), so renaming or removing a `MusicTrack` constant breaks scenes that use it.

- Copied by `LevelSceneConverter` both ways and by `LevelSceneRecordNormalizer` (which rebuilds the record field by
  field on every save, so a field it misses is silently dropped).
- Editor: `LevelSceneEditorTab.backgroundMusic`, set by `LevelSceneEditorTabFactory.fromScene`, copied by
  `fromLevelSceneEditor` (so the tester plays an unsaved choice), cleared by `fromImport` and `resetData`.
- Save dialog: a "Background music:" row with a `BackgroundMusicComboBox` ("None", then the looping `LEVEL_SCENE`
  tracks in enum order: no jingles and no world map songs), labels from `MusicTrackCellRenderer`. A `JComboBox`
  cannot index a null item, so "None"
  shows as the selection but is not highlighted in the open list.
