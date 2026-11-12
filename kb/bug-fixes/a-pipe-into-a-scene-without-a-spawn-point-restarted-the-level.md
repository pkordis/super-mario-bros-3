# A pipe into a scene without a spawn point restarted the level

Found during SMB3-68 review.

## Symptom

A pipe linked to another scene took the player back to the start of the current scene, as if the portal had no
link. The portal records in the database were intact.

## Cause

The destination (`W01-L01-S02`, a room reached only by pipe) has no spawn point. Re-saving it to set its background
music wrote the record with `spawnPoint` absent, since the editor tab holds an unplaced marker as `null`.

- The pipe trip builds the destination with `GameEngine.buildLevel(carryOver(...))`, which spawns a new
  `LevelScenePlayer`.
- Its constructor calls `LevelScenePlayerCapabilities.initializePosition`, which read `getSpawnPoint().x()` and threw
  a `NullPointerException`.
- `PipePortalTransit.arrive` catches any exception, logs "Pipe trip to portal ... failed; restarting the level" and
  restarts the original scene.

The music feature only exposed it: any save of a scene without a spawn point did the same.

## Fix

`initializePosition` starts the player at the scene's top-left when there is no spawn point. The pipe exit places
the player straight after the build, so that spot is never seen. The editor's test button still requires a spawn
point, since a scene started directly needs one.

Regression test: `PlayerPipeExitingTest.aSceneWithoutASpawnPointStillBuildsThePlayer`.
