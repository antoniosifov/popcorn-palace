"""Checks that every relative link, anchor and image in the READMEs points at something.

Run from anywhere: python3 .github/scripts/check-doc-links.py
"""
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parents[2]
files = [root / "README.md", root / "backend/README.md", root / "frontend/README.md", root / "notifications/README.md", root / "deploy/README.md"]


def slugs(text):
    """The anchors GitHub gives the headings of a document."""
    seen, result = {}, set()
    in_code = False
    for line in text.splitlines():
        if line.startswith("```"):
            in_code = not in_code
        if in_code or not line.startswith("#"):
            continue
        title = line.lstrip("#").strip()
        title = re.sub(r"`", "", title)
        slug = re.sub(r"[^\w\- ]", "", title.lower()).replace(" ", "-")
        count = seen.get(slug, 0)
        seen[slug] = count + 1
        result.add(slug if count == 0 else f"{slug}-{count}")
    return result


problems = 0
for file in files:
    text = file.read_text(encoding="utf-8")
    targets = re.findall(r"\]\(([^)\s]+)\)", text) + re.findall(r'(?:src|href)="([^"]+)"', text)
    for target in targets:
        if target.startswith(("http://", "https://", "mailto:")):
            continue
        path, _, anchor = target.partition("#")
        destination = (file.parent / path).resolve() if path else file
        if not destination.exists():
            print(f"{file.relative_to(root)}: missing {target}")
            problems += 1
            continue
        if anchor and destination.suffix == ".md":
            if anchor not in slugs(destination.read_text(encoding="utf-8")):
                print(f"{file.relative_to(root)}: no anchor #{anchor} in {destination.relative_to(root)}")
                problems += 1
    print(f"{file.relative_to(root)}: {len(targets)} links checked")
sys.exit(1 if problems else 0)
