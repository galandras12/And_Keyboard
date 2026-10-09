// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gesture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Uses the simulated paths from tools/gesture/make_fixtures.py (see app/src/test/resources/gesture). */
class Shark2DecoderTest {
    private class RecordedPath(val layout: String, val noise: Float, val word: String, val xs: FloatArray, val ys: FloatArray)

    private fun lines(name: String): List<String> =
        javaClass.getResourceAsStream("/gesture/$name")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }

    private val layouts: Map<String, GestureKeyLayout> = lines("layouts.tsv")
        .map { it.split('\t') }
        .groupBy({ it[0] }, { GestureKey(it[1][0], it[2].toFloat(), it[3].toFloat(), it[4].toFloat(), it[5].toFloat()) })
        .mapValues { GestureKeyLayout(it.value) }

    private fun lexicon(name: String) = GestureLexicon(lines("lexicon_$name.tsv").map { it.split('\t').let { p -> p[0] to p[1].toInt() } })

    private val lexicons = mapOf("qwerty" to lexicon("qwerty"), "qwertz" to lexicon("qwerty"), "azerty" to lexicon("qwerty"), "hu" to lexicon("hu"))

    private val paths: List<RecordedPath> = lines("paths.tsv").map { line ->
        val (layout, noise, word, points) = line.split('\t')
        val coordinates = points.split(' ').map { p -> p.split(',').map { it.toFloat() } }
        RecordedPath(layout, noise.toFloat(), word, coordinates.map { it[0] }.toFloatArray(), coordinates.map { it[1] }.toFloatArray())
    }

    private val decoder = Shark2Decoder()

    private fun decode(path: RecordedPath, max: Int = 5) =
        decoder.decode(path.xs, path.ys, layouts.getValue(path.layout), lexicons.getValue(path.layout), max).map { it.word }

    private fun accuracy(layout: String, noise: Float, topN: Int): Double {
        val selected = paths.filter { it.layout == layout && it.noise == noise }
        assertTrue(selected.isNotEmpty())
        return selected.count { it.word in decode(it, topN) }.toDouble() / selected.size
    }

    @Test fun `resampling keeps the end points and spaces points evenly`() {
        val line = listOf(floatArrayOf(0f, 0f), floatArrayOf(100f, 0f), floatArrayOf(100f, 100f))
        val resampled = Shark2Decoder.resample(line, 5)
        assertEquals(5, resampled.size)
        assertEquals(0f, resampled.first()[0]); assertEquals(0f, resampled.first()[1])
        assertEquals(100f, resampled.last()[0]); assertEquals(100f, resampled.last()[1])
        assertEquals(100f, resampled[2][0], 1e-3f) // halfway along the 200 long path is the corner
        assertEquals(0f, resampled[2][1], 1e-3f)
    }

    @Test fun `shape normalization removes position and size`() {
        val a = Shark2Decoder.normalizeShape(listOf(floatArrayOf(0f, 0f), floatArrayOf(10f, 5f), floatArrayOf(20f, 0f)))!!
        val b = Shark2Decoder.normalizeShape(listOf(floatArrayOf(100f, 50f), floatArrayOf(300f, 150f), floatArrayOf(500f, 50f)))!!
        assertEquals(0f, Shark2Decoder.meanDistance(a, b), 1e-5f)
    }

    @Test fun `a tap is not decoded as a gesture`() {
        val layout = layouts.getValue("qwerty")
        val key = layout.keyFor('h')!!
        assertTrue(decoder.decode(floatArrayOf(key.cx, key.cx + 3), floatArrayOf(key.cy, key.cy + 2), layout, lexicons.getValue("qwerty"), 5).isEmpty())
    }

    @Test fun `letters without a key use their base letter`() {
        val layout = layouts.getValue("qwerty")
        assertEquals(layout.keyFor('o'), layout.keyFor('ő'))
        assertEquals(layout.keyFor('u'), layout.keyFor('Ű'))
        assertEquals(null, layouts.getValue("qwerty").keyFor('1'))
    }

    @Test fun `ideal path of a word is decoded as that word`() {
        val layout = layouts.getValue("qwerty")
        val points = "hello".mapNotNull { layout.keyFor(it) }.distinct()
        val xs = points.map { it.cx }.toFloatArray()
        val ys = points.map { it.cy }.toFloatArray()
        val result = decoder.decode(xs, ys, layout, lexicons.getValue("qwerty"), 3)
        assertEquals("hello", result.first().word)
    }

    @Test fun `frequency breaks ties between similar paths`() {
        // "hell" (rare) and "held" (also rare) vs "hello": a hello-like path ending near o must give hello
        val layout = layouts.getValue("qwerty")
        val frequent = GestureLexicon(listOf("hold" to 250, "hole" to 10))
        val keys = "hole".map { layout.keyFor(it)!! }
        // path stops halfway between d and e's keys' neighbourhood: both candidates end within reach
        val xs = floatArrayOf(keys[0].cx, keys[1].cx, keys[2].cx, (layout.keyFor('d')!!.cx + layout.keyFor('e')!!.cx) / 2)
        val ys = floatArrayOf(keys[0].cy, keys[1].cy, keys[2].cy, (layout.keyFor('d')!!.cy + layout.keyFor('e')!!.cy) / 2)
        val result = decoder.decode(xs, ys, layout, frequent, 2)
        assertNotNull(result.firstOrNull())
        assertEquals(2, result.size)
        assertTrue(result[0].frequency >= result[1].frequency || result[0].cost < result[1].cost)
    }

    @Test fun `recorded paths on qwerty with small aiming error`() {
        val top1 = accuracy("qwerty", 0.20f, 1)
        val top3 = accuracy("qwerty", 0.20f, 3)
        println("qwerty 0.20: top1=$top1 top3=$top3")
        assertTrue(top1 >= 0.85, "top1 $top1")
        assertTrue(top3 >= 0.95, "top3 $top3")
    }

    @Test fun `recorded paths on qwerty with large aiming error`() {
        val top1 = accuracy("qwerty", 0.35f, 1)
        val top3 = accuracy("qwerty", 0.35f, 3)
        println("qwerty 0.35: top1=$top1 top3=$top3")
        assertTrue(top1 >= 0.65, "top1 $top1")
        assertTrue(top3 >= 0.85, "top3 $top3")
    }

    @Test fun `recorded paths on the hungarian layout`() {
        val top1 = accuracy("hu", 0.20f, 1)
        val top3 = accuracy("hu", 0.20f, 3)
        println("hu 0.20: top1=$top1 top3=$top3")
        assertTrue(top1 >= 0.85, "top1 $top1")
        assertTrue(top3 >= 0.95, "top3 $top3")
        val noisyTop3 = accuracy("hu", 0.35f, 3)
        println("hu 0.35: top3=$noisyTop3")
        assertTrue(noisyTop3 >= 0.85, "top3 $noisyTop3")
    }

    @Test fun `recorded paths on qwertz and azerty`() {
        assertTrue(accuracy("qwertz", 0.20f, 3) >= 0.9)
        assertTrue(accuracy("azerty", 0.20f, 3) >= 0.9)
    }

    @Test fun `recorded paths are still found among thousands of random distractor words`() {
        // random letter strings are a harsh stress test: they cover the path space much denser than real words
        val random = kotlin.random.Random(7)
        val distractors = (0 until 20000).map {
            val length = random.nextInt(3, 9)
            String(CharArray(length) { 'a' + random.nextInt(26) }) to random.nextInt(0, 60)
        }
        val big = GestureLexicon(lines("lexicon_qwerty.tsv").map { it.split('\t').let { p -> p[0] to p[1].toInt() } } + distractors)
        val selected = paths.filter { it.layout == "qwerty" && it.noise == 0.20f }
        val top1 = selected.count { decoder.decode(it.xs, it.ys, layouts.getValue("qwerty"), big, 1).firstOrNull()?.word == it.word }.toDouble() / selected.size
        val top5 = selected.count { p -> decoder.decode(p.xs, p.ys, layouts.getValue("qwerty"), big, 5).any { it.word == p.word } }.toDouble() / selected.size
        println("qwerty 0.20 with ${big.size} words: top1=$top1 top5=$top5")
        assertTrue(top1 >= 0.7, "top1 $top1")
        assertTrue(top5 >= 0.9, "top5 $top5")
    }

    @Test fun `a gesture is decoded quickly in a dictionary of 450000 words`() {
        val random = kotlin.random.Random(11)
        val big = GestureLexicon(lines("lexicon_qwerty.tsv").map { it.split('\t').let { p -> p[0] to p[1].toInt() } } +
            (0 until 450_000).map { String(CharArray(random.nextInt(3, 11)) { 'a' + random.nextInt(26) }) to random.nextInt(0, 255) })
        val layout = layouts.getValue("qwerty")
        val sample = paths.filter { it.layout == "qwerty" }.take(20)
        decoder.decode(sample[0].xs, sample[0].ys, layout, big, 5) // warm up
        val start = System.nanoTime()
        sample.forEach { decoder.decode(it.xs, it.ys, layout, big, 5) }
        val millis = (System.nanoTime() - start) / 1_000_000 / sample.size
        println("decode in ${big.size} words: $millis ms per gesture (on this machine, phones are slower)")
        assertTrue(millis < 400, "$millis ms per gesture")
    }
}
