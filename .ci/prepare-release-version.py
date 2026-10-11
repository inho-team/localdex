#!/usr/bin/env python3

import re
import sys
from pathlib import Path


def main() -> None:
    if len(sys.argv) not in (2, 3):
        raise SystemExit("Usage: prepare-release-version.py VERSION [GRADLE_FILE]")

    version = sys.argv[1]
    match = re.fullmatch(r"(0|[1-9][0-9]{0,4})\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)", version)
    if match is None:
        raise SystemExit("Version must be MAJOR.MINOR.PATCH.")

    major, minor, patch = map(int, match.groups())
    version_code = major * 10000 + minor * 100 + patch
    if not 1 <= version_code <= 2100000000:
        raise SystemExit("Version code is outside the Android package range.")

    path = Path(sys.argv[2]) if len(sys.argv) == 3 else Path("app/build.gradle.kts")
    source = path.read_text()
    source, name_count = re.subn(r'versionName\s*=\s*"[^"]+"', f'versionName = "{version}"', source, count=1)
    source, code_count = re.subn(r"versionCode\s*=\s*[0-9]+", f"versionCode = {version_code}", source, count=1)
    if name_count != 1 or code_count != 1:
        raise SystemExit("Could not update both Android version fields.")

    path.write_text(source)
    print(f"Prepared LocalDex {version} (versionCode {version_code}).")


if __name__ == "__main__":
    main()
