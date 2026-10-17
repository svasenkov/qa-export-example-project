#!/usr/bin/env python3
"""Validate skill metadata and local Markdown links in this harness.

Python standard library only. This tool never writes files. This is a structural
check, not a test of agent discovery, behavior, project commands or coverage.
The small frontmatter format used here is name/description with plain, JSON
quoted, or folded values; this is deliberately not a general YAML parser.
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parent.parent


def metadata(path: Path) -> dict[str, str]:
    text = path.read_text(encoding="utf-8")
    match = re.match(r"\A---\n(.*?)\n---(?:\n|$)", text, re.S)
    if not match:
        raise ValueError(f"{path}: missing or unclosed frontmatter")
    values: dict[str, str] = {}
    lines = iter(match[1].splitlines())
    current = None
    for line in lines:
        if not line.strip():
            continue
        if line.startswith(" "):
            if current is None:
                raise ValueError(f"{path}: unexpected indented metadata")
            values[current] += " " + line.strip()
            continue
        key, sep, raw = line.partition(":")
        if not sep or key not in {"name", "description"} or key in values:
            raise ValueError(f"{path}: unsupported or duplicate metadata field {key!r}")
        raw = raw.strip()
        current = key if raw in {">", ">-", "|", "|-"} else None
        if current:
            values[key] = ""
        elif raw.startswith('"'):
            value = json.loads(raw)
            if not isinstance(value, str):
                raise ValueError(f"{path}: {key} must be a string")
            values[key] = value
        else:
            values[key] = raw
    values = {key: value.strip() for key, value in values.items()}
    name = values.get("name", "")
    if not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", name) or len(name) > 64:
        raise ValueError(f"{path}: invalid skill name")
    if name != path.parent.name:
        raise ValueError(f"{path}: name differs from directory")
    if not values.get("description"):
        raise ValueError(f"{path}: empty description")
    return values


def validate_links(root: Path, paths: list[Path]) -> None:
    """Check inline Markdown file links used by these documents, outside fences.

    Does not validate external URLs, heading anchors or backtick prose paths.
    """
    for path in paths:
        text = re.sub(r"^```[^\n]*\n.*?^```[^\n]*$", "", path.read_text(encoding="utf-8"), flags=re.M | re.S)
        for target in re.findall(r"\]\(([^\s)]+)\)", text):
            url = urlsplit(target.strip("<>"))
            if url.scheme or url.netloc or not url.path:
                continue
            candidate = (path.parent / unquote(url.path)).resolve()
            if not candidate.is_relative_to(root.resolve()) or not candidate.exists():
                raise ValueError(f"{path.relative_to(root)}: missing or external local link {target}")


def validate(root: Path) -> None:
    skills = sorted((root / "skills").glob("*/SKILL.md"))
    if not skills:
        raise ValueError("No skills found in skills/")
    docs = [root / "AGENTS.md", root / "README.md", root / "docs/coverage-profile.md", *sorted((root / "docs/qa").glob("*.md"))]
    for path in docs:
        if not path.is_file():
            raise ValueError(f"Missing required document: {path.relative_to(root)}")
    for path in skills:
        metadata(path)
    validate_links(root, [*docs, *skills])


def main() -> int:
    try:
        validate(ROOT)
    except (ValueError, OSError) as error:
        print(f"ERROR: {error}")
        return 1
    print("OK: skill metadata and local file links checked. No files written.")
    print("Agent discovery, behavior and test commands were not executed by this check.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
