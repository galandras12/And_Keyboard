#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""
Builds a Hungarian main dictionary (main_hu.dict) for the keyboard.

Pipeline:
  1. download the Hungarian Hunspell dictionary (hu_HU.aff / hu_HU.dic, LibreOffice)
     and a free word frequency list (FrequencyWords, hu_full.txt)
  2. resolve the numeric affix aliases ("AF" lines) of hu_HU, which `unmunch` does not understand
  3. expand the dictionary with `unmunch` (Hunspell tools) and keep the generated forms that are
     attested in the frequency list, plus all base forms (stems)
  4. write an AOSP `.combined` wordlist, with `f` (0-255, logarithmic) taken from the frequency list
  5. compile it into a `.dict` file with dicttool_aosp.jar (needs Java)

The result can be imported in the keyboard settings (Languages & layouts -> language -> add dictionary).
Sources and their licenses are documented in README.md.

Requirements: Python 3.8+, `unmunch` (Debian/Ubuntu: apt install hunspell-tools), Java (for step 5).
"""

import argparse
import hashlib
import math
import os
import re
import subprocess
import sys
import time
import urllib.request

# Sources are pinned to exact commits and verified by SHA-256, so builds are reproducible.
LIBREOFFICE_COMMIT = "32b006a2c22a4ac7e8ed3f03346f7b3d85a970a4"
FREQUENCYWORDS_COMMIT = "525f9b560de45753a5ea01069454e72e9aa541c6"
AOSP_DICTIONARIES_COMMIT = "795c8c4ab3de8286152f53855e006e8362a62103"
LO_BASE = "https://raw.githubusercontent.com/LibreOffice/dictionaries/%s/hu_HU/" % LIBREOFFICE_COMMIT
FW_BASE = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/%s/content/2018/hu/" % FREQUENCYWORDS_COMMIT
DICTTOOL_URL = "https://codeberg.org/Helium314/aosp-dictionaries/raw/commit/%s/dicttool_aosp.jar" % AOSP_DICTIONARIES_COMMIT

SOURCES = {  # file name -> (url, sha256)
    "hu_HU.aff": (LO_BASE + "hu_HU.aff", "f3a2748dd535cfde2142ab17d0f7f8e4787b03fb25a60829c69ac8d493db4802"),
    "hu_HU.dic": (LO_BASE + "hu_HU.dic", "97293d670ad4a3b8e7eebef7e25c6e8e939b914c64b6b4672b2bf416b768f990"),
    "README_hu_HU.txt": (LO_BASE + "README_hu_HU.txt", "52b17fc3d53b6935eab747a71894507d702f7f0fde379c819c16cc803005fc84"),
    "hu_full.txt": (FW_BASE + "hu_full.txt", "d97b4e6017f16b1c0fc5c9bbb8a89e013adffd966c258951ed9067e0e0439689"),
    "dicttool_aosp.jar": (DICTTOOL_URL, "a8c5bd21f631ed0a92235d42d2fe83af5d70216172bf7e22781a9a946858237e"),
}

# A word is a run of letters, optionally joined by single hyphens.
WORD_RE = re.compile(r"^[^\W\d_]+(?:-[^\W\d_]+)*$")


def log(msg):
    print(msg, file=sys.stderr, flush=True)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch_sources(work_dir):
    os.makedirs(work_dir, exist_ok=True)
    for name, (url, digest) in SOURCES.items():
        path = os.path.join(work_dir, name)
        if not (os.path.exists(path) and sha256(path) == digest):
            log("downloading %s" % url)
            urllib.request.urlretrieve(url, path)
            if sha256(path) != digest:
                sys.exit("checksum mismatch for %s (expected %s)" % (name, digest))
    return {name: os.path.join(work_dir, name) for name in SOURCES}


def flatten_hunspell(aff_path, dic_path, out_aff, out_dic):
    """
    hu_HU stores affix flags as numeric aliases ("AF n" table; dic entries look like `word/12`,
    continuation classes in affix rules like `ös/1429`). `unmunch` only understands literal flags,
    so replace every alias by its flag string. Works on bytes: flags are arbitrary Latin-2 bytes.
    Returns ([(stem, flags)], special flag bytes).
    """
    aff_lines = open(aff_path, "rb").read().split(b"\n")
    aliases = [b""]
    out = []
    i = 0
    while i < len(aff_lines):
        line = aff_lines[i]
        if re.fullmatch(rb"AF \d+\s*", line):
            for _ in range(int(line.split()[1])):
                i += 1
                body = aff_lines[i][3:]
                cut = body.find(b" #")  # trailing "# n" comment
                aliases.append((body[:cut] if cut >= 0 else body).rstrip(b" \t\r"))
            i += 1
            continue
        if line.startswith((b"SFX ", b"PFX ")):
            parts = line.split(b" ")
            if len(parts) >= 5 and b"/" in parts[3]:
                add, _, cont = parts[3].partition(b"/")
                if cont.isdigit():
                    parts[3] = add + b"/" + aliases[int(cont)]
                    line = b" ".join(parts)
        out.append(line)
        i += 1
    with open(out_aff, "wb") as f:
        f.write(b"\n".join(out))

    stems = []
    for line in open(dic_path, "rb").read().split(b"\n")[1:]:  # first line is the entry count
        if not line:
            continue
        entry = line.split(b"\t", 1)[0]  # "word/alias<TAB>morphological data"
        word, _, alias = entry.partition(b"/")
        stems.append((word, aliases[int(alias)] if alias.isdigit() else alias))
    with open(out_dic, "wb") as f:
        f.write(str(len(stems)).encode() + b"\n")
        for word, flags in stems:
            f.write(word + (b"/" + flags if flags else b"") + b"\n")
    return stems, special_flags(b"\n".join(out))


def special_flags(aff):
    """Flag bytes of the Hunspell options that decide whether a bare stem is a usable word."""
    flags = {}
    for name in (b"NEEDAFFIX", b"ONLYINCOMPOUND", b"FORBIDDENWORD", b"NOSUGGEST"):
        m = re.search(rb"^" + name + rb" (\S+)", aff, re.M)
        if m:
            flags[name] = m.group(1)
    return flags


def read_frequencies(path, min_count):
    counts = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            parts = line.split()
            if len(parts) != 2 or not parts[1].isdigit():
                continue
            word, count = parts[0].lower(), int(parts[1])
            if count >= min_count and WORD_RE.match(word):
                counts[word] = counts.get(word, 0) + count
    return counts


def capitalize(word):
    return word[:1].upper() + word[1:]


def expand(dic_flat, aff_flat, counts, stems, special, min_unknown_count):
    """Streams all unmunch output through a filter, so the (huge) expansion never touches the disk."""
    # bytes -> lowercase word; both "word" and "Word" are looked up, because proper nouns are capitalized
    wanted = {}
    for word in counts:
        wanted[word.encode()] = word
        wanted[capitalize(word).encode()] = word

    forbidden = set()
    bare_only = set()  # stems that are only valid with an affix or inside compounds
    for word, flags in stems:
        if special.get(b"FORBIDDENWORD") and special[b"FORBIDDENWORD"] in flags:
            forbidden.add(word)
        for key in (b"NEEDAFFIX", b"ONLYINCOMPOUND", b"NOSUGGEST"):
            if special.get(key) and special[key] in flags:
                bare_only.add(word)

    kept = set()
    for word, _flags in stems:
        if word not in forbidden and word not in bare_only:
            kept.add(word)

    log("expanding with unmunch (a few hundred million forms, takes a few minutes)")
    proc = subprocess.Popen(["unmunch", dic_flat, aff_flat], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    total = 0
    for raw in proc.stdout:
        total += 1
        word = raw.rstrip(b"\n")
        if word in wanted and word not in forbidden:
            kept.add(word)
    if proc.wait() != 0:
        sys.exit("unmunch failed")
    log("unmunch generated %d forms, %d kept" % (total, len(kept)))

    words = {}
    for raw in kept:
        try:
            word = raw.decode("utf-8")
        except UnicodeDecodeError:
            continue
        if WORD_RE.match(word):
            words[word] = counts.get(word.lower(), 0)

    if min_unknown_count:  # optional: frequent corpus words that Hunspell does not know (names, slang, typos!)
        known = {w.lower() for w in words}
        for word, count in counts.items():
            if count >= min_unknown_count and word not in known:
                words[word] = count
    return words


def to_frequency(words):
    """Map corpus counts to AOSP `f` values: logarithmic, 1..255; words never seen in the corpus get 1."""
    top = max(words.values()) or 1
    result = {}
    for word, count in words.items():
        lower = word.lower()
        if word != lower and lower in words:
            f = 1  # "Rend" next to "rend": the capitalized form must not outrank the common lowercase one
        elif count <= 1:
            f = 1
        else:
            f = 1 + int(254 * math.log(count) / math.log(top))
        result[word] = min(f, 255)
    return result


def write_wordlist(path, freqs, version):
    header = "dictionary=main:hu,locale=hu,description=Hungarian,date=%d,version=%d" % (
        int(os.environ.get("SOURCE_DATE_EPOCH", time.time())), version)
    with open(path, "w", encoding="utf-8") as f:
        f.write(header + "\n")
        for word, f_value in sorted(freqs.items(), key=lambda kv: (-kv[1], kv[0])):
            f.write(" word=%s,f=%d\n" % (word, f_value))


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--work-dir", default=os.path.join(here, "work"), help="download and intermediate files (default: %(default)s)")
    ap.add_argument("--out-dir", default=os.path.join(here, "out"), help="where main_hu.combined / main_hu.dict go (default: %(default)s)")
    ap.add_argument("--min-count", type=int, default=3, help="ignore corpus words seen fewer times (default: %(default)s)")
    ap.add_argument("--min-unknown-count", type=int, default=0,
                    help="also add corpus words unknown to Hunspell seen at least this often; 0 = off (default). "
                         "The corpus is noisy (subtitles), so use high values, e.g. 2000")
    ap.add_argument("--version", type=int, default=19, help="dictionary version in the header (should be > 18)")
    ap.add_argument("--skip-dict", action="store_true", help="only write the .combined wordlist, do not run Java")
    args = ap.parse_args()

    files = fetch_sources(args.work_dir)
    os.makedirs(args.out_dir, exist_ok=True)

    flat_aff = os.path.join(args.work_dir, "hu_flat.aff")
    flat_dic = os.path.join(args.work_dir, "hu_flat.dic")
    stems, special = flatten_hunspell(files["hu_HU.aff"], files["hu_HU.dic"], flat_aff, flat_dic)
    log("%d stems" % len(stems))

    counts = read_frequencies(files["hu_full.txt"], args.min_count)
    log("%d corpus words with count >= %d" % (len(counts), args.min_count))

    words = expand(flat_dic, flat_aff, counts, stems, special, args.min_unknown_count)
    freqs = to_frequency(words)

    combined = os.path.join(args.out_dir, "main_hu.combined")
    write_wordlist(combined, freqs, args.version)
    log("wrote %d words to %s" % (len(freqs), combined))

    if args.skip_dict:
        return
    dict_path = os.path.join(args.out_dir, "main_hu.dict")
    subprocess.check_call(["java", "-jar", files["dicttool_aosp.jar"], "makedict", "-s", combined, "-d", dict_path])
    log("wrote %s (%d bytes)" % (dict_path, os.path.getsize(dict_path)))


if __name__ == "__main__":
    main()
