#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""
Builds an emoji search dictionary (emoji_<locale>.dict) from the Unicode CLDR emoji annotations.

The keyboard's emoji search looks up the typed word in an "emoji" dictionary, which maps words
to emojis (as shortcuts). This script creates that dictionary from
  * the CLDR annotations (keywords and short names) of a language, default Hungarian, and
  * the emoji-test.txt that the keyboard's emoji palette is generated from (list and order of emojis).
Skin tone variants are left out; the keyboard applies the preferred skin tone itself.

Requirements: Python 3.8+, Java (for the .dict compiler; not needed with --skip-dict).
"""
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import urllib.request

CLDR_TAG = "48.2.3"
CLDR_BASE = "https://raw.githubusercontent.com/unicode-org/cldr-json/%s/cldr-json/" % CLDR_TAG
DICTTOOL_URL = "https://codeberg.org/Helium314/aosp-dictionaries/raw/commit/795c8c4ab3de8286152f53855e006e8362a62103/dicttool_aosp.jar"
DICTTOOL_SHA256 = "a8c5bd21f631ed0a92235d42d2fe83af5d70216172bf7e22781a9a946858237e"
# the same file the emoji palette of the keyboard is generated from
EMOJI_TEST = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "make-emoji-keys", "src", "main", "resources",
                          "emoji", "ucd", "17.0", "emoji-test.txt")

MAX_EMOJIS_PER_WORD = 40
SKIN_TONES = range(0x1F3FB, 0x1F400)
VS16 = "️"
WORD_RE = re.compile(r"[^\W\d_]+(?:[-'’][^\W\d_]+)*", re.UNICODE)


def log(msg):
    print(msg, file=sys.stderr, flush=True)


def download(url, path, sha256=None):
    if os.path.exists(path) and (sha256 is None or hashlib.sha256(open(path, "rb").read()).hexdigest() == sha256):
        return path
    log("downloading " + url)
    urllib.request.urlretrieve(url, path)
    if sha256 and hashlib.sha256(open(path, "rb").read()).hexdigest() != sha256:
        sys.exit("checksum mismatch for " + path)
    return path


def read_emoji_test(path):
    """Fully qualified emojis without skin tones, in keyboard order -> {emoji: index}."""
    order = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or ";" not in line:
                continue
            codes, rest = line.split(";", 1)
            if rest.split("#")[0].strip() != "fully-qualified":
                continue
            points = [int(c, 16) for c in codes.split()]
            if any(p in SKIN_TONES for p in points):
                continue
            order[("".join(chr(p) for p in points))] = len(order)
    return order


def load_annotations(path, key):
    with open(path, encoding="utf-8") as f:
        return json.load(f)[key]["annotations"]


def words_of(phrase):
    return [w.lower() for w in WORD_RE.findall(phrase) if len(w) >= 2]


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--locale", default="hu", help="CLDR locale, e.g. hu or de (default: %(default)s)")
    ap.add_argument("--work-dir", default=os.path.join(here, "work"))
    ap.add_argument("--out-dir", default=os.path.join(here, "out"))
    ap.add_argument("--version", type=int, default=20)
    ap.add_argument("--skip-dict", action="store_true", help="only write the .combined wordlist, do not run Java")
    args = ap.parse_args()
    os.makedirs(args.work_dir, exist_ok=True)
    os.makedirs(args.out_dir, exist_ok=True)

    loc = args.locale
    ann = load_annotations(download(CLDR_BASE + "cldr-annotations-full/annotations/%s/annotations.json" % loc,
                                    os.path.join(args.work_dir, "annotations_%s.json" % loc)), "annotations")
    derived = load_annotations(download(CLDR_BASE + "cldr-annotations-derived-full/annotationsDerived/%s/annotations.json" % loc,
                                        os.path.join(args.work_dir, "annotationsDerived_%s.json" % loc)), "annotationsDerived")
    ann.update(derived)
    order = read_emoji_test(EMOJI_TEST)
    by_stripped = {e.replace(VS16, ""): e for e in order}  # CLDR often omits the variation selector

    # word -> {emoji: score}
    index = {}

    def add(word, emoji, score):
        scores = index.setdefault(word, {})
        scores[emoji] = max(scores.get(emoji, 0), score)

    matched = 0
    for key, data in ann.items():
        emoji = key if key in order else by_stripped.get(key.replace(VS16, ""))
        if emoji is None:
            continue
        matched += 1
        for phrase in data.get("tts", []):
            ws = words_of(phrase)
            for w in ws:
                add(w, emoji, 22 if len(ws) == 1 else 18)  # the whole name of the emoji is the best hit
        for phrase in data.get("default", []):
            ws = words_of(phrase)
            for w in ws:
                add(w, emoji, 20 if len(ws) == 1 else 17)  # a keyword of its own is a better hit than a word of a phrase
    log("%d of %d emojis annotated" % (matched, len(order)))

    combined = os.path.join(args.out_dir, "emoji_%s.combined" % loc)
    with open(combined, "w", encoding="utf-8") as f:
        f.write("dictionary=emoji:%s,description=Emoji for %s words,locale=%s,date=%d,version=%d\n" % (
            loc, loc, loc, int(os.environ.get("SOURCE_DATE_EPOCH", time.time())), args.version))
        for word in sorted(index):
            ranked = sorted(index[word].items(), key=lambda kv: (-kv[1], order[kv[0]]))[:MAX_EMOJIS_PER_WORD]
            f.write(" word=%s,f=%d,not_a_word=true\n" % (word, ranked[0][1]))
            for emoji, score in ranked:
                f.write("  shortcut=%s,f=%d\n" % (emoji, score))
    log("wrote %d words to %s" % (len(index), combined))

    if args.skip_dict:
        return
    jar = download(DICTTOOL_URL, os.path.join(args.work_dir, "dicttool_aosp.jar"), DICTTOOL_SHA256)
    out = os.path.join(args.out_dir, "emoji_%s.dict" % loc)
    subprocess.check_call(["java", "-jar", jar, "makedict", "-s", combined, "-d", out])
    log("wrote %s (%d bytes)" % (out, os.path.getsize(out)))


if __name__ == "__main__":
    main()
