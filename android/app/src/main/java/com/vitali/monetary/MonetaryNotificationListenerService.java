package com.vitali.monetary;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Captura notificaciones de apps de banco/billetera/MP y las reenvía al backend,
 * que las convierte en PendingTransaction (el usuario confirma en /pending).
 *
 * Corre en BACKGROUND (aún con la app cerrada) → NO tiene la cookie de la WebView.
 * Usa un DEVICE TOKEN (Authorization: Bearer) guardado en SharedPreferences por
 * NotificationListenerPlugin cuando el usuario activa la función.
 */
public class MonetaryNotificationListenerService extends NotificationListenerService {

  public static final String PREFS = "monetary_prefs";
  public static final String KEY_TOKEN = "device_token";
  public static final String KEY_ENABLED = "listener_enabled";
  public static final String KEY_PACKAGES = "listener_packages";
  public static final String INGEST_URL = "https://monetary-agent.vercel.app/api/ingest/notification";

  private static final Set<String> DEFAULT_PACKAGES = new HashSet<>(Arrays.asList(
      "com.mercadopago.wallet", "com.uala.android", "com.brubank.android",
      "ar.com.santander.rio.movilidad", "com.bancogalicia.movil", "com.todopago",
      "ar.bbva.net", "com.naranja"
  ));

  @Override
  public void onNotificationPosted(StatusBarNotification sbn) {
    SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    if (!prefs.getBoolean(KEY_ENABLED, false)) return;

    final String token = prefs.getString(KEY_TOKEN, null);
    if (token == null) return;

    final String pkg = sbn.getPackageName();
    if (pkg == null) return;

    Set<String> allowed = DEFAULT_PACKAGES;
    String csv = prefs.getString(KEY_PACKAGES, null);
    if (csv != null && !csv.trim().isEmpty()) {
      allowed = new HashSet<>();
      for (String p : csv.split(",")) { if (!p.trim().isEmpty()) allowed.add(p.trim()); }
    }
    if (!allowed.contains(pkg)) return;

    Notification n = sbn.getNotification();
    if (n == null || n.extras == null) return;
    Bundle extras = n.extras;
    CharSequence titleCs = extras.getCharSequence(Notification.EXTRA_TITLE);
    CharSequence textCs = extras.getCharSequence(Notification.EXTRA_TEXT);
    CharSequence bigCs = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
    String title = titleCs != null ? titleCs.toString() : "";
    String text = textCs != null ? textCs.toString() : "";
    String big = bigCs != null ? bigCs.toString() : "";
    String body = (big != null && !big.isEmpty()) ? big : text;
    StringBuilder raw = new StringBuilder();
    if (!title.isEmpty()) raw.append(title);
    if (!body.isEmpty()) { if (raw.length() > 0) raw.append(" — "); raw.append(body); }
    String rawText = raw.toString();
    if (rawText.length() > 1000) rawText = rawText.substring(0, 1000);
    if (rawText.length() < 3) return;

    postToBackend(token, rawText, pkg, sbn.getPostTime());
  }

  private void postToBackend(final String token, final String rawText, final String pkg, final long postedAt) {
    new Thread(new Runnable() {
      @Override public void run() {
        HttpURLConnection conn = null;
        try {
          JSONObject json = new JSONObject();
          json.put("rawText", rawText);
          json.put("packageName", pkg);
          json.put("postedAt", postedAt);
          byte[] payload = json.toString().getBytes("UTF-8");

          conn = (HttpURLConnection) new URL(INGEST_URL).openConnection();
          conn.setRequestMethod("POST");
          conn.setConnectTimeout(12000);
          conn.setReadTimeout(12000);
          conn.setDoOutput(true);
          conn.setRequestProperty("Content-Type", "application/json");
          conn.setRequestProperty("Authorization", "Bearer " + token);
          OutputStream os = conn.getOutputStream();
          os.write(payload);
          os.close();
          conn.getResponseCode(); // dispara el envío
        } catch (Exception ignored) {
          // best-effort: si falla, no reintenta (evita spamear)
        } finally {
          if (conn != null) conn.disconnect();
        }
      }
    }).start();
  }
}
