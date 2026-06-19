package com.vitali.monetary

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

/**
 * Puente JS ↔ nativo para el listener de notificaciones.
 * Desde la WebView (la web de Monetary) se llama:
 *   Capacitor.Plugins.NotificationListener.setToken({ token })
 *   Capacitor.Plugins.NotificationListener.setEnabled({ enabled, packages })
 *   Capacitor.Plugins.NotificationListener.isGranted()
 *   Capacitor.Plugins.NotificationListener.openSettings()   // abre "Acceso a notificaciones"
 */
@CapacitorPlugin(name = "NotificationListener")
class NotificationListenerPlugin : Plugin() {

  private fun prefs() =
    context.getSharedPreferences(MonetaryNotificationListenerService.PREFS, Context.MODE_PRIVATE)

  @PluginMethod
  fun setToken(call: PluginCall) {
    val token = call.getString("token")
    prefs().edit().putString(MonetaryNotificationListenerService.KEY_TOKEN, token).apply()
    call.resolve()
  }

  @PluginMethod
  fun setEnabled(call: PluginCall) {
    val enabled = call.getBoolean("enabled", false) ?: false
    val packages = call.getArray("packages")?.toList<String>()?.joinToString(",")
    prefs().edit().apply {
      putBoolean(MonetaryNotificationListenerService.KEY_ENABLED, enabled)
      if (packages != null) putString(MonetaryNotificationListenerService.KEY_PACKAGES, packages)
      apply()
    }
    call.resolve()
  }

  /** ¿El usuario otorgó "Acceso a notificaciones" a la app? */
  @PluginMethod
  fun isGranted(call: PluginCall) {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
    val granted = flat.contains(context.packageName)
    call.resolve(com.getcapacitor.JSObject().put("granted", granted))
  }

  /** Abre la pantalla de Special App Access para que el usuario habilite el listener. */
  @PluginMethod
  fun openSettings(call: PluginCall) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
    call.resolve()
  }
}
