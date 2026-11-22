"""Back up the stored level scenes, then clear the enemy part tiles from their NPC layer.

Since SMB3-84 an enemy is stored as a placement in the NPC layer's enemyPlacements list (stamp id plus pixel
position), not as part tiles in its tileIds. The game no longer spawns enemies from tiles, so the old part tiles
would stay as plain pictures. This clears them (every NPC cell holding a tile of type ENEMY_PART becomes 0, the empty
cell) so the enemies can be placed again in the editor. Any other tile in the NPC layer is left alone.

The connection comes from the project's Spring properties: src/main/resources/application.properties, overridden by
the git-ignored application-local.properties next to it (host, port, database, username, password, and the
level-scenes and tiles collection names). The user is authenticated against the database itself, as the editor does.

By default the script only lists what it would clear. Pass --apply to clear it: the scene documents it changes are
first written to a JSON backup (extended JSON, every field kept), and nothing is written if the backup fails.
Pass --restore FILE to put the scenes in a backup back as they were.

Needs pymongo:
    pip install pymongo

Usage:
    python db/clear_npc_enemy_tiles.py                            # lists what it would clear
    python db/clear_npc_enemy_tiles.py --title W01-L02-S01        # only that scene (repeatable)
    python db/clear_npc_enemy_tiles.py --apply                    # backs up, then clears
    python db/clear_npc_enemy_tiles.py --apply --backup-dir D:/backups
    python db/clear_npc_enemy_tiles.py --restore ~/smb3-db-backups/levelScenes-20261121-220000.json
"""
import argparse
import datetime
import pathlib

NPC_LAYER = "NON_PLAYABLE_CHARACTERS"
ENEMY_PART = "ENEMY_PART"
EMPTY_CELL = 0
RESOURCES = pathlib.Path(__file__).resolve().parents[2] / "src" / "main" / "resources"
DEFAULT_BACKUP_DIR = pathlib.Path.home() / "smb3-db-backups"


def read_properties(path):
    properties = {}
    if not path.exists():
        return properties
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        properties[key.strip()] = value.strip()
    return properties


def connection_settings():
    properties = read_properties(RESOURCES / "application.properties")
    properties.update({k: v for k, v in read_properties(RESOURCES / "application-local.properties").items() if v})
    prefix = "spring.data.mongodb."
    settings = {
        "host": properties.get(prefix + "host", ""),
        "port": int(properties.get(prefix + "port", "27017") or 27017),
        "database": properties.get(prefix + "database", ""),
        "username": properties.get(prefix + "username", ""),
        "password": properties.get(prefix + "password", ""),
        "scenes": properties.get(prefix + "collection.level-scenes", "levelScenes") or "levelScenes",
        "tiles": properties.get(prefix + "collection.tiles", "tiles") or "tiles",
    }
    missing = [name for name in ("host", "database", "username", "password") if not settings[name]]
    if missing:
        raise SystemExit("Missing Mongo settings in the properties files: " + ", ".join(missing))
    return settings


def enemy_part_ids(tiles):
    ids = set()
    for tile in tiles.find({"type": ENEMY_PART}, {"_id": 1, "id": 1}):
        ids.add(int(tile.get("id", tile["_id"])))
    return ids


def npc_tile_ids(scene):
    layer = (scene.get("layers") or {}).get(NPC_LAYER) or {}
    return list(layer.get("tileIds") or [])


def plan(scenes, titles, part_ids):
    """The scenes whose NPC layer holds enemy part tiles, with the cleared tile ids and what was cleared."""
    planned = []
    query = {"title": {"$in": titles}} if titles else {}
    for scene in scenes.find(query):
        tile_ids = npc_tile_ids(scene)
        cleared = [EMPTY_CELL if tile_id in part_ids else tile_id for tile_id in tile_ids]
        count = sum(1 for before, after in zip(tile_ids, cleared) if before != after)
        if count:
            kept = sum(1 for tile_id in cleared if tile_id != EMPTY_CELL)
            planned.append((scene, cleared, count, kept))
    return planned


def backup(documents, backup_dir):
    from bson import json_util

    backup_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    path = backup_dir / f"levelScenes-{stamp}.json"
    path.write_text(
        json_util.dumps(documents, json_options=json_util.CANONICAL_JSON_OPTIONS, indent=2),
        encoding="utf-8",
    )
    return path


def restore(scenes, path):
    from bson import json_util

    documents = json_util.loads(pathlib.Path(path).expanduser().read_text(encoding="utf-8"))
    for document in documents:
        scenes.replace_one({"_id": document["_id"]}, document, upsert=True)
        print(f"  restored {document.get('title')!r} (_id={document['_id']})")
    print(f"Restored {len(documents)} scene(s) from {path}.")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--apply", action="store_true", help="back up, then clear the tiles")
    parser.add_argument("--title", action="append", default=[], help="only the scene with this title (repeatable)")
    parser.add_argument("--backup-dir", type=pathlib.Path, default=DEFAULT_BACKUP_DIR,
                        help=f"where --apply writes the backup (default {DEFAULT_BACKUP_DIR})")
    parser.add_argument("--restore", metavar="FILE", help="put the scenes in this backup back, and do nothing else")
    args = parser.parse_args()

    try:
        from pymongo import MongoClient
    except ImportError:
        raise SystemExit("pymongo is not installed: pip install pymongo")

    settings = connection_settings()
    client = MongoClient(
        host=settings["host"],
        port=settings["port"],
        username=settings["username"],
        password=settings["password"],
        authSource=settings["database"],
    )
    try:
        database = client[settings["database"]]
        scenes = database[settings["scenes"]]
        print(f"{settings['database']}.{settings['scenes']} on {settings['host']}:{settings['port']}")
        if args.restore:
            restore(scenes, args.restore)
            return

        planned = plan(scenes, args.title, enemy_part_ids(database[settings["tiles"]]))
        if not planned:
            print("No NPC layer holds enemy part tiles. Nothing to do.")
            return
        for scene, _, count, kept in planned:
            print(f"  {scene.get('title')!r} (_id={scene['_id']}): {count} enemy part cell(s) to clear, "
                  f"{kept} other NPC tile(s) kept")
        if not args.apply:
            print(f"{len(planned)} scene(s) would change. Run again with --apply to back them up and clear them.")
            return

        path = backup([scene for scene, _, _, _ in planned], args.backup_dir.expanduser())
        print(f"Backed up {len(planned)} scene(s) to {path}")
        for scene, cleared, count, _ in planned:
            scenes.update_one({"_id": scene["_id"]}, {"$set": {f"layers.{NPC_LAYER}.tileIds": cleared}})
            print(f"  cleared {count} cell(s) in {scene.get('title')!r}")
        print(f"Done. Restore with: python db/clear_npc_enemy_tiles.py --restore \"{path}\"")
    finally:
        client.close()


if __name__ == "__main__":
    main()
