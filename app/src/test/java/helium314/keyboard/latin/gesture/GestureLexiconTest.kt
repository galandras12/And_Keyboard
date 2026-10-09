// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gesture

import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.DictionaryCollection
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.ArrayList
import java.util.Locale
import kotlin.system.measureTimeMillis
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeDictionary(type: String, private val words: List<Pair<String, Int>>) : Dictionary(type, Locale.ENGLISH) {
    override fun getSuggestions(composedData: ComposedData, ngramContext: NgramContext, proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion, sessionId: Int, weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray?): ArrayList<SuggestedWordInfo>? = null
    override fun isInDictionary(word: String) = words.any { it.first == word }
    override fun forEachWord(consumer: WordConsumer): Boolean {
        words.forEach { consumer.accept(it.first, it.second, false) }
        return true
    }
}

@RunWith(RobolectricTestRunner::class)
class GestureLexiconTest {
    @Test fun `the main dictionary is a collection and its words are read, but not the emoji search terms`() {
        // the keyboard keeps the main dictionary of a language in a DictionaryCollection together with the emoji dictionary
        val collection = DictionaryCollection(Dictionary.TYPE_MAIN, Locale.ENGLISH, listOf(
            FakeDictionary(Dictionary.TYPE_MAIN, listOf("hello" to 200, "world" to 100)),
            FakeDictionary(Dictionary.TYPE_EMOJI, listOf("smile" to 20)),
        ), floatArrayOf(1f, 1f))
        val words = ArrayList<String>()
        assertTrue(collection.forEachWord { word, _, _ -> words.add(word) })
        assertEquals(listOf("hello", "world"), words)
    }

    @Test fun `lexicon keeps real words only`() {
        val lexicon = GestureLexicon(listOf("hello" to 10, "a" to 10, "x1" to 10, "don't" to 5, "well-known" to 5, "😀" to 3))
        assertEquals(3, lexicon.size)
    }
}
