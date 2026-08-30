package com.contai.financeiro

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class FinanceNotificationListener : NotificationListenerService() {

    private fun prefs() =
        getSharedPreferences("contai_notifications", Context.MODE_PRIVATE)

    override fun onListenerConnected() {
        super.onListenerConnected()

        prefs().edit()
            .putString("last_package", "SISTEMA CONTAI")
            .putString("last_title", "Serviço conectado")
            .putString(
                "last_text",
                "O Android conectou o Contai ao serviço de notificações."
            )
            .apply()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras

        val title =
            extras.getCharSequence("android.title")?.toString().orEmpty()

        val text =
            extras.getCharSequence("android.text")?.toString().orEmpty()

        prefs().edit()
            .putString("last_package", sbn.packageName)
            .putString("last_title", title)
            .putString("last_text", text)
            .apply()
    }
}
