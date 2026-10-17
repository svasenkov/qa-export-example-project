"""Regression checks for drift and incomplete harness output; no agent calls."""

import tempfile
import unittest
from pathlib import Path

from sync_adapters import sync


class AdapterTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.write("AGENTS.md", "Read [testing](docs/qa/testing.md).\n")
        self.write("README.md", "Project\n")
        self.write("docs/coverage-profile.md", "Profile\n")
        self.write("docs/qa/testing.md", "Checks\n")
        self.skill = ".agents/skills/qa-example/SKILL.md"
        self.write(self.skill, "---\nname: qa-example\ndescription: Inspect an example.\n---\nRead [testing](../../../docs/qa/testing.md).\n")

    def write(self, name, content):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def test_check_does_not_generate_missing_files(self):
        self.assertTrue(sync(self.root, check=True))
        self.assertFalse((self.root / ".cline").exists())
        self.assertFalse((self.root / ".clinerules").exists())

    def test_generated_copy_preserves_body_and_detects_drift(self):
        sync(self.root, check=False)
        copy = ".cline/skills/qa-example/SKILL.md"
        self.assertEqual((self.root / copy).read_bytes(), (self.root / self.skill).read_bytes())
        self.assertEqual(sync(self.root, check=True), [])
        self.write(copy, "stale copy\n")
        self.assertIn(copy, sync(self.root, check=True))
        self.assertEqual((self.root / copy).read_text(), "stale copy\n")

    def test_missing_reference_blocks_write(self):
        (self.root / "docs/qa/testing.md").unlink()
        with self.assertRaisesRegex(ValueError, "missing.*link"):
            sync(self.root, check=False)
        self.assertFalse((self.root / ".cline").exists())

    def test_external_reference_blocks_write(self):
        self.write("AGENTS.md", "[outside](../outside.md)\n")
        with self.assertRaisesRegex(ValueError, "external local link"):
            sync(self.root, check=False)
        self.assertFalse((self.root / ".clinerules").exists())

    def test_bad_metadata_blocks_write(self):
        self.write(self.skill, "---\nname: another-name\ndescription: Example.\n---\n")
        with self.assertRaisesRegex(ValueError, "differs from directory"):
            sync(self.root, check=False)
        self.assertFalse((self.root / ".cline").exists())

    def test_missing_sources_does_not_report_success(self):
        (self.root / self.skill).unlink()
        with self.assertRaisesRegex(ValueError, "No skills"):
            sync(self.root, check=True)

    def test_unmanaged_skill_is_not_silently_deleted(self):
        self.write(".cline/skills/personal/SKILL.md", "personal instructions\n")
        with self.assertRaisesRegex(ValueError, "Unexpected Cline skills"):
            sync(self.root, check=False)
        self.assertTrue((self.root / ".cline/skills/personal/SKILL.md").exists())


if __name__ == "__main__":
    unittest.main()
