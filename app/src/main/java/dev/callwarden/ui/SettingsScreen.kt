package dev.callwarden.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.callwarden.BuildConfig
import dev.callwarden.data.Store

@Composable
fun SettingsScreen(store: Store) {
    val settings by store.settings.collectAsState()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { it.write(store.exportJson().toByteArray()) }
        }.isSuccess
        Toast.makeText(context, if (ok) "Rules exported" else "Export failed", Toast.LENGTH_SHORT).show()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val message = runCatching {
            val text = context.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }
            "Imported ${store.importJson(text)} new rules"
        }.getOrElse { "Import failed: not a CallWarden rules file" }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = settings.homeCountryCode,
            onValueChange = { v -> store.updateSettings { it.copy(homeCountryCode = v.filter(Char::isDigit).take(4)) } },
            label = { Text("Your country calling code") },
            prefix = { Text("+") },
            placeholder = { Text("e.g. 91") },
            supportingText = { Text("Lets rules match both 080 4718… and +91 80 4718…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        SettingSwitch(
            "Block hidden numbers",
            "Reject calls with no caller ID",
            settings.blockHidden,
        ) { v -> store.updateSettings { it.copy(blockHidden = v) } }
        SettingSwitch(
            "Block everyone not in my contacts",
            "Rejects every number you haven't saved. \"Always allow\" rules still get through.",
            settings.blockUnknown,
        ) { v -> store.updateSettings { it.copy(blockUnknown = v) } }
        SettingSwitch(
            "Hide blocked calls from call log",
            "Blocked calls won't appear in your phone app's history",
            settings.skipCallLog,
        ) { v -> store.updateSettings { it.copy(skipCallLog = v) } }
        SettingSwitch(
            "Keep call history",
            "Every screened call, blocked or not, so you can turn one into a rule. " +
                "Stored only inside this app, last 500 calls.",
            settings.keepLog,
        ) { v -> store.updateSettings { it.copy(keepLog = v) } }

        HorizontalDivider()
        Text("Backup", style = MaterialTheme.typography.titleMedium)
        Text(
            "Rules are never synced or backed up automatically. Export them to a file you control.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exportLauncher.launch("callwarden-rules.json") }) { Text("Export rules") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) {
                Text("Import rules")
            }
        }

        HorizontalDivider()
        Text("Privacy", style = MaterialTheme.typography.titleMedium)
        Text(
            "CallWarden has no internet permission, so it physically cannot send anything off " +
                "your phone. No accounts, no analytics, no caller-ID lookups, no ads. Your rules, " +
                "history and contacts stay on this device.",
            style = MaterialTheme.typography.bodyMedium,
        )

        HorizontalDivider()
        Text(
            "CallWarden ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · GPL-3.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
