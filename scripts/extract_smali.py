#!/usr/bin/env python3
"""Disassemble all DEX files to canonical analysis smali with baksmali."""

import argparse
import re
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path


APP_NAMES = {
    "com.instagram.barcelona": "threads",
    "com.zing.zalo": "zalo",
}


def default_output(apk: Path) -> Path:
    """Infer analysis/<app>/<version>/smali from an APKMirror bundle filename."""
    match = re.match(
        r"(?P<package>com(?:\.[A-Za-z0-9_]+)+)_(?P<version>\d+(?:\.\d+)+)-", apk.name
    )
    if not match:
        raise ValueError(
            f"Cannot infer app/version from {apk.name}; pass an output path"
        )
    package = match.group("package")
    app = APP_NAMES.get(package, package.rsplit(".", 1)[-1])
    return (
        Path(__file__).resolve().parents[1]
        / "analysis"
        / app
        / match.group("version")
        / "smali"
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", help="APK, APKM, XAPK, or APKS input")
    parser.add_argument(
        "output",
        nargs="?",
        help="output directory (default: inferred analysis/<app>/<version>/smali)",
    )
    args = parser.parse_args()
    apk = Path(args.apk)
    if not apk.is_file():
        parser.error(f"Not found: {apk}")
    try:
        out = Path(args.output) if args.output else default_output(apk)
    except ValueError as error:
        parser.error(str(error))

    out.parent.mkdir(parents=True, exist_ok=True)
    sources = [apk]
    with tempfile.TemporaryDirectory(
        dir=out.parent, prefix=".extract-smali-"
    ) as staging:
        stage = Path(staging) / "smali"
        stage.mkdir()
        with tempfile.TemporaryDirectory() as work:
            if apk.suffix.lower() in {".apkm", ".xapk", ".apks"}:
                split = Path(work) / "splits"
                split.mkdir()
                with zipfile.ZipFile(apk) as archive:
                    archive.extractall(
                        split,
                        [name for name in archive.namelist() if name.endswith(".apk")],
                    )
                sources = sorted(split.rglob("*.apk"))

            count = 0
            for source in sources:
                split_out = stage / (
                    source.stem if source.parent.name == "splits" else ""
                )
                split_out.mkdir(parents=True, exist_ok=True)
                with zipfile.ZipFile(source) as archive:
                    for dex in sorted(
                        name for name in archive.namelist() if name.endswith(".dex")
                    ):
                        current = Path(work) / "current.dex"
                        current.write_bytes(archive.read(dex))
                        subprocess.run(
                            [
                                "baksmali",
                                "d",
                                str(current),
                                "-o",
                                str(split_out / Path(dex).stem),
                            ],
                            check=True,
                        )
                        count += 1
            if not count:
                raise SystemExit(f"❌ No DEX files found in {apk}")

        if out.exists():
            shutil.rmtree(out)
        stage.rename(out)
    print(
        f"✅ Disassembled {count} DEX file(s) to {out}/ (baksmali; replaced existing output)"
    )


if __name__ == "__main__":
    main()
