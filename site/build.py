"""Builds the policy website: puts each page in layout.html and fills {{KEY}} from details.properties.

Usage: python3 site/build.py <output-dir>
Fails (and lists what is missing) if any detail is empty, so incomplete pages are never published.
"""
import html
import pathlib
import re
import shutil
import sys

SITE = pathlib.Path(__file__).resolve().parent


def load_details():
    details = {}
    for line in (SITE / "details.properties").read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, _, value = line.partition("=")
        details[key.strip()] = value.strip()
    return details


def main():
    out = pathlib.Path(sys.argv[1])
    details = load_details()
    missing = [k for k, v in details.items() if not v]
    if missing:
        print("Fill these in site/details.properties before publishing: " + ", ".join(missing))
        sys.exit(1)

    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    unknown = set()
    layout = (SITE / "layout.html").read_text(encoding="utf-8")
    for page in sorted((SITE / "pages").glob("*.html")):
        body = page.read_text(encoding="utf-8")
        title = re.match(r"<!-- title: (.+?) -->", body).group(1)
        text = layout.replace("[[TITLE]]", title).replace("[[BODY]]", body)

        def fill(match):
            key = match.group(1)
            if key not in details:
                unknown.add(f"{page.name}: {key}")
                return match.group(0)
            return html.escape(details[key])

        (out / page.name).write_text(re.sub(r"\{\{([A-Z_]+)\}\}", fill, text), encoding="utf-8")
    shutil.copy(SITE / "style.css", out / "style.css")
    if unknown:
        print("Unknown placeholders: " + ", ".join(sorted(unknown)))
        sys.exit(1)
    print(f"Built {len(list(out.glob('*.html')))} pages into {out}")


if __name__ == "__main__":
    main()
