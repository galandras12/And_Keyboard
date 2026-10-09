// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.utils.AvailableDictionary
import helium314.keyboard.latin.utils.DictionaryCatalog
import helium314.keyboard.latin.utils.DictionaryDownloader
import helium314.keyboard.latin.utils.DownloadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Lists the dictionaries that can be downloaded for [locale]. A downloaded file is passed to [onDownloaded],
 * which is expected to check it and install it, e.g. with [NewDictionaryDialog].
 */
@Composable
fun DictionaryDownloadList(locale: Locale, onDownloaded: (File) -> Unit) {
    val ctx = LocalContext.current
    val dictionaries = remember(locale) { DictionaryCatalog.availableFor(locale, ctx) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (dictionaries.isEmpty()) {
            Text(stringResource(R.string.dictionary_download_none), style = MaterialTheme.typography.bodyMedium)
        } else {
            dictionaries.forEach { DictionaryDownloadRow(it, onDownloaded) }
            Text(
                stringResource(R.string.dictionary_download_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private sealed interface DownloadState {
    data object Idle : DownloadState
    class Running(val fraction: Float?) : DownloadState // null: unknown size
    class Failed(val message: String) : DownloadState
}

@Composable
private fun DictionaryDownloadRow(dictionary: AvailableDictionary, onDownloaded: (File) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember(dictionary) { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var cancelled by remember(dictionary) { mutableStateOf(false) }
    val typeName = stringResource(if (dictionary.type == "emoji") R.string.dictionary_download_type_emoji else R.string.dictionary_download_type_main)
    val sourceName = stringResource(when (dictionary.source) {
        AvailableDictionary.Source.THIS_PROJECT ->
            if (dictionary.type == "emoji") R.string.dictionary_download_source_this_project_emoji else R.string.dictionary_download_source_this_project
        AvailableDictionary.Source.AOSP_DICTIONARIES -> R.string.dictionary_download_source_aosp
        AvailableDictionary.Source.AOSP_DICTIONARIES_EXPERIMENTAL -> R.string.dictionary_download_source_aosp_experimental
        AvailableDictionary.Source.AOSP_DICTIONARIES_CLDR -> R.string.dictionary_download_source_aosp_cldr
    })
    val errors = mapOf(
        DownloadResult.Reason.INSECURE_URL to stringResource(R.string.dictionary_download_error_insecure),
        DownloadResult.Reason.NETWORK to stringResource(R.string.dictionary_download_error_network),
        DownloadResult.Reason.TOO_LARGE to stringResource(R.string.dictionary_download_error_too_large),
        DownloadResult.Reason.CHECKSUM to stringResource(R.string.dictionary_download_error_checksum),
    )
    val statusErrorFormat = stringResource(R.string.dictionary_download_error_status, "%s")
    val failedFormat = stringResource(R.string.dictionary_download_failed, "%s")

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text("$typeName · ${dictionary.locale.localizedDisplayName(LocalResources.current)}", style = MaterialTheme.typography.titleSmall)
                Text(sourceName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (state) {
                is DownloadState.Running -> OutlinedButton({ cancelled = true }) { Text(stringResource(R.string.dictionary_download_cancel)) }
                else -> FilledTonalButton({
                    cancelled = false
                    state = DownloadState.Running(null)
                    scope.launch {
                        val target = File(ctx.cacheDir, "temp_dict_${dictionary.type}_${dictionary.localeString}") // one file per row, rows can download at once
                        val result = withContext(Dispatchers.IO) {
                            DictionaryDownloader.download(dictionary.url, target, dictionary.sha256, isCancelled = { cancelled }) { done, total ->
                                state = DownloadState.Running(if (total > 0) done.toFloat() / total else null)
                            }
                        }
                        when (result) {
                            is DownloadResult.Success -> { state = DownloadState.Idle; onDownloaded(result.file) }
                            is DownloadResult.Failure -> state = if (result.reason == DownloadResult.Reason.CANCELLED) DownloadState.Idle else {
                                val reason = if (result.reason == DownloadResult.Reason.HTTP_STATUS) statusErrorFormat.format(result.detail ?: "")
                                    else errors.getValue(result.reason)
                                DownloadState.Failed(failedFormat.format(reason))
                            }
                        }
                    }
                }) { Text(stringResource(R.string.dictionary_download_button)) }
            }
        }
        when (val s = state) {
            is DownloadState.Running -> {
                val modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                if (s.fraction == null) LinearProgressIndicator(modifier = modifier)
                else LinearProgressIndicator(progress = { s.fraction }, modifier = modifier)
            }
            is DownloadState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            DownloadState.Idle -> {}
        }
    }
}
