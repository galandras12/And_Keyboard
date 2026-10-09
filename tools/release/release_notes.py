#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""
Writes the GitHub release notes for a version from CHANGELOG.md:

  1. the pre-release notice,
  2. the description of the released (latest) version,
  3. the whole changelog, newest version first,
  4. the source application the keyboard is based on.

usage: release_notes.py <version, e.g. 0.2> [CHANGELOG.md]   (prints Markdown to standard output)
"""
import re
import sys


def main():
    version = sys.argv[1].lstrip("vV")
    path = sys.argv[2] if len(sys.argv) > 2 else "CHANGELOG.md"
    text = open(path, encoding="utf-8").read()

    # split into "## ..." sections
    parts = re.split(r"(?m)^## ", text)
    versions = {}  # version -> section text, in file order (newest first)
    source = ""
    for part in parts[1:]:
        title, _, body = part.partition("\n")
        m = re.match(r"(\d+(?:\.\d+)*)\b", title)
        if m:
            versions[m.group(1)] = "## " + title + "\n" + body.rstrip() + "\n"
        elif title.strip().lower().startswith("source application"):
            source = body.strip()
    if version not in versions:
        sys.exit("version %s is not in %s" % (version, path))
    newest = next(iter(versions))
    if version != newest:
        sys.exit("version %s is not the newest one in the changelog (%s)" % (version, newest))

    out = []
    out.append("> **Pre-release.** This is an early version of And Keyboard, not fully tested on real devices. "
               "The attached APK is a **debug build** (signed with a public debug key), meant for testing.\n")
    latest = versions[version].replace("## ", "## What's new in ", 1)
    out.append(latest)
    out.append("## Changelog\n")
    for body in versions.values():
        out.append(body.replace("## ", "### ", 1).replace("\n### ", "\n#### "))
    out.append("## Source application\n")
    out.append(source + "\n")
    print("\n".join(out))


if __name__ == "__main__":
    main()
