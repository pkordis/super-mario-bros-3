"""Delete the stored Rendering Starter and Spawn Point tile records from the Mongo tiles collection.

Those two used to be tile types. They are virtual objects now (SMB3-75), and a stored tile whose type no longer
exists cannot be read back, so the editor fails to start until they are gone. Run this once per database, before
starting the editor on the new code.

The connection comes from the project's Spring properties: src/main/resources/application.properties, overridden by
the git-ignored application-local.properties next to it (host, port, database, username, password, tiles collection).
The user is authenticated against the database itself, as the editor does.

By default the script only lists what it would delete. Pass --delete to delete it.

Needs pymongo:
    pip install pymongo

Usage:
    python db/delete_virtual_tile_records.py
    python db/delete_virtual_tile_records.py --delete
"""
import argparse
import pathlib

RETIRED_TYPES = ["RENDERING_STARTER", "SPAWN_POINT"]
RESOURCES = pathlib.Path(__file__).resolve().parents[2] / "src" / "main" / "resources"


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
        "collection": properties.get(prefix + "collection.tiles", "tiles") or "tiles",
    }
    missing = [name for name in ("host", "database", "username", "password") if not settings[name]]
    if missing:
        raise SystemExit("Missing Mongo settings in the properties files: " + ", ".join(missing))
    return settings


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--delete", action="store_true", help="delete the records instead of only listing them")
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
        tiles = client[settings["database"]][settings["collection"]]
        query = {"type": {"$in": RETIRED_TYPES}}
        found = list(tiles.find(query, {"_id": 1, "type": 1}))
        print(f"{settings['database']}.{settings['collection']} on {settings['host']}:{settings['port']}")
        if not found:
            print("No retired tile records found. Nothing to do.")
            return
        for record in found:
            print(f"  _id={record['_id']} type={record['type']}")
        if not args.delete:
            print(f"{len(found)} record(s) would be deleted. Run again with --delete to delete them.")
            return
        result = tiles.delete_many(query)
        print(f"Deleted {result.deleted_count} record(s).")
    finally:
        client.close()


if __name__ == "__main__":
    main()
