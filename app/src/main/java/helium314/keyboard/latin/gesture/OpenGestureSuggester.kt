// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.gesture

import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.dictionary.Dictionary
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Glide typing without the closed source library: connects [Shark2Decoder] to the keyboard and to the main dictionaries.
 * The word list of a dictionary is read once (on the first gesture) and then kept for as long as the dictionary lives.
 */
object OpenGestureSuggester {
    private const val MAX_RESULTS = 10
    private const val TAG = "OpenGestureSuggester"

    private val decoder = Shark2Decoder()
    private val lexicons = WeakHashMap<Dictionary, Array<GestureLexicon?>>() // index 0: all words, 1: without offensive words
    @Volatile private var cachedLayout: Pair<WeakReference<Keyboard>, GestureKeyLayout>? = null
    /** The biggest dictionaries have millions of words; the rarest ones are left out to keep memory use and decoding time in check. */
    private const val MAX_LEXICON_WORDS = 600_000

    /** Suggestions for the gesture in [composedData], best first; null if the dictionary can't be used for it. */
    fun suggest(
        dictionary: Dictionary, composedData: ComposedData, keyboard: Keyboard, blockOffensive: Boolean, weightForLocale: Float
    ): List<SuggestedWordInfo>? {
        val lexicon = lexiconFor(dictionary, blockOffensive) ?: return null
        val layout = layoutFor(keyboard)
        val pointers = composedData.mInputPointers
        val size = pointers.pointerSize
        val xs = FloatArray(size) { pointers.xCoordinates[it].toFloat() }
        val ys = FloatArray(size) { pointers.yCoordinates[it].toFloat() }
        return decoder.decode(xs, ys, layout, lexicon, MAX_RESULTS).map {
            // native scores are large positive numbers, higher is better; keep the order of the decoder cost
            val score = ((2_000_000f - it.cost * 1_000_000f) * weightForLocale).toInt()
            SuggestedWordInfo(it.word, "", score, SuggestedWordInfo.KIND_CORRECTION, dictionary,
                SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE)
        }
    }

    @Synchronized
    private fun lexiconFor(dictionary: Dictionary, blockOffensive: Boolean): GestureLexicon? {
        val index = if (blockOffensive) 1 else 0
        val cached = lexicons.getOrPut(dictionary) { arrayOfNulls(2) }
        cached[index]?.let { return it }
        val entries = ArrayList<Pair<String, Int>>()
        val readable = dictionary.forEachWord { word, probability, offensive ->
            if (!(blockOffensive && offensive)) entries.add(word to probability.coerceIn(0, 255))
        }
        if (!readable) return null
        if (entries.size > MAX_LEXICON_WORDS) {
            entries.sortByDescending { it.second }
            entries.subList(MAX_LEXICON_WORDS, entries.size).clear()
        }
        return GestureLexicon(entries).also { cached[index] = it }
    }

    /** Reads the word list of the dictionary already, so the first gesture does not have to wait for it. */
    fun prewarm(dictionary: Dictionary, blockOffensive: Boolean) {
        lexiconFor(dictionary, blockOffensive)
    }

    private fun layoutFor(keyboard: Keyboard): GestureKeyLayout {
        cachedLayout?.let { if (it.first.get() === keyboard) return it.second }
        return layoutOf(keyboard).also { cachedLayout = WeakReference(keyboard) to it }
    }

    /** The letter keys of the keyboard, in the coordinates the gesture points are in. */
    internal fun layoutOf(keyboard: Keyboard): GestureKeyLayout =
        GestureKeyLayout(keyboard.sortedKeys.filter { !it.isSpacer && Character.isLetter(it.code) }.map {
            GestureKey(Character.toChars(it.code)[0], it.x + it.width / 2f, it.y + it.height / 2f, it.width.toFloat(), it.height.toFloat())
        })
}
