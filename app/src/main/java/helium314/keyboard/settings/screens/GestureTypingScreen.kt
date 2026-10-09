// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import helium314.keyboard.keyboard.PointerTracker
import helium314.keyboard.latin.gesture.GestureDiagnostics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.utils.DictionaryAutoDownloader
import helium314.keyboard.latin.utils.getEnabledLocalesWithoutDictionary
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.settings.initPreview
import helium314.keyboard.latin.utils.previewDark
import androidx.core.content.edit

@Composable
fun GestureTypingScreen(
    onClickBack: () -> Unit,
) {
    val prefs = LocalContext.current.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val gestureFloatingPreviewEnabled = prefs.getBoolean(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT, Defaults.PREF_GESTURE_FLOATING_PREVIEW_TEXT)
    val gestureEnabled = prefs.getBoolean(Settings.PREF_GESTURE_INPUT, Defaults.PREF_GESTURE_INPUT)
    val items = listOf(
        Settings.PREF_GESTURE_INPUT,
        if (gestureEnabled)
            Settings.PREF_GESTURE_PREVIEW_TRAIL else null,
        if (gestureEnabled)
            Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT else null,
        if (gestureEnabled && gestureFloatingPreviewEnabled)
            Settings.PREF_GESTURE_FLOATING_PREVIEW_DYNAMIC else null,
        if (gestureEnabled)
            Settings.PREF_GESTURE_SPACE_AWARE else null,
        if (gestureEnabled)
            Settings.PREF_GESTURE_FAST_TYPING_COOLDOWN else null,
        if (gestureEnabled &&
            (prefs.getBoolean(Settings.PREF_GESTURE_PREVIEW_TRAIL, Defaults.PREF_GESTURE_PREVIEW_TRAIL) || gestureFloatingPreviewEnabled))
            Settings.PREF_GESTURE_TRAIL_FADEOUT_DURATION else null
        )
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_gesture),
        settings = items,
        header = { GestureStatusCard() }
    )
}

fun createGestureTypingSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_GESTURE_INPUT, R.string.gesture_input, R.string.gesture_input_summary) {
        SwitchPreference(it, Defaults.PREF_GESTURE_INPUT)
    },
    Setting(context, Settings.PREF_GESTURE_PREVIEW_TRAIL, R.string.gesture_preview_trail) {
        SwitchPreference(it, Defaults.PREF_GESTURE_PREVIEW_TRAIL)
    },
    Setting(context, Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT,
        R.string.gesture_floating_preview_static, R.string.gesture_floating_preview_static_summary)
    {
        SwitchPreference(it, Defaults.PREF_GESTURE_FLOATING_PREVIEW_TEXT)
    },
    Setting(context, Settings.PREF_GESTURE_FLOATING_PREVIEW_DYNAMIC,
        R.string.gesture_floating_preview_text, R.string.gesture_floating_preview_dynamic_summary)
    { def ->
        val ctx = LocalContext.current
        SwitchPreference(def, Defaults.PREF_GESTURE_FLOATING_PREVIEW_DYNAMIC) {
            // is this complexity and 2 pref keys for one setting really needed?
            // default value is based on system reduced motion
            val default = Settings.readGestureDynamicPreviewDefault(ctx)
            val followingSystem = it == default
            // allow the default to be overridden
            ctx.prefs().edit { putBoolean(Settings.PREF_GESTURE_DYNAMIC_PREVIEW_FOLLOW_SYSTEM, followingSystem) }
            KeyboardSwitcher.getInstance().setThemeNeedsReload()
        }
    },
    Setting(context, Settings.PREF_GESTURE_SPACE_AWARE, R.string.gesture_space_aware, R.string.gesture_space_aware_summary) {
        SwitchPreference(it, Defaults.PREF_GESTURE_SPACE_AWARE)
    },
    Setting(context, Settings.PREF_GESTURE_FAST_TYPING_COOLDOWN, R.string.gesture_fast_typing_cooldown) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = Defaults.PREF_GESTURE_FAST_TYPING_COOLDOWN,
            range = 0f..500f,
            description = {
                if (it <= 0) stringResource(R.string.gesture_fast_typing_cooldown_instant)
                else stringResource(R.string.abbreviation_unit_milliseconds, it.toString())
            }
        )
    },
    Setting(context, Settings.PREF_GESTURE_TRAIL_FADEOUT_DURATION, R.string.gesture_trail_fadeout_duration) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = Defaults.PREF_GESTURE_TRAIL_FADEOUT_DURATION,
            range = 100f..1900f,
            description = { stringResource(R.string.abbreviation_unit_milliseconds, (it + 100).toString()) },
            stepSize = 10,
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
)

/** Tells whether glide typing can work: which decoder is used and whether the languages have the dictionary it needs. */
@Composable
private fun GestureStatusCard() {
    val ctx = LocalContext.current
    val resources = LocalResources.current
    var refresh by remember { mutableIntStateOf(0) }
    val missing = remember(refresh) { getEnabledLocalesWithoutDictionary(ctx) }
    val ok = missing.isEmpty()
    val enabler = PointerTracker.getGestureEnabler()
    val yes = stringResource(R.string.gesture_status_yes)
    val no = stringResource(R.string.gesture_status_no)
    Card(
        colors = CardDefaults.cardColors(containerColor = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.gesture_status_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if (JniUtils.sHaveGestureLib) R.string.gesture_status_library else R.string.gesture_status_builtin), style = MaterialTheme.typography.bodyMedium)
            // live state, read from the keyboard (the settings run in the same process)
            key(refresh) {
                Text(
                    stringResource(R.string.gesture_status_handling,
                        if (enabler.isMainDictionaryAvailable) yes else no,
                        if (enabler.isEnabledByUser) yes else no,
                        if (enabler.isEnabledByInputField) yes else no),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    stringResource(R.string.gesture_status_decoder_stats,
                        if (GestureDiagnostics.lexiconWords < 0) "-" else GestureDiagnostics.lexiconWords.toString(),
                        GestureDiagnostics.gesturesStarted, GestureDiagnostics.gesturesDecoded,
                        if (GestureDiagnostics.lastCandidates < 0) "-" else GestureDiagnostics.lastCandidates.toString(),
                        if (GestureDiagnostics.lastMillis < 0) "-" else GestureDiagnostics.lastMillis.toString()),
                    style = MaterialTheme.typography.bodySmall
                )
                GestureDiagnostics.lastError?.let {
                    Text(stringResource(R.string.gesture_status_error, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            TextButton(onClick = { refresh++ }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.gesture_status_refresh)) }
            if (ok) {
                Text(stringResource(R.string.gesture_status_dictionary_ok), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    stringResource(R.string.gesture_status_dictionary_missing, missing.joinToString(", ") { it.localizedDisplayName(resources) }),
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = { DictionaryAutoDownloader.requestCheck(ctx, showMessage = true, force = true) }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.dictionary_banner_button))
                }
            }
        }
    }
}

@Preview
@Composable
private fun Preview() {
    JniUtils.sHaveGestureLib = true
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            GestureTypingScreen { }
        }
    }
}
