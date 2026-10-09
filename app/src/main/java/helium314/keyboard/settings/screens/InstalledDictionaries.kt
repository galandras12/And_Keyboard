// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.dictionarypack.DictionaryPackConstants
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.utils.DeleteButton
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.DictionaryInstaller
import helium314.keyboard.latin.utils.DictionaryNames
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import java.io.File
import java.util.Locale

class InstalledDictionary(val file: File, val locale: Locale, val type: String, val name: String)

/** All dictionary files the keyboard has (downloaded or loaded from a file), with the name the user gave them, if any. */
fun listInstalledDictionaries(context: Context): List<InstalledDictionary> {
    val prefs = context.prefs()
    val result = ArrayList<InstalledDictionary>()
    File(DictionaryInfoUtils.getWordListCacheDirectory(context)).listFiles()?.filter { it.isDirectory }?.forEach { dir ->
        val locale = DictionaryInfoUtils.getWordListIdFromFileName(dir.name).constructLocale()
        dir.listFiles()?.filter { it.isFile && it.name.endsWith(".dict") }?.forEach { file ->
            val header = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(file)
            val type = header?.mIdString?.substringBefore(":") ?: file.name.substringBefore("_").substringBefore(".")
            val name = DictionaryNames.get(prefs, file) ?: header?.description?.takeIf { it.isNotBlank() } ?: type
            result.add(InstalledDictionary(file, locale, type, name))
        }
    }
    return result.sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.type }))
}

/** The list of the dictionaries that are installed, each with a button to remove it. */
@Composable
fun InstalledDictionariesList() {
    val ctx = LocalContext.current
    val resources = LocalResources.current
    var refresh by remember { mutableIntStateOf(0) }
    val installedCount = DictionaryInstaller.installCount.intValue // changes when a dictionary was added somewhere else
    val installed = remember(refresh, installedCount) { listInstalledDictionaries(ctx) }
    var toDelete by remember { mutableStateOf<InstalledDictionary?>(null) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.installed_dictionaries_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
        )
        if (installed.isEmpty()) {
            Text(
                stringResource(R.string.installed_dictionaries_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        installed.forEach { dict ->
            val typeName = stringResource(if (dict.type == "emoji") R.string.dictionary_download_type_emoji else R.string.dictionary_download_type_main)
            ListItem(
                headlineContent = { Text(dict.name) },
                supportingContent = {
                    Text("${dict.locale.localizedDisplayName(resources)} · $typeName · ${Formatter.formatShortFileSize(ctx, dict.file.length())}")
                },
                trailingContent = { DeleteButton { toDelete = dict } }
            )
        }
    }
    toDelete?.let { dict ->
        ConfirmationDialog(
            onDismissRequest = { toDelete = null },
            confirmButtonText = stringResource(R.string.remove),
            onConfirmed = {
                DictionaryNames.remove(ctx.prefs(), dict.file)
                dict.file.delete()
                ctx.sendBroadcast(Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION))
                refresh++
            },
            content = { Text(stringResource(R.string.remove_dictionary_message, dict.name)) }
        )
    }
}
