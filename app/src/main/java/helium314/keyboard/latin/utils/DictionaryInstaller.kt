// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.content.Intent
import helium314.keyboard.dictionarypack.DictionaryPackConstants
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.dictionary.ReadOnlyBinaryDictionary
import helium314.keyboard.latin.makedict.DictionaryHeader
import java.io.File
import java.util.Locale

/** Checks dictionary files and puts them where the keyboard looks for user dictionaries. */
object DictionaryInstaller {
    /** Goes up whenever a dictionary was installed, so lists of dictionaries can update themselves. */
    val installCount = androidx.compose.runtime.mutableIntStateOf(0)

    /** @return an error message resource, or the header of the valid dictionary file */
    fun check(file: File): Pair<Int?, DictionaryHeader?> {
        val newHeader = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(file)
            ?: return R.string.dictionary_file_error to null
        val locale = newHeader.mLocaleString.constructLocale()
        val dict = ReadOnlyBinaryDictionary(file.absolutePath, 0, file.length(), false, locale, "test")
        if (!dict.isValidDictionary) {
            dict.close()
            return R.string.dictionary_load_error to null
        }
        dict.close()
        return null to newHeader
    }

    fun targetFile(context: Context, locale: Locale, header: DictionaryHeader): File {
        val cacheDir = DictionaryInfoUtils.getCacheDirectoryForLocale(locale, context)
        return File(cacheDir, header.mIdString.substringBefore(":") + "_" + DictionaryInfoUtils.USER_DICTIONARY_SUFFIX)
    }

    /** Moves the checked [cachedFile] to the dictionary folder of [locale], replacing a dictionary of the same type, and lets the keyboard reload. */
    fun install(context: Context, cachedFile: File, header: DictionaryHeader, locale: Locale, name: String): File {
        val dictFile = targetFile(context, locale, header)
        dictFile.parentFile?.mkdirs()
        dictFile.delete()
        if (!cachedFile.renameTo(dictFile)) { // e.g. when the cache folder is on another file system
            cachedFile.copyTo(dictFile, overwrite = true)
            cachedFile.delete()
        }
        DictionaryNames.set(context.prefs(), dictFile, name)
        if (header.mIdString.substringBefore(":") == "main") {
            // replaced main dict, remove the one created from internal data
            File(dictFile.parentFile, DictionaryInfoUtils.MAIN_DICT_FILE_NAME).delete()
        }
        context.sendBroadcast(Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION))
        installCount.intValue++
        return dictFile
    }
}
