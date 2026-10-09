// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.SharedPreferences
import androidx.core.content.edit
import helium314.keyboard.latin.settings.Settings
import kotlinx.serialization.json.Json
import java.io.File

/** Names that the user gave to dictionary files (the files themselves are called main_user.dict and so on). */
object DictionaryNames {
    private fun key(dictFile: File) = "${dictFile.parentFile?.name}/${dictFile.name}"

    fun get(prefs: SharedPreferences, dictFile: File): String? = read(prefs)[key(dictFile)]

    /** Sets the name of the dictionary file, a blank name removes it. */
    fun set(prefs: SharedPreferences, dictFile: File, name: String) {
        val names = read(prefs).toMutableMap()
        if (name.isBlank()) names.remove(key(dictFile)) else names[key(dictFile)] = name.trim()
        write(prefs, names)
    }

    fun remove(prefs: SharedPreferences, dictFile: File) = set(prefs, dictFile, "")

    private fun read(prefs: SharedPreferences): Map<String, String> {
        val pref = prefs.getString(Settings.PREF_DICTIONARY_NAMES, "").orEmpty()
        if (pref.isEmpty()) return emptyMap()
        return runCatching { Json.decodeFromString<Map<String, String>>(pref) }.getOrNull() ?: emptyMap()
    }

    private fun write(prefs: SharedPreferences, names: Map<String, String>) {
        prefs.edit { putString(Settings.PREF_DICTIONARY_NAMES, Json.encodeToString(names)) }
    }
}
