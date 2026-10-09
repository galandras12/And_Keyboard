// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Downloads the main dictionary of every enabled language that has none, so the user does not have to do anything.
 * Can be switched off in the dictionary settings. Failed attempts are repeated at most every [RETRY_DELAY_MILLIS].
 */
object DictionaryAutoDownloader {
    private const val RETRY_DELAY_MILLIS = 6L * 60 * 60 * 1000
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val AUTOMATIC_SOURCES = setOf(AvailableDictionary.Source.THIS_PROJECT, AvailableDictionary.Source.AOSP_DICTIONARIES)

    /** Starts the check in the background, unless the setting is off or a check is already running. [showMessage]: tell the user about new dictionaries. */
    @JvmOverloads
    fun requestCheck(context: Context, showMessage: Boolean = false, force: Boolean = false) {
        val appContext = context.applicationContext
        if (!force && !appContext.prefs().getBoolean(Settings.PREF_AUTO_DOWNLOAD_DICTIONARIES, Defaults.PREF_AUTO_DOWNLOAD_DICTIONARIES)) return
        val missing = getEnabledLocalesWithoutDictionary(appContext) // cheap, and reads settings, so do it on the calling thread
        if (missing.isEmpty()) return
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                val installed = downloadMissing(appContext, missing, ignoreRetryDelay = force)
                if (showMessage && installed.isNotEmpty()) {
                    val names = installed.joinToString(", ") { it.getDisplayName(Locale.getDefault()) }
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(appContext, appContext.getString(R.string.dictionary_auto_downloaded, names), Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                running.set(false)
            }
        }
    }

    /**
     * Which dictionary to download for [locale]: a main dictionary of the best matching locale, from this project first. Null if there is none.
     * Only stable dictionaries are chosen automatically; experimental ones can be huge and can be picked by hand.
     */
    fun chooseMainDictionary(locale: Locale, candidates: List<AvailableDictionary>): AvailableDictionary? =
        candidates.filter { it.type == "main" && it.source in AUTOMATIC_SOURCES }
            .map { it to LocaleUtils.getMatchLevel(locale, it.locale) }
            .filter { it.second >= LocaleUtils.LOCALE_GOOD_MATCH }
            .sortedWith(compareByDescending<Pair<AvailableDictionary, Int>> { it.second }.thenBy { sourceRank(it.first.source) })
            .firstOrNull()?.first

    private fun sourceRank(source: AvailableDictionary.Source) = when (source) {
        AvailableDictionary.Source.THIS_PROJECT -> 0
        AvailableDictionary.Source.AOSP_DICTIONARIES -> 1
        AvailableDictionary.Source.AOSP_DICTIONARIES_EXPERIMENTAL -> 2
        AvailableDictionary.Source.AOSP_DICTIONARIES_CLDR -> 3
    }

    /** @return the locales for which a dictionary was installed */
    private fun downloadMissing(context: Context, missing: List<Locale>, ignoreRetryDelay: Boolean): List<Locale> {
        val prefs = context.prefs()
        val attempts = readAttempts(context).toMutableMap()
        val now = System.currentTimeMillis()
        val installed = ArrayList<Locale>()
        for (locale in missing) {
            val last = attempts[locale.toLanguageTag()]
            if (!ignoreRetryDelay && last != null && now - last < RETRY_DELAY_MILLIS) continue
            val dictionary = chooseMainDictionary(locale, DictionaryCatalog.availableFor(locale, context)) ?: continue
            attempts[locale.toLanguageTag()] = now
            val target = File(context.cacheDir, "temp_auto_dict_${locale.toLanguageTag()}")
            val result = DictionaryDownloader.download(dictionary.url, target, dictionary.sha256)
            if (result !is DownloadResult.Success) continue
            val (error, header) = DictionaryInstaller.check(target)
            if (error != null || header == null) { target.delete(); continue }
            DictionaryInstaller.install(context, target, header, locale, header.description ?: "")
            attempts.remove(locale.toLanguageTag())
            installed.add(locale)
        }
        prefs.edit { putString(Settings.PREF_AUTO_DICTIONARY_ATTEMPTS, Json.encodeToString(attempts)) }
        return installed
    }

    private fun readAttempts(context: Context): Map<String, Long> {
        val pref = context.prefs().getString(Settings.PREF_AUTO_DICTIONARY_ATTEMPTS, "").orEmpty()
        if (pref.isEmpty()) return emptyMap()
        return runCatching { Json.decodeFromString<Map<String, Long>>(pref) }.getOrNull() ?: emptyMap()
    }
}
