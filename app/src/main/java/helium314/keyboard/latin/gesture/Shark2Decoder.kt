// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gesture

import java.text.Normalizer
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Open source glide typing decoder, following the SHARK2 idea (Kristensson & Zhai, 2004):
 * every word has an ideal path through the centers of its letter keys, and a gesture is matched
 * against these paths with two channels,
 *  - shape: the paths are normalized for position and size and compared point by point,
 *  - location: the raw paths are compared, where the part of the distance that lies within the
 *    key radius ("tunneling") is free and the ends are weighted less than the middle.
 * Both costs are combined with the word frequency to rank the candidates.
 * Candidates are pruned by the keys near the start and the end of the gesture.
 *
 * This file has no Android dependencies, so it can be tested on the JVM.
 */

class GestureKey(val letter: Char, val cx: Float, val cy: Float, val width: Float, val height: Float)

/** Positions of the letter keys in keyboard coordinates, the same coordinates the gesture uses. */
class GestureKeyLayout(keys: Collection<GestureKey>) {
    val keys: List<GestureKey>
    val averageKeyWidth: Float
    private val byLetter = HashMap<Char, GestureKey>()
    private val folded = HashMap<Char, Char>()

    init {
        for (key in keys) byLetter.putIfAbsent(key.letter.lowercaseChar(), key)
        this.keys = byLetter.values.toList()
        averageKeyWidth = if (this.keys.isEmpty()) 1f else this.keys.map { it.width }.average().toFloat()
    }

    /** The key used to type [c]; letters that have no key of their own (e.g. ő on a layout without it) use their base letter. */
    fun keyFor(c: Char): GestureKey? {
        val lower = c.lowercaseChar()
        byLetter[lower]?.let { return it }
        val base = folded.getOrPut(lower) { foldDiacritics(lower) }
        return if (base != lower) byLetter[base] else null
    }

    private fun foldDiacritics(c: Char): Char {
        val decomposed = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
        return decomposed.firstOrNull { it.isLetter() } ?: c
    }
}

/**
 * Word list with frequencies (0..255, higher is more frequent); words are indexed by first and last letter.
 * Kept small, as dictionaries can have hundreds of thousands of words: only the word and its frequency are stored.
 */
class GestureLexicon(entries: Iterable<Pair<String, Int>>) {
    class Entry(val word: String, val frequency: Int) {
        /** the lowercase letters of the word, without apostrophes and hyphens */
        val letters: String get() = word.lowercase().filter { it.isLetter() }
    }

    private val buckets = HashMap<Int, ArrayList<Entry>>()
    var size = 0
        private set

    init {
        for ((word, frequency) in entries) {
            var first = ' '
            var last = ' '
            var letterCount = 0
            var valid = true
            for (c in word) {
                if (c.isLetter()) {
                    if (letterCount == 0) first = c.lowercaseChar()
                    last = c.lowercaseChar()
                    letterCount++
                } else if (c != '\'' && c != '-') {
                    valid = false
                    break
                }
            }
            if (!valid || letterCount < 2 || letterCount > MAX_WORD_LENGTH) continue
            buckets.getOrPut(bucketKey(first, last)) { ArrayList() }.add(Entry(word, frequency))
            size++
        }
    }

    /** All entries whose first and last letter satisfy the given conditions. */
    fun forEachBucket(action: (first: Char, last: Char, entries: List<Entry>) -> Unit) {
        for ((key, list) in buckets) action((key ushr 16).toChar(), (key and 0xFFFF).toChar(), list)
    }

    private fun bucketKey(first: Char, last: Char) = (first.code shl 16) or last.code

    companion object {
        const val MAX_WORD_LENGTH = 40
    }
}

