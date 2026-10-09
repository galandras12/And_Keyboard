// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.emoji

import kotlin.test.Test
import kotlin.test.assertEquals

class EmojiUsageCounterTest {
    @Test fun `most used emoji comes first`() {
        val counter = EmojiUsageCounter()
        listOf("😀", "😂", "😂", "👍", "😂", "👍").forEach { counter.add(it) }
        assertEquals(listOf("😂", "👍", "😀"), counter.top(10))
        assertEquals(listOf("😂", "👍"), counter.top(2))
    }

    @Test fun `equally used emojis are ordered by last use`() {
        val counter = EmojiUsageCounter()
        listOf("a", "b", "c").forEach { counter.add(it) }
        assertEquals(listOf("c", "b", "a"), counter.top(3))
        counter.add("a")
        assertEquals("a", counter.top(1).single())
    }

    @Test fun `counts survive saving and loading`() {
        val counter = EmojiUsageCounter()
        listOf("x", "y", "y").forEach { counter.add(it) }
        assertEquals(listOf("y", "x"), EmojiUsageCounter(counter.toMap()).top(5))
    }

    @Test fun `least used emoji is forgotten when there are too many`() {
        val counter = EmojiUsageCounter(maxEntries = 3)
        listOf("a", "a", "b", "b", "c", "d").forEach { counter.add(it) }
        assertEquals(setOf("a", "b", "d"), counter.top(10).toSet()) // c was the least used one (and older than d)
    }

    @Test fun `a new favourite overtakes an old one after counts are halved`() {
        val counter = EmojiUsageCounter(halveAbove = 10)
        repeat(11) { counter.add("old") } // 11 > 10: halved to 6
        repeat(7) { counter.add("new") }
        assertEquals("new", counter.top(1).single())
    }

    @Test fun `removed and empty emojis are not counted`() {
        val counter = EmojiUsageCounter()
        counter.add("")
        counter.add("a")
        counter.remove("a")
        assertEquals(emptyList(), counter.top(5))
    }
}
