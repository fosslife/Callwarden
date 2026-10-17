package dev.callwarden.core

// Pure Kotlin (no Android imports) so the matching logic can be unit-tested on the JVM.

enum class RuleType(val title: String) {
    EXACT("Exact number"),
    PREFIX("Starts with"),
    WILDCARD("Pattern"),
    REGEX("Regex"),
}

enum class RuleAction(val title: String) {
    BLOCK("Block"),
    SILENCE("Silence"),
    ALLOW("Always allow"),
}

data class Rule(
    val id: Long,
    val type: RuleType,
    val pattern: String,
    val action: RuleAction = RuleAction.BLOCK,
    val label: String = "",
    val enabled: Boolean = true,
)

object PhoneNumbers {
    /** Strips separators. Keeps a leading "+" and turns a leading "00" into "+". */
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        return when {
            trimmed.startsWith("+") -> "+$digits"
            digits.startsWith("00") -> "+" + digits.drop(2)
            else -> digits
        }
    }

    /**
     * The same number written the different ways a person might type it in a rule.
     * With home code "91", "+91 80 4718 1234" yields:
     * +918047181234, 918047181234, 8047181234, 08047181234.
     */
    fun candidates(raw: String, homeCountryCode: String): Set<String> {
        val n = normalize(raw)
        if (n.isEmpty() || n == "+") return emptySet()
        val cc = homeCountryCode.filter { it.isDigit() }
        val out = linkedSetOf(n)
        if (n.startsWith("+")) {
            out += n.drop(1)
            if (cc.isNotEmpty() && n.startsWith("+$cc") && n.length > cc.length + 1) {
                val national = n.drop(1 + cc.length)
                out += national
                out += "0$national"
            }
        } else if (cc.isNotEmpty()) {
            val national = n.removePrefix("0")
            if (national.isNotEmpty()) {
                out += national
                out += "0$national"
                out += "+$cc$national"
                out += "$cc$national"
            }
        }
        return out
    }
}

object RuleValidator {
    private val wildcardChars = Regex("""^\+?[0-9#?*]+$""")

    /** Returns a human-readable error, or null when the rule is usable. */
    fun validate(type: RuleType, pattern: String): String? {
        if (pattern.isBlank()) return "Enter a number or pattern"
        return when (type) {
            RuleType.EXACT, RuleType.PREFIX -> {
                val digits = pattern.count { it.isDigit() }
                when {
                    pattern.any { it.isLetter() || it in "#?*" } -> "Only digits, spaces, dashes and a leading + are allowed"
                    digits == 0 -> "Enter at least one digit"
                    type == RuleType.PREFIX && digits < 3 -> "Use at least 3 digits so you don't block too much"
                    else -> null
                }
            }
            RuleType.WILDCARD -> {
                val compact = compactWildcard(pattern)
                when {
                    !wildcardChars.matches(compact) -> "Use digits, # or ? (one digit) and * (any digits)"
                    compact.count { it.isDigit() } < 3 -> "Use at least 3 fixed digits so you don't block too much"
                    else -> null
                }
            }
            RuleType.REGEX -> try {
                val regex = Regex(pattern)
                if (regex.matches("")) "This regex matches everything" else null
            } catch (e: Exception) {
                "Invalid regex: ${e.message?.lineSequence()?.firstOrNull() ?: "syntax error"}"
            }
        }
    }

    internal fun compactWildcard(pattern: String): String {
        val kept = pattern.trim().filter { it.isDigit() || it in "+#?*" }
        return if (kept.startsWith("00")) "+" + kept.drop(2) else kept
    }
}

private class CompiledRule(val rule: Rule, homeCountryCode: String) {
    private val exact: Set<String> = if (rule.type == RuleType.EXACT) {
        PhoneNumbers.candidates(rule.pattern, homeCountryCode)
    } else emptySet()

    private val prefix: String = if (rule.type == RuleType.PREFIX) PhoneNumbers.normalize(rule.pattern) else ""

    private val regex: Regex? = when (rule.type) {
        RuleType.WILDCARD -> Regex(
            RuleValidator.compactWildcard(rule.pattern).map {
                when (it) {
                    '+' -> "\\+"
                    '#', '?' -> "\\d"
                    '*' -> "\\d*"
                    else -> it.toString()
                }
            }.joinToString("")
        )
        RuleType.REGEX -> Regex(rule.pattern)
        else -> null
    }

    fun matches(candidates: Set<String>): Boolean = when (rule.type) {
        RuleType.EXACT -> candidates.any { it in exact }
        RuleType.PREFIX -> prefix.isNotEmpty() && candidates.any { it.startsWith(prefix) }
        RuleType.WILDCARD, RuleType.REGEX -> candidates.any { regex!!.matches(it) }
    }
}

data class Decision(val action: RuleAction?, val rule: Rule?, val reason: String = "")

private fun Rule.describe() = label.ifBlank { "${type.title}: $pattern" }

/** "Always allow" beats "Block", which beats "Silence". */
class RuleEngine(rules: List<Rule>, private val homeCountryCode: String) {
    private val compiled = rules
        .filter { it.enabled && RuleValidator.validate(it.type, it.pattern) == null }
        .map { CompiledRule(it, homeCountryCode) }

    fun evaluate(number: String): Decision {
        val candidates = PhoneNumbers.candidates(number, homeCountryCode)
        if (candidates.isEmpty()) return Decision(null, null)
        for (action in listOf(RuleAction.ALLOW, RuleAction.BLOCK, RuleAction.SILENCE)) {
            compiled.firstOrNull { it.rule.action == action && it.matches(candidates) }
                ?.let { return Decision(action, it.rule) }
        }
        return Decision(null, null)
    }

    /**
     * The whole incoming-call decision, rules plus the catch-all settings, with the
     * reason to show in the history. [hidden] means the caller withheld their number.
     */
    fun screen(number: String, hidden: Boolean, blockHidden: Boolean, blockUnknown: Boolean): Decision {
        if (hidden) {
            return Decision(if (blockHidden) RuleAction.BLOCK else null, null, "Hidden or unknown number")
        }
        val decision = evaluate(number)
        return when {
            // An "always allow" rule wins over "block everyone not in my contacts".
            decision.action != null -> decision.copy(reason = decision.rule?.describe().orEmpty())
            blockUnknown -> Decision(RuleAction.BLOCK, null, "Not in contacts")
            else -> Decision(null, null, "No rule matched")
        }
    }
}
