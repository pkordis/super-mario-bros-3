# Sound

Sound is pre-recorded `.ogg` files played through jME's own audio (OpenAL, `jme3-lwjgl3`), decoded by
`jme3-jogg`. This replaced the abandoned plan to emulate the APU and port the sound engine (SMB3-51 and its
children, closed as won't do). First wired in SMB3-67.

## Files

- `src/main/resources/sound/music/` (32 files): the songs plus the short jingles (hurry up, course clear,
  player down, ...). `MusicTrack` has one constant per file.
- `src/main/resources/sound/effects/` (28 files). `SoundEffect` has one constant per file.
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

## `SoundPlayer`

`house.x1337.app.smb3.game.sound.SoundPlayer`, a `@Singleton` reached with `getBean(SoundPlayer.class)`.

- `play(SoundEffect)`: one `AudioNode` per effect, `playInstance()` each time, so effects overlap (themselves
  included) and never stop the music.
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
- `play(MusicTrack)` / `stopMusic()`: one track at a time, looping. `DataType.Stream` in jME means a stream with the
  stream cache on (`CachedOggStream`), which is seekable, so the loop can rewind. An uncached stream cannot loop.
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
  verify `playSource` / `playSourceInstance` / `stopSource` (see `SoundPlayerTest`). Reset it to `null` after.

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
  (`ALAudioRenderer.playSourceInstance`).
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

| Sound | Where | Condition |
|---|---|---|
| `SoundEffect.JUMP` | `LevelScenePlayerMoveCapable.handleVerticalMovement` | A jump press that starts a jump from the ground. A mid-air press (tail wag) plays nothing. |

`LevelScenePlayer` takes its `SoundPlayer` from the bean factory in its constructor and exposes it as
`getSoundPlayer()`. Tests that build a real `LevelScenePlayer` under a mocked `StaticBeanFactory` get `null` for it
unless the mock answers with mocks by default (as `PlayerRunOffTest` does) or stubs `SoundPlayer`. Stub it before
driving a ground jump.
