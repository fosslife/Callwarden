package com.fosslife.hush.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.fosslife.hush.core.Rule
import com.fosslife.hush.core.RuleAction
import com.fosslife.hush.core.RuleType
import com.fosslife.hush.data.HIDDEN
import com.fosslife.hush.data.LogEntry
import com.fosslife.hush.data.Store
import java.text.DateFormat
import java.util.Date

@Composable
fun LogScreen(store: Store) {
    val log by store.log.collectAsState()
    val rules by store.rules.collectAsState()
    val settings by store.settings.collectAsState()
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    var newRuleFor by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp, 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        !settings.keepLog -> "History is turned off in Settings."
                        log.isEmpty() -> "No calls screened yet. Calls from saved contacts never " +
                            "reach Hush, so they won't show up here."
                        else -> "${log.size} screened call${if (log.size == 1) "" else "s"} (stored only on this phone)"
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (log.isNotEmpty()) TextButton(onClick = store::clearLog) { Text("Clear") }
            }
        }
        items(log, key = { "${it.time}-${it.number}" }) { entry ->
            val alreadyAllowed = rules.any {
                it.action == RuleAction.ALLOW && it.type == RuleType.EXACT && it.pattern == entry.number
            }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.number, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${entry.status()} · ${format.format(Date(entry.time))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (entry.action == null) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error,
                        )
                        if (entry.reason.isNotBlank()) {
                            Text(entry.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (entry.number != HIDDEN) {
                        when {
                            entry.action == RuleAction.BLOCK && !alreadyAllowed -> TextButton(onClick = {
                                store.upsertRule(
                                    Rule(0, RuleType.EXACT, entry.number, RuleAction.ALLOW, "Allowed from history")
                                )
                            }) { Text("Allow") }
                            entry.action != RuleAction.BLOCK ->
                                TextButton(onClick = { newRuleFor = entry.number }) { Text("Block…") }
                        }
                    }
                }
            }
        }
    }

    // Opens the normal rule editor with the number filled in; the pattern is still
    // the user's to write (trim digits for a prefix, swap # in for a wildcard).
    newRuleFor?.let { number ->
        RuleDialog(
            initial = Rule(0, RuleType.PREFIX, number),
            onDismiss = { newRuleFor = null },
            onSave = { store.upsertRule(it); newRuleFor = null },
            onDelete = null,
        )
    }
}

private fun LogEntry.status() = when (action) {
    RuleAction.BLOCK -> "Blocked"
    RuleAction.SILENCE -> "Silenced"
    else -> "Rang"
}
