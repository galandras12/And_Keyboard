// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.compat.locale
import helium314.keyboard.dictionarypack.DictionaryPackConstants
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.R
import helium314.keyboard.latin.dictionary.ReadOnlyBinaryDictionary
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.makedict.DictionaryHeader
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.DictionaryInstaller
import helium314.keyboard.latin.utils.DictionaryNames
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.ScriptUtils.script
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.settings.DropDownField
import helium314.keyboard.settings.WithSmallTitle
import java.io.File
import java.util.Locale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import helium314.keyboard.latin.RichInputMethodManager

@Composable
fun NewDictionaryDialog(
    onDismissRequest: () -> Unit,
    cachedFile: File,
    mainLocale: Locale?
) {
    val (error, header) = DictionaryInstaller.check(cachedFile)
    if (error != null) {
        InfoDialog(stringResource(error), onDismissRequest)
        cachedFile.delete()
    } else if (header != null) {
        val ctx = LocalContext.current
        val dictLocale = header.mLocaleString.constructLocale()
        val enabledLocales = SubtypeSettings.getEnabledSubtypes().map { it.locale() }
        val enabledLanguages = enabledLocales.map { it.language }
        val comparer = compareBy<Locale>(
            { it != mainLocale },
            { it !in enabledLocales },
            { it != dictLocale },
            { it.language !in enabledLanguages },
            { it.script() != dictLocale.script() }
        )
        val locales = SubtypeSettings.getAvailableSubtypeLocales()
            .filter { it.script() == dictLocale.script() || it.script() == mainLocale?.script() }
            .sortedWith(comparer)
        var locale by remember {
            mutableStateOf(mainLocale
                ?: RichInputMethodManager.getInstance().findSubtypeForHintLocale(dictLocale)?.locale()
                ?: dictLocale.takeIf { it in locales }
                ?: locales.first())
        }
        val cacheDir = DictionaryInfoUtils.getCacheDirectoryForLocale(locale, ctx)
        val dictFile = File(cacheDir, header.mIdString.substringBefore(":") + "_" + DictionaryInfoUtils.USER_DICTIONARY_SUFFIX)
        val type = header.mIdString.substringBefore(":")
        val info = header.info(LocalConfiguration.current.locale())
        var name by remember { mutableStateOf(DictionaryNames.get(ctx.prefs(), dictFile) ?: header.description ?: "") }
        ThreeButtonAlertDialog(
            onDismissRequest = { onDismissRequest(); cachedFile.delete() },
            onConfirmed = { DictionaryInstaller.install(ctx, cachedFile, header, locale, name) },
            confirmButtonText = stringResource(if (dictFile.exists()) R.string.replace_dictionary else android.R.string.ok),
            title = { Text(stringResource(R.string.add_new_dictionary_title)) },
            content = {
                Column {
                    Text(info, Modifier.padding(bottom = 10.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(60) },
                        label = { Text(stringResource(R.string.dictionary_name_label)) },
                        supportingText = { Text(stringResource(R.string.dictionary_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    )
                    WithSmallTitle(stringResource(R.string.button_select_language)) {
                        DropDownField(
                            selectedItem = locale,
                            onSelected = { locale = it },
                            items = locales
                        ) { Text(it.localizedDisplayName(LocalResources.current)) }
                    }
                    if (locale.script() != dictLocale.script()) {
                        // whatever, still allow it if the user wants
                        HorizontalDivider()
                        Text(
                            stringResource(R.string.dictionary_file_wrong_script),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp, top = 4.dp)
                        )
                    }
                    if (dictFile.exists()) {
                        val oldInfo = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(dictFile)?.info(LocalConfiguration.current.locale())
                        HorizontalDivider()
                        Text(
                            stringResource(R.string.replace_dictionary_message, type, oldInfo ?: "(no info)", info),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            scrollContent = true,
        )
    }
}
