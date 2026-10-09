// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import androidx.core.content.edit
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import kotlinx.serialization.json.Json

/**
 * How often each emoji was picked, most frequent first. Only stored on the device, in the app preferences,
 * and only the emoji and a count: nothing about the text that is typed.
 */
class EmojiUsageCounter(initial: Map<String, Int> = emptyMap(), private val maxEntries: Int = 200, private val halveAbove: Int = 500) {
    // insertion order is the order of last use, oldest first
    private val counts = LinkedHashMap<String, Int>(initial)

    fun add(emoji: String) {
        if (emoji.isEmpty()) return
        val count = (counts.remove(emoji) ?: 0) + 1
        counts[emoji] = count
        // let old habits fade, so a new favourite can overtake an old one
        if (count > halveAbove) {
            for (entry in counts.entries) entry.setValue((entry.value + 1) / 2)
        }
        while (counts.size > maxEntries) {
            // forget the least used one, the oldest if equal
            val least = counts.entries.minWith(compareBy { it.value })
            counts.remove(least.key)
        }
    }

    fun remove(emoji: String) {
        counts.remove(emoji)
    }

    /** Most frequently used first; emojis that were used equally often are ordered by last use. */
    fun top(n: Int): List<String> =
        counts.entries.withIndex().sortedWith(compareByDescending<IndexedValue<Map.Entry<String, Int>>> { it.value.value }.thenByDescending { it.index })
            .take(n).map { it.value.key }

    fun toMap(): Map<String, Int> = LinkedHashMap(counts)
}

object FrequentEmojis {
    private val prefs = Settings.getCurrentContext().prefs()
    const val MAX_COUNT = 39 // same as the recents

    @JvmStatic
    fun add(emoji: String) {
        val counter = load()
        counter.add(emoji)
        save(counter)
    }

    @JvmStatic
    fun get(): List<String> = load().top(MAX_COUNT)

    @JvmStatic
    fun clear() {
        prefs.edit { remove(Settings.PREF_FREQUENT_EMOJIS) }
    }

    private fun load(): EmojiUsageCounter {
        val pref = prefs.getString(Settings.PREF_FREQUENT_EMOJIS, Defaults.PREF_FREQUENT_EMOJIS)
        if (pref.isNullOrEmpty()) return EmojiUsageCounter()
        return EmojiUsageCounter(runCatching { Json.decodeFromString<Map<String, Int>>(pref) }.getOrNull() ?: emptyMap())
    }

    private fun save(counter: EmojiUsageCounter) {
        prefs.edit { putString(Settings.PREF_FREQUENT_EMOJIS, Json.encodeToString(counter.toMap())) }
    }
}
