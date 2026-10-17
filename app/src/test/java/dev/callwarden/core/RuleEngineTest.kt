package dev.callwarden.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RuleEngineTest {
    private fun engine(vararg rules: Rule, cc: String = "91") = RuleEngine(rules.toList(), cc)
    private fun rule(type: RuleType, pattern: String, action: RuleAction = RuleAction.BLOCK) =
        Rule(id = pattern.hashCode().toLong(), type = type, pattern = pattern, action = action)

    @Test fun normalizeHandlesSeparatorsAndDoubleZero() {
        assertEquals("+918047181234", PhoneNumbers.normalize("+91 (80) 4718-1234"))
        assertEquals("+918047181234", PhoneNumbers.normalize("0091 80 4718 1234"))
        assertEquals("08047181234", PhoneNumbers.normalize("080-4718 1234"))
    }

    @Test fun prefixMatchesInternationalAndLocalForms() {
        val e = engine(rule(RuleType.PREFIX, "+91 80 4718"))
        assertEquals(RuleAction.BLOCK, e.evaluate("+918047181234").action)
        assertEquals(RuleAction.BLOCK, e.evaluate("08047189999").action)
        assertNull(e.evaluate("+918047191234").action)

        val local = engine(rule(RuleType.PREFIX, "080 4718"))
        assertEquals(RuleAction.BLOCK, local.evaluate("+918047181234").action)
    }

    @Test fun wildcardSingleAndMultiDigit() {
        val e = engine(rule(RuleType.WILDCARD, "+91 80 47## 12##"))
        assertEquals(RuleAction.BLOCK, e.evaluate("+918047551299").action)
        assertNull(e.evaluate("+918047551399").action)
        assertNull(e.evaluate("+9180475512999").action) // too long

        val star = engine(rule(RuleType.WILDCARD, "+91 140*"))
        assertEquals(RuleAction.BLOCK, star.evaluate("+911401234567").action)
        assertNull(star.evaluate("+911411234567").action)
    }

    @Test fun regexMatchesWholeNumber() {
        val e = engine(rule(RuleType.REGEX, """\+91(80|22)4718\d{4}"""))
        assertEquals(RuleAction.BLOCK, e.evaluate("+91 22 4718 0001").action)
        assertNull(e.evaluate("+91 33 4718 0001").action)
    }

    @Test fun exactMatchesAcrossFormats() {
        val e = engine(rule(RuleType.EXACT, "098765 43210"))
        assertEquals(RuleAction.BLOCK, e.evaluate("+919876543210").action)
        assertNull(e.evaluate("+919876543211").action)
    }

    @Test fun allowBeatsBlockAndBlockBeatsSilence() {
        val e = engine(
            rule(RuleType.PREFIX, "+91804718", RuleAction.SILENCE),
            rule(RuleType.PREFIX, "+9180471"),
            rule(RuleType.EXACT, "+918047180000", RuleAction.ALLOW),
        )
        assertEquals(RuleAction.ALLOW, e.evaluate("+918047180000").action)
        assertEquals(RuleAction.BLOCK, e.evaluate("+918047181111").action)
    }

    @Test fun disabledAndInvalidRulesAreIgnored() {
        val e = engine(
            rule(RuleType.PREFIX, "+91804718").copy(enabled = false),
            rule(RuleType.REGEX, "([unclosed"),
        )
        assertNull(e.evaluate("+918047181234").action)
    }

    @Test fun otherCountriesDoNotCollide() {
        val e = engine(rule(RuleType.PREFIX, "+44 20"))
        assertNull(e.evaluate("+912012345678").action)
        assertEquals(RuleAction.BLOCK, e.evaluate("+442071234567").action)
    }

    @Test fun validatorRejectsOverBroadPatterns() {
        assertNotNull(RuleValidator.validate(RuleType.WILDCARD, "*"))
        assertNotNull(RuleValidator.validate(RuleType.PREFIX, "+9"))
        assertNotNull(RuleValidator.validate(RuleType.REGEX, ".*"))
        assertNotNull(RuleValidator.validate(RuleType.EXACT, "abc"))
        assertNull(RuleValidator.validate(RuleType.WILDCARD, "+91 80 47## ####"))
    }

    @Test fun hiddenNumbersProduceNoMatch() {
        val e = engine(rule(RuleType.WILDCARD, "+91 ##########"))
        assertNull(e.evaluate("").action)
    }

    // screen() is what ScreeningService actually calls: rules first, then the catch-all settings.

    @Test fun blockUnknownCatchesNumbersNoRuleMatched() {
        val e = engine(rule(RuleType.PREFIX, "+91 80 4718"))
        val d = e.screen("+919999988888", hidden = false, blockHidden = false, blockUnknown = true)
        assertEquals(RuleAction.BLOCK, d.action)
        assertEquals("Not in contacts", d.reason)
    }

    @Test fun allowRuleBeatsBlockUnknown() {
        val e = engine(rule(RuleType.EXACT, "+919999988888", RuleAction.ALLOW))
        val d = e.screen("+919999988888", hidden = false, blockHidden = false, blockUnknown = true)
        assertEquals(RuleAction.ALLOW, d.action)
    }

    @Test fun withoutBlockUnknownAnUnmatchedNumberRings() {
        val e = engine(rule(RuleType.PREFIX, "+91 80 4718"))
        val d = e.screen("+919999988888", hidden = false, blockHidden = false, blockUnknown = false)
        assertNull(d.action)
        assertEquals("No rule matched", d.reason)
    }

    @Test fun blockRuleStillWinsOverBlockUnknown() {
        val e = engine(rule(RuleType.PREFIX, "+91 80 4718").copy(label = "Spam co"))
        val d = e.screen("+918047181234", hidden = false, blockHidden = false, blockUnknown = true)
        assertEquals(RuleAction.BLOCK, d.action)
        assertEquals("Spam co", d.reason) // the rule's own reason, not the catch-all's
    }

    @Test fun hiddenNumbersFollowTheirOwnSetting() {
        val e = engine(rule(RuleType.PREFIX, "+91 80 4718"))
        assertEquals(
            RuleAction.BLOCK,
            e.screen("", hidden = true, blockHidden = true, blockUnknown = false).action,
        )
        // "Block everyone not in my contacts" must not silently swallow hidden calls too.
        assertNull(e.screen("", hidden = true, blockHidden = false, blockUnknown = true).action)
    }

    @Test fun unlabelledRuleReportsItsPattern() {
        val e = engine(rule(RuleType.PREFIX, "+91 80 4718"))
        val d = e.screen("+918047181234", hidden = false, blockHidden = false, blockUnknown = false)
        assertEquals("Starts with: +91 80 4718", d.reason)
    }
}
