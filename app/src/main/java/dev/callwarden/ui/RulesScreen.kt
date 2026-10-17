package dev.callwarden.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.callwarden.core.Rule
import dev.callwarden.core.RuleAction
import dev.callwarden.core.RuleEngine
import dev.callwarden.core.RuleType
import dev.callwarden.core.RuleValidator
import dev.callwarden.data.Store

@Composable
fun RulesScreen(store: Store) {
    val rules by store.rules.collectAsState()
    val settings by store.settings.collectAsState()
    var editing by remember { mutableStateOf<Rule?>(null) }
    var creating by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (settings.homeCountryCode.isBlank()) {
                item {
                    Text(
                        "Tip: set your country code in Settings so local and international " +
                            "forms of a number (080… vs +91 80…) match the same rules.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { TestNumberCard(rules, settings.homeCountryCode) }
            if (rules.isEmpty()) {
                item {
                    Text(
                        "No rules yet. Tap + to block a number, a prefix like +91 80 4718, " +
                            "or a pattern like +91 80 47## ####.",
                        modifier = Modifier.padding(vertical = 24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            items(rules, key = { it.id }) { rule ->
                RuleRow(
                    rule = rule,
                    onToggle = { store.upsertRule(rule.copy(enabled = it)) },
                    onClick = { editing = rule },
                )
            }
        }
        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Add rule") }
    }

    val current = editing
    if (creating || current != null) {
        RuleDialog(
            initial = current,
            onDismiss = { creating = false; editing = null },
            onSave = { store.upsertRule(it); creating = false; editing = null },
            onDelete = current?.let { r -> { store.deleteRule(r.id); editing = null } },
        )
    }
}

@Composable
private fun TestNumberCard(rules: List<Rule>, homeCountryCode: String) {
    var number by rememberSaveable { mutableStateOf("") }
    val decision = remember(rules, homeCountryCode, number) {
        RuleEngine(rules, homeCountryCode).evaluate(number)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = number,
                onValueChange = { number = it },
                label = { Text("Test a number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            if (number.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                val rule = decision.rule
                Text(
                    if (decision.action == null || rule == null) "Would ring normally"
                    else "${decision.action.title}: ${rule.label.ifBlank { rule.pattern }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (decision.action) {
                        RuleAction.BLOCK, RuleAction.SILENCE -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun RuleRow(rule: Rule, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (rule.label.isNotBlank()) {
                    Text(rule.label, style = MaterialTheme.typography.titleMedium)
                }
                Text(rule.pattern, fontFamily = FontFamily.Monospace)
                Text(
                    "${rule.action.title} · ${rule.type.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (rule.action == RuleAction.ALLOW) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
    }
}

private fun RuleType.help() = when (this) {
    RuleType.EXACT -> "One specific number, e.g. +91 98765 43210"
    RuleType.PREFIX -> "Every number that starts with these digits, e.g. +91 80 4718 " +
        "matches +91 80 4718 0000 to +91 80 4718 9999 (and longer)"
    RuleType.WILDCARD -> "# or ? = exactly one digit, * = any number of digits. " +
        "e.g. +91 80 47## 12## or +91 140*"
    RuleType.REGEX -> "Advanced: a regular expression that must match the whole number, " +
        "e.g. \\+91(80|22)4718\\d{4}"
}

/** [initial] with id 0 means "new rule, pre-filled" — used from the call history. */
@Composable
fun RuleDialog(
    initial: Rule?,
    onDismiss: () -> Unit,
    onSave: (Rule) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var type by remember { mutableStateOf(initial?.type ?: RuleType.PREFIX) }
    var action by remember { mutableStateOf(initial?.action ?: RuleAction.BLOCK) }
    var pattern by remember { mutableStateOf(initial?.pattern ?: "") }
    var label by remember { mutableStateOf(initial?.label ?: "") }
    val error = RuleValidator.validate(type, pattern)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null || initial.id == 0L) "New rule" else "Edit rule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Match", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuleType.entries.forEach { t ->
                        FilterChip(selected = t == type, onClick = { type = t }, label = { Text(t.title) })
                    }
                }
                Text(type.help(), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text(if (type == RuleType.REGEX) "Regex" else "Number or pattern") },
                    singleLine = true,
                    isError = pattern.isNotEmpty() && error != null,
                    supportingText = { if (pattern.isNotEmpty() && error != null) Text(error) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (type == RuleType.REGEX) KeyboardType.Ascii else KeyboardType.Phone,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Action", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuleAction.entries.forEach { a ->
                        FilterChip(selected = a == action, onClick = { action = a }, label = { Text(a.title) })
                    }
                }
                Text(
                    when (action) {
                        RuleAction.BLOCK -> "Reject the call. The caller hears it end."
                        RuleAction.SILENCE -> "Let it ring silently. It still shows as a missed call."
                        RuleAction.ALLOW -> "Exception: never block this, even if another rule matches."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = error == null,
                onClick = {
                    onSave(
                        Rule(
                            id = initial?.id ?: 0L,
                            type = type,
                            pattern = pattern.trim(),
                            action = action,
                            label = label.trim(),
                            enabled = initial?.enabled ?: true,
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
