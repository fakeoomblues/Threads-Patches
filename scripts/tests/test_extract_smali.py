import importlib.util
import io
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).resolve().parents[1] / "extract_smali.py"
SPEC = importlib.util.spec_from_file_location("extract_smali", SCRIPT)
extract_smali = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(extract_smali)


class ExtractSmaliTest(unittest.TestCase):
    def test_default_output_uses_app_and_version(self):
        threads = extract_smali.default_output(
            Path("com.instagram.barcelona_445.0.0.46.83-1.apkm")
        )
        zalo = extract_smali.default_output(Path("com.zing.zalo_26.08.01-1.apkm"))
        root = SCRIPT.parents[1] / "analysis"
        self.assertEqual(root / "threads/445.0.0.46.83/smali", threads)
        self.assertEqual(root / "zalo/26.08.01/smali", zalo)

    def test_default_output_replaces_stale_directory(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            bundle = root / "com.instagram.barcelona_445.0.0.46.83-1.apkm"
            out = root / "analysis/threads/445.0.0.46.83/smali"
            out.mkdir(parents=True)
            (out / "stale.smali").write_text("stale")
            split = io.BytesIO()
            with zipfile.ZipFile(split, "w") as apk:
                apk.writestr("classes.dex", b"dex")
            with zipfile.ZipFile(bundle, "w") as apkm:
                apkm.writestr("base.apk", split.getvalue())

            def fake_baksmali(command, check):
                target = Path(command[command.index("-o") + 1])
                target.mkdir(parents=True)
                (target / "Class.smali").write_text("fresh")

            with (
                mock.patch.object(extract_smali, "default_output", return_value=out),
                mock.patch.object(
                    extract_smali.subprocess, "run", side_effect=fake_baksmali
                ),
                mock.patch.object(sys, "argv", [str(SCRIPT), str(bundle)]),
            ):
                extract_smali.main()

            self.assertFalse((out / "stale.smali").exists())
            self.assertEqual("fresh", next(out.rglob("Class.smali")).read_text())


if __name__ == "__main__":
    unittest.main()
