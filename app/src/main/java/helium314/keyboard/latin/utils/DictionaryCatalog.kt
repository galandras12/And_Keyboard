// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import java.util.Locale

/** A dictionary file that can be downloaded. [sha256] is only known for files that are hosted with this project. */
data class AvailableDictionary(
    val type: String,
    val localeString: String,
    val source: Source,
    val url: String,
    val sha256: String? = null,
) {
    enum class Source { THIS_PROJECT, AOSP_DICTIONARIES, AOSP_DICTIONARIES_EXPERIMENTAL, AOSP_DICTIONARIES_CLDR }

    val locale: Locale get() = localeString.constructLocale()
}

/**
 * Which dictionaries can be downloaded: the ones hosted in the `dictionaries` folder of this project, and
 * the ones listed in assets/dictionaries_in_dict_repo.csv, which are hosted in the aosp-dictionaries repository.
 */
object DictionaryCatalog {
    private const val OWN_BASE_URL = "https://raw.githubusercontent.com/galandras12/And_Keyboard/main/dictionaries/"

    private val own = listOf(
        AvailableDictionary("main", "hu", AvailableDictionary.Source.THIS_PROJECT, OWN_BASE_URL + "main_hu.dict",
            "5f66d2f4024be80da03da7faa5c234b81784a2363f13c923af53baa8900e18d0"),
        AvailableDictionary("emoji", "hu", AvailableDictionary.Source.THIS_PROJECT, OWN_BASE_URL + "emoji_hu.dict",
            "548caf74fcd239c167c09c34f37876d489b281e080efa8d07a688fcbff681872"),
    )

    /** Dictionaries for [locale] (same language and, if the dictionary has one, region), own ones first. */
    fun availableFor(locale: Locale, context: Context): List<AvailableDictionary> =
        (own + readRepositoryList(context)).filter { LocaleUtils.getMatchLevel(locale, it.locale) >= LocaleUtils.LOCALE_GOOD_MATCH }

    /** All locales that have at least one dictionary to download. */
    fun localesWithDownloads(context: Context): Set<Locale> = (own + readRepositoryList(context)).mapTo(HashSet()) { it.locale }

    private fun readRepositoryList(context: Context): List<AvailableDictionary> {
        val result = ArrayList<AvailableDictionary>()
        context.assets.open("dictionaries_in_dict_repo.csv").reader().forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val (type, localeString, flag) = line.split(",")
            val (source, folder) = when (flag) {
                "cldr" -> AvailableDictionary.Source.AOSP_DICTIONARIES_CLDR to Links.DICTIONARY_EMOJI_CLDR_SUFFIX
                "exp" -> AvailableDictionary.Source.AOSP_DICTIONARIES_EXPERIMENTAL to Links.DICTIONARY_EXPERIMENTAL_SUFFIX
                else -> AvailableDictionary.Source.AOSP_DICTIONARIES to Links.DICTIONARY_NORMAL_SUFFIX
            }
            result.add(AvailableDictionary(type, localeString, source,
                Links.DICTIONARY_URL + Links.DICTIONARY_DOWNLOAD_SUFFIX + folder + type + "_" + localeString.lowercase() + ".dict"))
        }
        return result
    }
}
