#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""
Generates the glide typing test fixtures in app/src/test/resources/gesture/.

There is no real recorded data in this repository, so the "recorded" paths are simulated: a path through
the key centers of a word, smoothed with a Catmull-Rom spline, with random aiming errors at the corners,
jitter, and uneven sampling speed, similar to a finger moving over a keyboard. The generator is seeded, so
the fixtures are reproducible. It is independent of the Kotlin decoder on purpose.

Real traces can be added to the same files (format below) once they are recorded on a phone.

Files:
  layouts.tsv   layout<TAB>letter<TAB>center x<TAB>center y<TAB>width<TAB>height
  lexicon_<layout>.tsv   word<TAB>frequency (0-255)
  paths.tsv     layout<TAB>noise<TAB>word<TAB>x1,y1 x2,y2 ...   (noise = aiming error in key widths)
"""
import math
import os
import random

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "src", "test", "resources", "gesture")

# rows: (letters, x offset in key widths); keyboard is 1080 px wide, like a phone
LAYOUTS = {
    "qwerty": [("qwertyuiop", 0.0), ("asdfghjkl", 0.5), ("zxcvbnm", 1.5)],
    "qwertz": [("qwertzuiop", 0.0), ("asdfghjkl", 0.5), ("yxcvbnm", 1.5)],
    "azerty": [("azertyuiop", 0.0), ("qsdfghjklm", 0.0), ("wxcvbn", 1.5)],
    "hu": [("qwertzuiopö", 0.0), ("asdfghjkléá", 0.0), ("yxcvbnmü", 1.5)],
}
WIDTH = 1080.0
ROW_HEIGHT = 150.0


def build_layout(name):
    rows = LAYOUTS[name]
    slots = max(len(letters) + off for letters, off in rows)
    key_w = WIDTH / slots
    keys = {}
    for r, (letters, off) in enumerate(rows):
        for i, c in enumerate(letters):
            keys[c] = ((off + i + 0.5) * key_w, (r + 0.5) * ROW_HEIGHT, key_w, ROW_HEIGHT)
    return keys


def base_letter(c):
    return {"ő": "o", "ó": "o", "ű": "u", "ú": "u", "í": "i", "ö": "ö", "ü": "ü"}.get(c, c)


def waypoints(word, keys):
    pts, prev = [], None
    for c in word:
        k = c if c in keys else base_letter(c)
        if k != prev:
            pts.append((keys[k][0], keys[k][1]))
        prev = k
    return pts


def catmull(p0, p1, p2, p3, t):
    return tuple(0.5 * ((2 * p1[i]) + (-p0[i] + p2[i]) * t + (2 * p0[i] - 5 * p1[i] + 4 * p2[i] - p3[i]) * t * t
                        + (-p0[i] + 3 * p1[i] - 3 * p2[i] + p3[i]) * t ** 3) for i in range(2))


def simulate(word, keys, rng, noise):
    key_w = next(iter(keys.values()))[2]
    pts = [(x + rng.gauss(0, noise * key_w), y + rng.gauss(0, noise * key_w * 0.8)) for x, y in waypoints(word, keys)]
    if len(pts) < 2:
        return []
    ext = [pts[0]] + pts + [pts[-1]]
    dense = []
    for i in range(1, len(ext) - 2):
        for s in range(20):
            dense.append(catmull(ext[i - 1], ext[i], ext[i + 1], ext[i + 2], s / 20))
    dense.append(pts[-1])
    # uneven sampling: random step length along the path, finger jitter on top
    result, pos, step_base = [], 0.0, key_w * 0.25
    cum = [0.0]
    for i in range(1, len(dense)):
        cum.append(cum[-1] + math.dist(dense[i], dense[i - 1]))
    total = cum[-1]
    j = 0
    while pos < total:
        while j < len(cum) - 2 and cum[j + 1] < pos:
            j += 1
        seg = cum[j + 1] - cum[j] or 1.0
        t = (pos - cum[j]) / seg
        x = dense[j][0] + (dense[j + 1][0] - dense[j][0]) * t + rng.gauss(0, 0.03 * key_w)
        y = dense[j][1] + (dense[j + 1][1] - dense[j][1]) * t + rng.gauss(0, 0.03 * key_w)
        result.append((round(x, 1), round(y, 1)))
        pos += step_base * rng.uniform(0.5, 1.6)
    result.append((round(dense[-1][0], 1), round(dense[-1][1], 1)))
    return result


EN = """the 255 of 250 and 249 to 248 in 245 is 240 you 238 that 236 it 235 he 230 was 229 for 228 on 226 are 225 as 224 with 222 his 221
they 220 at 219 be 218 this 217 have 216 from 215 or 214 one 213 had 212 by 211 word 200 but 210 not 209 what 208 all 207 were 206
we 205 when 204 your 203 can 202 said 201 there 199 use 198 an 197 each 196 which 195 she 194 do 193 how 192 their 191 if 190 will 189
up 188 other 187 about 186 out 185 many 184 then 183 them 182 these 181 so 180 some 179 her 178 would 177 make 176 like 175 him 174
into 173 time 172 has 171 look 170 two 169 more 168 write 167 go 166 see 165 number 164 no 163 way 162 could 161 people 160 my 159
than 158 first 157 water 156 been 155 call 154 who 153 oil 152 its 151 now 150 find 149 long 148 down 147 day 146 did 145 get 144
come 143 made 142 may 141 part 140 hello 150 world 149 keyboard 140 quick 120 brown 120 fox 110 jumps 100 over 160 lazy 100 dog 110
good 150 morning 130 thanks 140 please 140 sorry 130 friend 130 house 140 phone 140 message 130 love 150 happy 130 today 140
tomorrow 120 yesterday 110 weekend 110 coffee 120 music 125 movie 120 book 125 read 130 write 126 school 125 work 135 home 140
let 150 yes 150 hell 100 held 100 hold 110 would 120 world 120 word 130 ward 80 wild 90 wind 100 wine 100 wire 90 wise 95""".split()

HU = """a 255 az 252 és 250 hogy 248 nem 247 van 246 egy 245 is 244 ez 243 de 242 meg 241 már 240 csak 239 mint 238 volt 237
még 236 igen 235 ha 234 itt 233 most 232 akkor 231 vagy 230 lesz 229 nagyon 228 kell 227 fog 226 minden 225 mi 224 én 223 te 222 ő 221
ember 220 idő 219 nap 218 év 217 magyar 216 ország 215 város 214 szép 213 jó 212 rossz 211 új 210 régi 209 nagy 208 kicsi 207 szeretlek 190
köszönöm 200 szia 199 szervusz 150 helló 160 üdv 140 viszlát 145 kérem 170 bocsánat 160 ház 200 autó 160 busz 150 vonat 150 repülő 145
víz 180 kenyér 170 tej 160 sör 150 bor 150 kávé 170 tea 160 étterem 150 szálloda 140 hotel 140 iskola 170 munka 190 család 180 barát 175
gyerek 185 apa 170 anya 175 testvér 150 bátyám 100 öcsém 100 húgom 100 telefon 170 üzenet 165 levél 150 könyv 170 újság 140 zene 160
film 165 játék 160 sport 150 labda 140 foci 145 szerelem 160 boldog 155 szomorú 130 fáradt 130 éhes 125 szomjas 115 beteg 135
orvos 140 kórház 135 gyógyszer 125 pénz 175 bank 150 bolt 160 piac 140 ár 150 olcsó 120 drága 130 reggel 170 este 170 éjszaka 150
tegnap 160 holnap 165 ma 190 hétvége 140 hétfő 135 kedd 130 szerda 130 csütörtök 125 péntek 135 szombat 135 vasárnap 130
január 110 február 105 március 110 április 105 május 110 június 105 július 105 augusztus 105 szeptember 105 október 105
november 100 december 105 tavasz 120 nyár 125 ősz 115 tél 120 hideg 135 meleg 140 eső 130 hó 125 szél 120 nap 150 hold 120
csillag 115 tenger 125 hegy 125 erdő 125 folyó 120 tó 115 virág 125 fa 140 kutya 140 macska 140 madár 125 hal 125 ló 120
asztal 130 szék 125 ajtó 130 ablak 130 szoba 140 konyha 135 fürdő 120 kert 130 utca 140 tér 130 híd 115 templom 120 múzeum 115
gép 130 számítógép 125 billentyűzet 110 egér 100 képernyő 105 internet 130 hálózat 115 program 125 alkalmazás 120 szó 150
mondat 120 nyelv 130 betű 120 szám 140 kérdés 140 válasz 135 probléma 125 megoldás 115 segítség 140 kérek 120 köszi 155
jól 180 rosszul 120 gyorsan 130 lassan 125 sokat 140 keveset 110 mindig 160 soha 130 néha 125 talán 135 persze 150 szerintem 140
tessék 130 rendben 150 nincs 170 vagyok 160 vagy 160 vagyunk 120 vagytok 100 vannak 150 volna 140 lenne 135 szeretnék 140 tudom 160
tudod 130 tudok 135 akarom 120 akarok 120 megyek 130 jövök 125 jössz 110 látom 125 látlak 110 hallom 110 értem 140 értek 110
dolgozom 120 olvasok 100 írok 110 beszélek 115 hallgatok 90 nézek 105 főzök 90 eszem 115 iszom 115 alszom 100 sétálok 90 futok 90
kedvenc 120 szuper 120 király 115 klassz 110 remek 120 rendes 110 kedves 130 drága 120 aranyos 115 okos 115 buta 100 vicces 105""".split()


def lexicon(tokens):
    return list(zip(tokens[0::2], (int(t) for t in tokens[1::2])))


def main():
    os.makedirs(OUT, exist_ok=True)
    rng = random.Random(20260101)
    with open(os.path.join(OUT, "layouts.tsv"), "w", encoding="utf-8") as f:
        for name in LAYOUTS:
            for c, (x, y, w, h) in sorted(build_layout(name).items()):
                f.write("%s\t%s\t%.1f\t%.1f\t%.1f\t%.1f\n" % (name, c, x, y, w, h))
    lexicons = {"qwerty": lexicon(EN), "qwertz": lexicon(EN), "azerty": lexicon(EN), "hu": lexicon(HU)}
    for name, lex in lexicons.items():
        if name in ("qwertz", "azerty"):  # share the English list; only the hu list is Hungarian
            continue
        with open(os.path.join(OUT, "lexicon_%s.tsv" % name), "w", encoding="utf-8") as f:
            for w, fr in lex:
                f.write("%s\t%d\n" % (w, fr))
    lines = []
    for name, noise, count in (("qwerty", 0.2, 90), ("qwerty", 0.35, 60), ("hu", 0.2, 120), ("hu", 0.35, 60),
                               ("qwertz", 0.2, 30), ("azerty", 0.2, 30)):
        keys = build_layout(name)
        pool = [w for w, _ in lexicons[name] if len(w) >= 3 and all((c in keys or base_letter(c) in keys) for c in w)]
        for word in rng.sample(pool, min(count, len(pool))):
            pts = simulate(word, keys, rng, noise)
            if len(pts) >= 2:
                lines.append("%s\t%.2f\t%s\t%s\n" % (name, noise, word, " ".join("%g,%g" % p for p in pts)))
    with open(os.path.join(OUT, "paths.tsv"), "w", encoding="utf-8") as f:
        f.writelines(lines)
    print("wrote %d paths" % len(lines))


if __name__ == "__main__":
    main()
