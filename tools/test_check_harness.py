"""Regression checks for drift and incomplete harness output; no agent calls."""

import tempfile
import unittest
from pathlib import Path

from check_harness import validate


class HarnessTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.write("AGENTS.md", "Read [testing](docs/qa/testing.md).\n")
        self.write("README.md", "Project\n")
        self.write("docs/coverage-profile.md", "Profile\n")
        self.write("docs/qa/testing.md", "Checks\n")
        self.skill = "skills/qa-example/SKILL.md"
        self.write(self.skill, "---\nname: qa-example\ndescription: Inspect an example.\n---\nRead [testing](../../docs/qa/testing.md).\n")

    def write(self, name, content):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def test_valid_harness_is_not_modified(self):
        before = {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob("*") if p.is_file()}
        validate(self.root)
        after = {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob("*") if p.is_file()}
        self.assertEqual(before, after)

    def test_missing_reference_fails(self):
        (self.root / "docs/qa/testing.md").unlink()
        with self.assertRaisesRegex(ValueError, "missing.*link"):
            validate(self.root)

    def test_external_reference_fails(self):
        self.write("AGENTS.md", "[outside](../outside.md)\n")
        with self.assertRaisesRegex(ValueError, "external local link"):
            validate(self.root)

    def test_bad_metadata_fails(self):
        self.write(self.skill, "---\nname: another-name\ndescription: Example.\n---\n")
        with self.assertRaisesRegex(ValueError, "differs from directory"):
            validate(self.root)

    def test_missing_sources_does_not_report_success(self):
        (self.root / self.skill).unlink()
        with self.assertRaisesRegex(ValueError, "No skills"):
            validate(self.root)


if __name__ == "__main__":
    unittest.main()
