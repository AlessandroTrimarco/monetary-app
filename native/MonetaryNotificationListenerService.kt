package com.vitali.monetary

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Captura notificaciones de apps de banco/billetera/MP y las reenvía al backend,
 * que las convierte en PendingTransaction (el usuario confirma en /pending).
 *
 * Corre en BACKGROUND (aún con la app cerrada) → NO tiene la cookie de la WebView.
 * Por eso usa un DEVICE TOKEN (Authorization: Bearer) guardado en SharedPreferences,
 * que la WebView setea vía NotificationListenerPlugin cuando el usuario activa la función.
 *
 * Requiere que el usuario otorgue "Acceso a notificaciones" (Settings) — no es un permiso
 * normal; se abre la pantalla de Special App Access. El usuario controla qué apps escuchar.
 */
class MonetaryNotificationListenerService : NotificationListenerService() {

  companion object {
    const val PREFS = "monetary_prefs"
    const val KEY_TOKEN = "device_token"
    const val KEY_ENABLED = "listener_enabled"
    const val KEY_PACKAGES = "listener_packages" // CSV de packageNames a escuchar
    const val INGEST_URL = "https://monetary-agent.vercel.app/api/ingest/notification"

    // Apps financieras comunes en AR (el usuario puede ampliar desde la app).
    val DEFAULT_PACKAGES = setOf(
      "com.mercadopago.wallet",
      "com.uala.android",
      "com.brubank.android",
      "ar.com.santander.rio.movilidad",
      "com.bancogalicia.movil",
      "com.todopago",
      "ar.bbva.net",
      "com.naranja"
    )
  }

  override fun onNotificationPosted(sbn: StatusBarNotification) {
    val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (!prefs.getBoolean(KEY_ENABLED, false)) return

    val token = prefs.getString(KEY_TOKEN, null) ?: return

    val pkg = sbn.packageName ?: return
    val allowed = prefs.getString(KEY_PACKAGES, null)
      ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
      ?: DEFAULT_PACKAGES
    if (pkg !in allowed) return

    val extras = sbn.notification?.extras ?: return
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
    val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
    val raw = listOf(title, big.ifEmpty { text }).filter { it.isNotBlank() }.joinToString(" — ").take(1000)
    if (raw.length < 3) return

    postToBackend(token, raw, pkg, sbn.postTime)
  }

  private fun postToBackend(token: String, rawText: String, pkg: String, postedAt: Long) {
    thread {
      try {
        val body = JSONObject()
          .put("rawText", rawText)
          .put("packageName", pkg)
          .put("postedAt", postedAt)
          .toString()
        val conn = (URL(INGEST_URL).openConnection() as HttpURLConnection).apply {
          requestMethod = "POST"
          connectTimeout = 12000
          readTimeout = 12000
          doOutput = true
          setRequestProperty("Content-Type", "application/json")
          setRequestProperty("Authorization", "Bearer $token")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        conn.inputStream.use { it.readBytes() } // drenar
        conn.disconnect()
      } catch (_: Exception) {
        // Best-effort: si falla, la notificación se pierde (no reintenta para no spamear).
      }
    }
  }
}
