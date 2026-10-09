// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DictionaryAutoDownloaderTest {
    private fun dict(type: String, locale: String, source: AvailableDictionary.Source = AvailableDictionary.Source.AOSP_DICTIONARIES) =
        AvailableDictionary(type, locale, source, "https://example.org/${type}_$locale.dict")

    private fun choose(locale: String, vararg candidates: AvailableDictionary) =
        DictionaryAutoDownloader.chooseMainDictionary(Locale.forLanguageTag(locale), candidates.toList())

    @Test fun `only main dictionaries are chosen`() {
        assertNull(choose("hu", dict("emoji", "hu")))
        assertEquals("main", choose("hu", dict("emoji", "hu"), dict("main", "hu"))?.type)
    }

    @Test fun `dictionary of this project comes before the other sources`() {
        val own = dict("main", "hu", AvailableDictionary.Source.THIS_PROJECT)
        val other = dict("main", "hu")
        val experimental = dict("main", "hu", AvailableDictionary.Source.AOSP_DICTIONARIES_EXPERIMENTAL)
        assertEquals(own, choose("hu", experimental, other, own))
        assertEquals(other, choose("hu", experimental, other))
    }

    @Test fun `exact locale beats the same language`() {
        val gb = dict("main", "en_GB")
        val us = dict("main", "en_US")
        assertEquals(us, choose("en-US", gb, us))
        assertEquals(gb, choose("en-GB", us, gb))
    }

    @Test fun `a dictionary of the language is used if there is none for the country`() {
        val de = dict("main", "de")
        assertEquals(de, choose("de-AT", de))
    }

    @Test fun `other languages are never chosen`() {
        assertNull(choose("hu", dict("main", "de"), dict("main", "en_US")))
    }
}
