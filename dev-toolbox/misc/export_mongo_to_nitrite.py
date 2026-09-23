#!/usr/bin/env python3
"""Export a MongoDB database into a Nitrite (MVStore) file the game can open.

Thin launcher around ``house.x1337.app.smb3.tool.MongoToNitriteExporter``: it compiles the
project, resolves the runtime classpath through the Maven wrapper and hands every connection
option over to the Java exporter.

Settings not passed explicitly fall back to the ``SPRING_DATA_MONGODB_*`` environment variables,
then to ``src/main/resources/application.properties``, then to built-in defaults
(``localhost:27017``, database ``smb3db``).

An existing output file is renamed in place to ``smb3.db.bak01``, ``smb3.db.bak02``, ... using the
first free index; nothing is ever overwritten.
"""

import argparse
import os
import subprocess
import sys

TOOLBOX_DIR = os.path.dirname(os.path.abspath(__file__))        # dev-toolbox/misc
TOOLBOX_ROOT = os.path.dirname(TOOLBOX_DIR)                     # dev-toolbox/
PROJECT_ROOT = os.path.dirname(TOOLBOX_ROOT)                    # super-mario-bros-3/
TARGET_DIR = os.path.join(PROJECT_ROOT, 'target')
CLASSES_DIR = os.path.join(TARGET_DIR, 'classes')
CLASSPATH_FILE = os.path.join(TARGET_DIR, 'exporter-classpath.txt')
MAIN_CLASS = 'house.x1337.app.smb3.tool.MongoToNitriteExporter'


def maven_wrapper():
    return os.path.join(PROJECT_ROOT, 'mvnw.cmd' if os.name == 'nt' else 'mvnw')


def run(command):
    print('$ ' + ' '.join(command))
    completed = subprocess.run(command, cwd=PROJECT_ROOT, shell=False)
    if completed.returncode != 0:
        sys.exit(completed.returncode)


def build():
    mvnw = maven_wrapper()
    run([mvnw, '-q', 'compile'])
    run([mvnw, '-q', 'dependency:build-classpath',
         '-Dmdep.outputFile=' + CLASSPATH_FILE, '-Dmdep.includeScope=runtime'])


def classpath():
    with open(CLASSPATH_FILE, encoding='utf-8') as handle:
        dependencies = handle.read().strip()
    return CLASSES_DIR + os.pathsep + dependencies


def main():
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument('--uri', help='Full Mongo connection string (overrides --host/--port)')
    parser.add_argument('--host', help='Mongo host (default: localhost)')
    parser.add_argument('--port', help='Mongo port (default: 27017)')
    parser.add_argument('--database', help='Mongo database (default: smb3db)')
    parser.add_argument('--username', help='Mongo user (default: none, i.e. no authentication)')
    parser.add_argument('--password', help='Mongo password')
    parser.add_argument('--auth-database', help='Authentication database (default: --database)')
    parser.add_argument('--output', help='Nitrite output file (default: <project root>/smb3.db)')
    parser.add_argument('--compress', help='MVStore compression, true or false (default: false)')
    parser.add_argument('--no-backup', action='store_true',
                        help='Fail instead of rotating an existing output file')
    parser.add_argument('--skip-build', action='store_true',
                        help='Reuse the previously compiled classes and cached classpath')
    args = parser.parse_args()

    if not args.skip_build or not os.path.isdir(CLASSES_DIR) or not os.path.isfile(CLASSPATH_FILE):
        build()

    exporter_args = []
    for option in ('uri', 'host', 'port', 'database', 'username', 'password', 'auth_database',
                   'compress'):
        value = getattr(args, option)
        if value:
            exporter_args += ['--' + option.replace('_', '-'), value]
    if args.no_backup:
        exporter_args.append('--no-backup')
    exporter_args += ['--output', args.output or os.path.join(PROJECT_ROOT, 'smb3.db')]

    run(['java', '-cp', classpath(), MAIN_CLASS] + exporter_args)


if __name__ == '__main__':
    main()
