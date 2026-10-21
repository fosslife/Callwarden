package com.fosslife.hush.data

import android.content.Context
import android.util.AtomicFile
import com.fosslife.hush.core.Rule
import com.fosslife.hush.core.RuleAction
import com.fosslife.hush.core.RuleType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Settings(
    val homeCountryCode: String = "",
    val blockHidden: Boolean = false,
    val blockUnknown: Boolean = false,
    val skipCallLog: Boolean = false,
    val keepLog: Boolean = true,
)

/** Stand-in number for a withheld caller ID. */
const val HIDDEN = "Hidden"

/** A screened call. [action] is null when no rule matched and the phone rang normally. */
data class LogEntry(
    val time: Long,
    val number: String,
    val action: RuleAction?,
    val reason: String,
)

/**
 * Everything lives in the app's private storage: rules.json, log.json and a
 * SharedPreferences file. Nothing leaves the device unless the user exports it.
 */
class Store private constructor(context: Context) {
    private val app = context.applicationContext
    private val rulesFile = AtomicFile(File(app.filesDir, "rules.json"))
    private val logFile = AtomicFile(File(app.filesDir, "log.json"))
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _rules = MutableStateFlow(readRules())
    val rules: StateFlow<List<Rule>> = _rules

    // Lazy: onScreenCall must not read the whole history before deciding on a call.
    private val _log by lazy { MutableStateFlow(readLog()) }
    val log: StateFlow<List<LogEntry>> get() = _log

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings

    @Synchronized
    fun upsertRule(rule: Rule) {
        val list = _rules.value.toMutableList()
        val index = list.indexOfFirst { it.id == rule.id }
        if (index >= 0) list[index] = rule else list += rule.copy(id = nextId(list))
        saveRules(list)
    }

    @Synchronized
    fun deleteRule(id: Long) = saveRules(_rules.value.filterNot { it.id == id })

    @Synchronized
    fun updateSettings(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        prefs.edit()
            .putString("homeCountryCode", s.homeCountryCode)
            .putBoolean("blockHidden", s.blockHidden)
            .putBoolean("blockUnknown", s.blockUnknown)
            .putBoolean("skipCallLog", s.skipCallLog)
            .putBoolean("keepLog", s.keepLog)
            .apply()
        _settings.value = s
        if (!s.keepLog) clearLog()
    }

    @Synchronized
    fun addLog(entry: LogEntry) {
        if (!_settings.value.keepLog) return
        val list = (listOf(entry) + _log.value).take(MAX_LOG)
        write(logFile, JSONArray(list.map { it.toJson() }).toString())
        _log.value = list
    }

    @Synchronized
    fun clearLog() {
        logFile.delete()
        _log.value = emptyList()
    }

    fun exportJson(): String = JSONObject()
        .put("app", "Hush")
        .put("version", 1)
        .put("rules", JSONArray(_rules.value.map { it.toJson() }))
        .toString(2)

    /** Adds rules from an export, skipping ones that already exist. Returns how many were added. */
    @Synchronized
    fun importJson(text: String): Int {
        val trimmed = text.trim()
        val array = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).getJSONArray("rules")
        val list = _rules.value.toMutableList()
        var added = 0
        for (i in 0 until array.length()) {
            val r = ruleFromJson(array.getJSONObject(i)) ?: continue
            if (list.none { it.type == r.type && it.pattern == r.pattern && it.action == r.action }) {
                list += r.copy(id = nextId(list))
                added++
            }
        }
        saveRules(list)
        return added
    }

    private fun nextId(list: List<Rule>) = (list.maxOfOrNull { it.id } ?: 0L) + 1

    private fun saveRules(list: List<Rule>) {
        write(rulesFile, JSONArray(list.map { it.toJson() }).toString())
        _rules.value = list
    }

    private fun readRules(): List<Rule> = readArray(rulesFile)?.let { array ->
        (0 until array.length()).mapNotNull { ruleFromJson(array.getJSONObject(it)) }
    } ?: emptyList()

    private fun readLog(): List<LogEntry> = readArray(logFile)?.let { array ->
        (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            runCatching {
                LogEntry(
                    time = o.getLong("time"),
                    number = o.getString("number"),
                    action = RuleAction.entries.find { it.name == o.optString("action") },
                    reason = o.optString("reason"),
                )
            }.getOrNull()
        }
    } ?: emptyList()

    private fun readSettings() = Settings(
        homeCountryCode = prefs.getString("homeCountryCode", "") ?: "",
        blockHidden = prefs.getBoolean("blockHidden", false),
        blockUnknown = prefs.getBoolean("blockUnknown", false),
        skipCallLog = prefs.getBoolean("skipCallLog", false),
        keepLog = prefs.getBoolean("keepLog", true),
    )

    private fun readArray(file: AtomicFile): JSONArray? =
        runCatching { JSONArray(String(file.readFully())) }.getOrNull()

    private fun write(file: AtomicFile, text: String) {
        val out = file.startWrite()
        try {
            out.write(text.toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    private fun Rule.toJson() = JSONObject()
        .put("id", id)
        .put("type", type.name)
        .put("pattern", pattern)
        .put("action", action.name)
        .put("label", label)
        .put("enabled", enabled)

    private fun ruleFromJson(o: JSONObject): Rule? = runCatching {
        Rule(
            id = o.optLong("id"),
            type = RuleType.valueOf(o.getString("type")),
            pattern = o.getString("pattern"),
            action = RuleAction.valueOf(o.optString("action", RuleAction.BLOCK.name)),
            label = o.optString("label"),
            enabled = o.optBoolean("enabled", true),
        )
    }.getOrNull()

    private fun LogEntry.toJson() = JSONObject()
        .put("time", time)
        .put("number", number)
        .put("action", action?.name.orEmpty())
        .put("reason", reason)

    companion object {
        private const val MAX_LOG = 500

        @Volatile private var instance: Store? = null

        fun get(context: Context): Store =
            instance ?: synchronized(this) { instance ?: Store(context).also { instance = it } }
    }
}
