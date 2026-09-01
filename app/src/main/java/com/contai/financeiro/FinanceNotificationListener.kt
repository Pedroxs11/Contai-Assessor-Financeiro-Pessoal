package com.contai.financeiro

import android.content.Context
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

class FinanceNotificationListener : NotificationListenerService() {

    private fun prefs() =
        getSharedPreferences("contai_notifications", Context.MODE_PRIVATE)

    override fun onListenerConnected() {
        super.onListenerConnected()

        prefs().edit()
            .putBoolean("service_connected", true)
            .putString("last_package", "SISTEMA CONTAI")
            .putString("last_title", "Serviço conectado")
            .putString(
                "last_text",
                "O Android conectou o Contai ao serviço de notificações."
            )
            .apply()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()

        prefs().edit()
            .putBoolean("service_connected", false)
            .apply()

        NotificationListenerService.requestRebind(
            ComponentName(this, FinanceNotificationListener::class.java)
        )
    }

    private fun saveToHistory(
        packageName: String,
        title: String,
        text: String,
        parsed: ParsedTransaction
    ) {
        val prefs = prefs()
        val history = JSONArray(
            prefs.getString("transaction_history", "[]") ?: "[]"
        )

        val item = JSONObject()
            .put("timestamp", System.currentTimeMillis())
            .put("package", packageName)
            .put("title", title)
            .put("text", text)
            .put("type", parsed.type)
            .put("confidence", parsed.confidence)
            .put("classification", parsed.classification)

        if (parsed.amount != null) {
            item.put("amount", parsed.amount)
        }

        history.put(item)

        prefs.edit()
            .putString("transaction_history", history.toString())
            .apply()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras

        val title = listOf(
            extras.getCharSequence("android.title")?.toString().orEmpty(),
            extras.getCharSequence("android.title.big")?.toString().orEmpty(),
            extras.getCharSequence("android.subText")?.toString().orEmpty()
        ).firstOrNull { it.isNotBlank() }.orEmpty()

        val textLines =
            extras.getCharSequenceArray("android.textLines")
                ?.joinToString(" ")
                .orEmpty()

        val text = listOf(
            extras.getCharSequence("android.bigText")?.toString().orEmpty(),
            extras.getCharSequence("android.text")?.toString().orEmpty(),
            textLines,
            extras.getCharSequence("android.subText")?.toString().orEmpty(),
            notification.tickerText?.toString().orEmpty()
        ).firstOrNull { it.isNotBlank() }.orEmpty()

        prefs().edit()
            .putLong("debug_last_event_at", System.currentTimeMillis())
            .putString("debug_last_package", sbn?.packageName.orEmpty())
            .putString("debug_last_title", title)
            .putString("debug_last_text", text)
            .apply()

        val parsed = FinancialParser.parse(sbn?.packageName.orEmpty(), title, text)

        if (parsed.classification == "NAO_FINANCEIRA") {
            return
        }

        saveToHistory(
            sbn?.packageName.orEmpty(),
            title,
            text,
            parsed
        )

        val editor = prefs().edit()
            .putString("last_package", sbn?.packageName.orEmpty())
            .putString("last_title", title)
            .putString("last_text", text)
            .putString("last_type", parsed.type)
            .putInt("last_confidence", parsed.confidence)
            .putString("last_classification", parsed.classification)

        if (parsed.amount != null) {
            editor.putString("last_amount", parsed.amount.toString())
        } else {
            editor.remove("last_amount")
        }

        editor.apply()
    }
}
