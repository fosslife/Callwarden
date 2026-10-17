package dev.callwarden.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.callwarden.data.ContactItem
import dev.callwarden.data.DeviceContacts
import kotlinx.coroutines.launch

private val CONTACT_PERMISSIONS = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)

private fun hasContactPermissions(context: Context) = CONTACT_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun ContactsScreen() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasContactPermissions(context)) }
    var denied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.all { it }
        denied = !granted
    }

    // Catches the permission being granted in system Settings while we're in the background.
    LifecycleResumeEffect(Unit) {
        if (!granted && hasContactPermissions(context)) granted = true
        onPauseOrDispose {}
    }

    if (granted) {
        ContactList()
    } else {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                "CallWarden can block saved contacts by switching on Android's built-in " +
                    "\"send to voicemail\" option for them. This needs access to your contacts. " +
                    "Contacts are read on this phone only and never uploaded anywhere.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            // After two refusals Android stops showing the prompt, so send the user to Settings.
            if (denied) {
                Button(onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                    )
                }) { Text("Open app settings") }
            } else {
                Button(onClick = { permissionLauncher.launch(CONTACT_PERMISSIONS) }) { Text("Allow contact access") }
            }
        }
    }
}

@Composable
private fun ContactList() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<ContactItem>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { contacts = DeviceContacts.load(context) }

    val all = contacts
    if (all == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val shown = remember(all, query) {
        val q = query.trim()
        if (q.isEmpty()) all
        else all.filter { c -> c.name.contains(q, ignoreCase = true) || c.numbers.any { it.contains(q) } }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search contacts") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Switch on to send a contact straight to voicemail. Android doesn't pass calls " +
                    "from saved contacts to blocking apps, so this is how contacts get blocked.",
                modifier = Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(shown, key = { it.id }) { contact ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(contact.name, style = MaterialTheme.typography.titleMedium)
                    Text(contact.numbers.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = contact.sendToVoicemail,
                    onCheckedChange = { value ->
                        scope.launch {
                            if (DeviceContacts.setSendToVoicemail(context, contact.id, value)) {
                                contacts = contacts?.map { if (it.id == contact.id) it.copy(sendToVoicemail = value) else it }
                            }
                        }
                    },
                )
            }
            HorizontalDivider()
        }
    }
}
