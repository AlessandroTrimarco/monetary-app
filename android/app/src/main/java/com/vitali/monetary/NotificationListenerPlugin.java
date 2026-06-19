package com.vitali.monetary;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONException;

/**
 * Puente JS ↔ nativo para el listener de notificaciones. Desde la WebView:
 *   Capacitor.Plugins.NotificationListener.setToken({ token })
 *   Capacitor.Plugins.NotificationListener.setEnabled({ enabled, packages })
 *   Capacitor.Plugins.NotificationListener.isGranted()
 *   Capacitor.Plugins.NotificationListener.openSettings()
 */
@CapacitorPlugin(name = "NotificationListener")
public class NotificationListenerPlugin extends Plugin {

  private SharedPreferences prefs() {
    return getContext().getSharedPreferences(
        MonetaryNotificationListenerService.PREFS, Context.MODE_PRIVATE);
  }

  @PluginMethod
  public void setToken(PluginCall call) {
    String token = call.getString("token");
    prefs().edit().putString(MonetaryNotificationListenerService.KEY_TOKEN, token).apply();
    call.resolve();
  }

  @PluginMethod
  public void setEnabled(PluginCall call) {
    boolean enabled = Boolean.TRUE.equals(call.getBoolean("enabled", false));
    SharedPreferences.Editor e = prefs().edit();
    e.putBoolean(MonetaryNotificationListenerService.KEY_ENABLED, enabled);
    JSArray packages = call.getArray("packages");
    if (packages != null) {
      try {
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < packages.length(); i++) {
          if (i > 0) csv.append(",");
          csv.append(packages.getString(i));
        }
        e.putString(MonetaryNotificationListenerService.KEY_PACKAGES, csv.toString());
      } catch (JSONException ignored) {}
    }
    e.apply();
    call.resolve();
  }

  @PluginMethod
  public void isGranted(PluginCall call) {
    String flat = Settings.Secure.getString(
        getContext().getContentResolver(), "enabled_notification_listeners");
    boolean granted = flat != null && flat.contains(getContext().getPackageName());
    JSObject ret = new JSObject();
    ret.put("granted", granted);
    call.resolve(ret);
  }

  @PluginMethod
  public void openSettings(PluginCall call) {
    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    getContext().startActivity(intent);
    call.resolve();
  }
}
