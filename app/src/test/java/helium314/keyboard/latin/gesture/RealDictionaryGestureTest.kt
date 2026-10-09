// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gesture

import helium314.keyboard.latin.dictionary.DictionaryCollection
import helium314.keyboard.latin.dictionary.ReadOnlyBinaryDictionary
import org.junit.Assume.assumeTrue
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The decoding chain on a real dictionary file: native dictionary -> DictionaryCollection -> word list -> decoder.
 * Needs the native library built for the host (tools/host-native/build.sh, then run the tests with
 * -Djava.library.path pointing to it) and the dictionary file; skipped without them.
 */
class RealDictionaryGestureTest {
    private fun lines(name: String) = javaClass.getResourceAsStream("/gesture/$name")!!.bufferedReader().readLines().filter { it.isNotBlank() }

    @Test fun `words of a real dictionary file are decoded from the path through their keys`() {
        val dictFile = File("../dictionaries/main_hu.dict")
        val loaded = runCatching { System.loadLibrary("jni_latinime") }
        loaded.exceptionOrNull()?.let { it.printStackTrace(System.out); it.cause?.printStackTrace(System.out) }
        val nativeAvailable = loaded.isSuccess
        assumeTrue("needs the host native library and the dictionary file", nativeAvailable && dictFile.exists())

        val locale = Locale.forLanguageTag("hu")
        val main = ReadOnlyBinaryDictionary(dictFile.absolutePath, 0, dictFile.length(), false, locale, "main")
        assertTrue(main.isValidDictionary)
        val collection = DictionaryCollection("main", locale, listOf(main), floatArrayOf(1f))
        val entries = ArrayList<Pair<String, Int>>()
        assertTrue(collection.forEachWord { word, probability, _ -> entries.add(word to probability) })
        println("words read from the dictionary: ${entries.size}")
        assertTrue(entries.size > 100_000, "only ${entries.size} words")
        val lexicon = GestureLexicon(entries)

        val layout = GestureKeyLayout(lines("layouts.tsv").map { it.split('\t') }.filter { it[0] == "hu" }
            .map { GestureKey(it[1][0], it[2].toFloat(), it[3].toFloat(), it[4].toFloat(), it[5].toFloat()) })
        val decoder = Shark2Decoder()
        var top1 = 0
        var top3 = 0
        val words = listOf("szeretlek", "köszönöm", "hétvége", "ember", "magyar", "billentyűzet", "telefon", "jó", "reggelt", "viszontlátásra", "kávé", "hogy")
            .filter { it.length >= 3 }
        for (word in words) {
            val keys = word.mapNotNull { layout.keyFor(it) }.fold(ArrayList<GestureKey>()) { list, key -> if (list.lastOrNull() !== key) list.add(key); list }
            val result = decoder.decode(keys.map { it.cx }.toFloatArray(), keys.map { it.cy }.toFloatArray(), layout, lexicon, 5).map { it.word.lowercase() }
            println("$word -> $result")
            if (result.firstOrNull() == word) top1++
            if (word in result.take(3)) top3++
        }
        assertTrue(top3 >= words.size - 1, "top3 $top3 of ${words.size}")
        assertTrue(top1 >= words.size - 3, "top1 $top1 of ${words.size}")
    }
}
