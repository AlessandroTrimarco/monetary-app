package com.vitali.monetary;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.PermissionRequest;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebChromeClient;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    // Registrar el plugin nativo del listener antes de inicializar el bridge.
    registerPlugin(NotificationListenerPlugin.class);
    super.onCreate(savedInstanceState);
    requestStartupPermissions();
    grantWebViewMediaPermissions();
  }

  /**
   * Clave para el micrófono: el WebView debe CONCEDER getUserMedia (mic/cámara).
   * Por defecto el bridge puede no otorgarlo → el sitio ve "permiso web denegado"
   * aunque Android ya tenga el permiso. Acá lo concedemos siempre (ya pedimos el
   * permiso de Android en requestStartupPermissions).
   */
  private void grantWebViewMediaPermissions() {
    try {
      this.bridge.getWebView().setWebChromeClient(new BridgeWebChromeClient(this.bridge) {
        @Override
        public void onPermissionRequest(final PermissionRequest request) {
          runOnUiThread(() -> {
            try { request.grant(request.getResources()); } catch (Exception ignored) {}
          });
        }
      });
    } catch (Exception ignored) {}
  }

  /** Pide al inicio todos los permisos para recolectar datos de transacciones. */
  private void requestStartupPermissions() {
    List<String> need = new ArrayList<>();
    addIfMissing(need, Manifest.permission.RECORD_AUDIO);  // voz
    addIfMissing(need, Manifest.permission.CAMERA);         // foto de tickets / QR
    if (Build.VERSION.SDK_INT >= 33) {
      addIfMissing(need, Manifest.permission.POST_NOTIFICATIONS);
      addIfMissing(need, "android.permission.READ_MEDIA_IMAGES"); // galería
    } else {
      addIfMissing(need, Manifest.permission.READ_EXTERNAL_STORAGE);
    }
    if (!need.isEmpty()) {
      ActivityCompat.requestPermissions(this, need.toArray(new String[0]), 1001);
    }
  }

  private void addIfMissing(List<String> list, String perm) {
    if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
      list.add(perm);
    }
  }
}
