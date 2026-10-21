package com.fosslife.hush.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import com.fosslife.hush.core.RuleAction
import com.fosslife.hush.core.RuleEngine
import com.fosslife.hush.data.HIDDEN
import com.fosslife.hush.data.LogEntry
import com.fosslife.hush.data.Store

/**
 * Android calls this for every incoming call from a number that is not in the
 * user's contacts, once the app holds the call-screening role. It runs fully
 * offline: the decision is made from local rules only.
 */
class ScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }

        val store = Store.get(this)
        val settings = store.settings.value
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val hidden = number.isBlank() || details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED

        val decision = RuleEngine(store.rules.value, settings.homeCountryCode)
            .screen(number, hidden, settings.blockHidden, settings.blockUnknown)

        val response = CallResponse.Builder()
        when (decision.action) {
            RuleAction.BLOCK -> response
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
                .setSkipCallLog(settings.skipCallLog)
            RuleAction.SILENCE -> response.setSilenceCall(true)
            RuleAction.ALLOW, null -> Unit
        }
        respondToCall(details, response.build())

        // Log every screened call, including the ones that rang: that list is how the
        // user spots a number worth turning into a rule.
        store.addLog(
            LogEntry(System.currentTimeMillis(), number.ifBlank { HIDDEN }, decision.action, decision.reason)
        )
    }
}