class Shark2Decoder(private val config: Config = Config()) {
    class Config(
        /** points that gesture and word paths are resampled to for the final comparison */
        val samples: Int = 48,
        /** points for the fast first pass */
        val coarseSamples: Int = 16,
        /** candidates that go from the fast first pass to the full comparison */
        val shortlist: Int = 150,
        /** a word may start/end at keys up to this many key sizes away from the first/last gesture point */
        val endpointRadius: Float = 1.4f,
        /** gestures shorter than this many key widths are taps, not gestures */
        val minLengthInKeyWidths: Float = 0.6f,
        val shapeWeight: Float = 1f,
        val locationWeight: Float = 0.25f,
        /** how much a frequency of 255 improves the cost over a frequency of 0 */
        val frequencyWeight: Float = 0.12f,
        /** radius of the free zone around the path, in key widths */
        val tunnelRadius: Float = 0.5f,
    )

    class Candidate(val word: String, val cost: Float, val shapeCost: Float, val locationCost: Float, val frequency: Int)

    /** Returns the best [maxResults] words for the gesture, lowest cost first. Empty if the gesture is too short or nothing matches. */
    fun decode(xs: FloatArray, ys: FloatArray, layout: GestureKeyLayout, lexicon: GestureLexicon, maxResults: Int): List<Candidate> {
        if (xs.size != ys.size || xs.size < 2 || layout.keys.isEmpty()) return emptyList()
        val gesture = dedupe(xs, ys)
        if (gesture.size < 2) return emptyList()
        val keyWidth = layout.averageKeyWidth
        if (pathLength(gesture) < config.minLengthInKeyWidths * keyWidth) return emptyList()

        val startKeys = keysNear(layout, gesture.first()[0], gesture.first()[1])
        val endKeys = keysNear(layout, gesture.last()[0], gesture.last()[1])
        if (startKeys.isEmpty() || endKeys.isEmpty()) return emptyList()

        val coarseGesture = normalizeShape(resample(gesture, config.coarseSamples)) ?: return emptyList()
        val fineGesture = resample(gesture, config.samples)
        val fineGestureShape = normalizeShape(fineGesture) ?: return emptyList()

        // fast pass: shape distance on few points for all words that start and end near the gesture
        class Coarse(val entry: GestureLexicon.Entry, val template: List<FloatArray>, val distance: Float)
        val coarse = ArrayList<Coarse>()
        lexicon.forEachBucket { first, last, entries ->
            val firstKey = layout.keyFor(first) ?: return@forEachBucket
            val lastKey = layout.keyFor(last) ?: return@forEachBucket
            if (firstKey !in startKeys || lastKey !in endKeys) return@forEachBucket
            for (entry in entries) {
                val template = templateFor(entry.letters, layout) ?: continue
                val shape = normalizeShape(resample(template, config.coarseSamples)) ?: continue
                coarse.add(Coarse(entry, template, meanDistance(coarseGesture, shape)))
            }
        }
        coarse.sortBy { it.distance }

        val results = ArrayList<Candidate>()
        for (c in coarse.take(config.shortlist)) {
            val fineTemplate = resample(c.template, config.samples)
            val shapeTemplate = normalizeShape(fineTemplate) ?: continue
            val shapeCost = meanDistance(fineGestureShape, shapeTemplate)
            val locationCost = locationCost(fineGesture, fineTemplate, keyWidth)
            val frequencyBonus = config.frequencyWeight * c.entry.frequency / 255f
            val cost = config.shapeWeight * shapeCost + config.locationWeight * locationCost - frequencyBonus
            results.add(Candidate(c.entry.word, cost, shapeCost, locationCost, c.entry.frequency))
        }
        results.sortBy { it.cost }
        val seen = HashSet<String>()
        return results.filter { seen.add(it.word.lowercase()) }.take(maxResults) // "rend" and "Rend" are one suggestion
    }

    private fun keysNear(layout: GestureKeyLayout, x: Float, y: Float): Set<GestureKey> {
        val result = HashSet<GestureKey>()
        for (key in layout.keys) {
            val distance = hypot((x - key.cx) / key.width, (y - key.cy) / key.height)
            if (distance <= config.endpointRadius) result.add(key)
        }
        return result
    }

