package com.contai.financeiro

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

class FinanceNotificationListener : NotificationListenerService() {

    private val heartbeatHandler = Handler(Looper.getMainLooper())

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val binderResponding = runCatching { activeNotifications != null }.getOrDefault(false)
            val editor = prefs().edit()
                .putBoolean("service_connected", binderResponding)
                .putLong("listener_probe_at", now)
                .putString("listener_probe_status", if (binderResponding) "BOUND" else "UNBOUND")
            if (binderResponding) editor.putLong("listener_last_alive_at", now)
            editor.apply()

            if (!binderResponding) {
                saveLifecycleEvent("heartbeatDetectedUnbound")
                NotificationListenerService.requestRebind(ComponentName(this@FinanceNotificationListener, FinanceNotificationListener::class.java))
            }
            heartbeatHandler.postDelayed(this, 15_000)
        }
    }

    private fun startHeartbeat() { heartbeatHandler.removeCallbacks(heartbeatRunnable); heartbeatHandler.post(heartbeatRunnable) }
    private fun stopHeartbeat() { heartbeatHandler.removeCallbacks(heartbeatRunnable) }
    private fun prefs() = getSharedPreferences("contai_notifications", Context.MODE_PRIVATE)

    private fun saveLifecycleEvent(event: String) {
        prefs().edit().putString("listener_lifecycle_event", event).putLong("listener_lifecycle_at", System.currentTimeMillis()).apply()
    }

    override fun onCreate() { super.onCreate(); saveLifecycleEvent("onCreate"); CaptureWatchdog.schedule(this) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val now = System.currentTimeMillis()
        val preferences = prefs()
        val recoveryAttempts = preferences.getInt("watchdog_recovery_attempts", 0)

        saveLifecycleEvent("onListenerConnected")
        startHeartbeat()
        CaptureWatchdog.schedule(this)

        val editor = preferences.edit()
            .putBoolean("service_connected", true)
            .putLong("listener_last_alive_at", now)
            .putString("last_package", "SISTEMA CONTAI")
            .putString("last_title", "Serviço conectado")
            .putString("last_text", "O Android conectou o Contai ao serviço de notificações.")

        if (recoveryAttempts > 0) {
            editor
                .putLong("watchdog_last_recovered_at", now)
                .putInt("watchdog_last_recovery_attempts_completed", recoveryAttempts)
        }
        editor.putInt("watchdog_recovery_attempts", 0).apply()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected(); saveLifecycleEvent("onListenerDisconnected"); stopHeartbeat()
        prefs().edit().putBoolean("service_connected", false).apply()
        NotificationListenerService.requestRebind(ComponentName(this, FinanceNotificationListener::class.java)); CaptureWatchdog.schedule(this)
    }

    override fun onDestroy() {
        saveLifecycleEvent("onDestroy"); stopHeartbeat(); prefs().edit().putBoolean("service_connected", false).apply(); CaptureWatchdog.schedule(this); super.onDestroy()
    }

    private fun bundleText(extras: Bundle): String {
        val parts = mutableListOf<String>()
        val keys = listOf(
            "android.title", "android.title.big", "android.text", "android.bigText",
            "android.subText", "android.summaryText", "android.infoText"
        )
        keys.forEach { key ->
            extras.getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(parts::add)
        }
        extras.getCharSequenceArray("android.textLines")
            ?.map { it.toString().trim() }
            ?.filter { it.isNotBlank() }
            ?.let(parts::addAll)

        @Suppress("DEPRECATION")
        val messages = extras.getParcelableArray("android.messages")
        messages?.forEach { raw ->
            runCatching {
                val bundle = raw as? Bundle ?: return@runCatching
                bundle.getCharSequence("text")?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(parts::add)
                bundle.getCharSequence("sender")?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(parts::add)
            }
        }
        return parts.distinct().joinToString(" • ")
    }

    private fun appendDiagnosticEvent(
        packageName: String,
        title: String,
        text: String,
        parsed: ParsedTransaction,
        timestamp: Long
    ) {
        val preferences = prefs()
        val current = runCatching {
            JSONArray(preferences.getString("capture_recent_events", "[]") ?: "[]")
        }.getOrElse { JSONArray() }

        val updated = JSONArray()
        val start = (current.length() - 7).coerceAtLeast(0)
        for (i in start until current.length()) {
            current.optJSONObject(i)?.let(updated::put)
        }

        updated.put(
            JSONObject()
                .put("timestamp", timestamp)
                .put("package", packageName)
                .put("title", title)
                .put("text", text)
                .put("classification", parsed.classification)
                .put("type", parsed.type)
                .apply { parsed.amount?.let { put("amount", it) } }
        )

        preferences.edit()
            .putString("capture_recent_events", updated.toString())
            .apply()
    }

    private fun isRecentDuplicate(
        history: JSONArray,
        packageName: String,
        title: String,
        text: String,
        parsed: ParsedTransaction,
        now: Long
    ): Boolean {
        for (index in history.length() - 1 downTo 0) {
            val item = history.optJSONObject(index) ?: continue
            val timestamp = item.optLong("timestamp", 0L)
            if (timestamp <= 0L || now < timestamp || now - timestamp > 30_000) continue

            val storedAmount = if (item.has("amount")) item.optDouble("amount") else null
            if (
                item.optString("package") == packageName &&
                item.optString("title") == title &&
                item.optString("text") == text &&
                item.optString("type") == parsed.type &&
                storedAmount == parsed.amount
            ) return true
        }
        return false
    }

    private fun saveToHistory(packageName: String, title: String, text: String, parsed: ParsedTransaction): Boolean {
        val prefs = prefs()
        val history = JSONArray(prefs.getString("transaction_history", "[]") ?: "[]")
        val now = System.currentTimeMillis()
        if (isRecentDuplicate(history, packageName, title, text, parsed, now)) {
            prefs.edit()
                .putLong("debug_last_duplicate_at", now)
                .putString("debug_last_duplicate_package", packageName)
                .apply()
            return false
        }

        val normalizedLearningText = text.lowercase().replace(Regex("""r\$\s*[0-9.]+,[0-9]{2}"""), "r$ valor").replace(Regex("""\s+"""), " ").trim()
        val learningKey = "$packageName|$title|$normalizedLearningText".lowercase().trim()
        val learnedParts = prefs.getString("learned_$learningKey", null)?.split("|", limit = 2)
        val finalType = learnedParts?.getOrNull(0) ?: parsed.type
        val category = learnedParts?.getOrNull(1) ?: when (finalType) { "ENTRADA" -> "Receitas"; "DESPESA" -> "Outros"; else -> "Não categorizado" }
        val item = JSONObject().put("timestamp", now).put("movementTimestamp", now).put("package", packageName).put("title", title).put("text", text)
            .put("type", finalType).put("category", category).put("confidence", parsed.confidence).put("classification", parsed.classification).put("investmentType", parsed.investmentType)
        if (parsed.amount != null) item.put("amount", parsed.amount)
        history.put(item)
        prefs.edit().putString("transaction_history", history.toString()).putLong("capture_last_saved_at", now).putString("capture_last_saved_package", packageName).apply()
        return true
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        if (posted.packageName == packageName) return
        val notification = posted.notification ?: return
        val extras = notification.extras
        val title = listOf(
            extras.getCharSequence("android.title")?.toString().orEmpty(),
            extras.getCharSequence("android.title.big")?.toString().orEmpty(),
            extras.getCharSequence("android.subText")?.toString().orEmpty()
        ).firstOrNull { it.isNotBlank() }.orEmpty()

        val richText = bundleText(extras)
        val ticker = notification.tickerText?.toString().orEmpty()
        val text = listOf(richText, ticker).filter { it.isNotBlank() }.distinct().joinToString(" • ")
        val now = System.currentTimeMillis()

        prefs().edit()
            .putBoolean("service_connected", true)
            .putLong("listener_last_alive_at", now)
            .putLong("debug_last_event_at", now)
            .putString("debug_last_package", posted.packageName.orEmpty())
            .putString("debug_last_title", title)
            .putString("debug_last_text", text)
            .putString("debug_last_raw", "$title • $text".trim(' ', '•'))
            .apply()

        val parsed = FinancialParser.parse(posted.packageName.orEmpty(), title, text)
        prefs().edit()
            .putLong("capture_last_parser_at", System.currentTimeMillis())
            .putString("capture_last_parser_result", parsed.classification)
            .putString("capture_last_parser_type", parsed.type)
            .putString("capture_last_parser_description", parsed.description)
            .apply()

        appendDiagnosticEvent(
            packageName = posted.packageName.orEmpty(),
            title = title,
            text = text,
            parsed = parsed,
            timestamp = now
        )

        if (parsed.classification == "NAO_FINANCEIRA") return

        saveToHistory(posted.packageName.orEmpty(), title, text, parsed)
        val editor = prefs().edit().putString("last_package", posted.packageName.orEmpty()).putString("last_title", title).putString("last_text", text)
            .putString("last_type", parsed.type).putInt("last_confidence", parsed.confidence).putString("last_classification", parsed.classification)
        if (parsed.amount != null) editor.putString("last_amount", parsed.amount.toString()) else editor.remove("last_amount")
        editor.apply()
    }
}