    /** Ideal path of a word: the key centers of its letters, repeated keys (e.g. "ll") count once. */
    private fun templateFor(letters: String, layout: GestureKeyLayout): List<FloatArray>? {
        val points = ArrayList<FloatArray>(letters.length)
        var previous: GestureKey? = null
        for (c in letters) {
            val key = layout.keyFor(c) ?: return null
            if (key !== previous) points.add(floatArrayOf(key.cx, key.cy))
            previous = key
        }
        return if (points.size >= 2) points else null
    }

    /**
     * Location channel: mean distance between the raw paths, where everything within the tunnel
     * radius is free and the ends of the path count half as much as the middle.
     */
    private fun locationCost(gesture: List<FloatArray>, template: List<FloatArray>, keyWidth: Float): Float {
        val last = (gesture.size - 1).toFloat()
        var sum = 0f
        var weights = 0f
        for (i in gesture.indices) {
            val weight = 0.5f + 0.5f * sin(Math.PI.toFloat() * i / last)
            val distance = hypot(gesture[i][0] - template[i][0], gesture[i][1] - template[i][1])
            sum += weight * max(0f, distance / keyWidth - config.tunnelRadius)
            weights += weight
        }
        return sum / weights
    }

    companion object {
        private fun dedupe(xs: FloatArray, ys: FloatArray): List<FloatArray> {
            val result = ArrayList<FloatArray>(xs.size)
            for (i in xs.indices) {
                val last = result.lastOrNull()
                if (last == null || hypot(xs[i] - last[0], ys[i] - last[1]) >= 0.5f) result.add(floatArrayOf(xs[i], ys[i]))
            }
            return result
        }

        fun pathLength(points: List<FloatArray>): Float {
            var length = 0f
            for (i in 1 until points.size) length += hypot(points[i][0] - points[i - 1][0], points[i][1] - points[i - 1][1])
            return length
        }

        /** [n] points at equal distances along the path. */
        fun resample(points: List<FloatArray>, n: Int): List<FloatArray> {
            val total = pathLength(points)
            val result = ArrayList<FloatArray>(n)
            if (total == 0f) {
                repeat(n) { result.add(points[0].copyOf()) }
                return result
            }
            val step = total / (n - 1)
            var segment = 0
            var segmentStart = 0f // path length at the start of the current segment
            for (i in 0 until n) {
                val target = min(i * step, total)
                while (segment < points.size - 2) {
                    val segmentLength = hypot(points[segment + 1][0] - points[segment][0], points[segment + 1][1] - points[segment][1])
                    if (segmentStart + segmentLength >= target) break
                    segmentStart += segmentLength
                    segment++
                }
                val a = points[segment]
                val b = points[segment + 1]
                val segmentLength = hypot(b[0] - a[0], b[1] - a[1])
                val t = if (segmentLength == 0f) 0f else ((target - segmentStart) / segmentLength).coerceIn(0f, 1f)
                result.add(floatArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
            }
            return result
        }

        /** Shape channel normalization: centroid to the origin, larger side of the bounding box to 1. Null for degenerate paths. */
        fun normalizeShape(points: List<FloatArray>): List<FloatArray>? {
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            var cx = 0f; var cy = 0f
            for (p in points) {
                minX = min(minX, p[0]); maxX = max(maxX, p[0])
                minY = min(minY, p[1]); maxY = max(maxY, p[1])
                cx += p[0]; cy += p[1]
            }
            cx /= points.size; cy /= points.size
            val scale = max(maxX - minX, maxY - minY)
            if (scale < 1e-3f) return null
            return points.map { floatArrayOf((it[0] - cx) / scale, (it[1] - cy) / scale) }
        }

        fun meanDistance(a: List<FloatArray>, b: List<FloatArray>): Float {
            var sum = 0f
            for (i in a.indices) sum += hypot(a[i][0] - b[i][0], a[i][1] - b[i][1])
            return sum / a.size
        }
    }
}
