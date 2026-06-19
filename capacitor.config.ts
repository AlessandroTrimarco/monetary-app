import type { CapacitorConfig } from "@capacitor/cli"

/**
 * Monetary Agent — APK nativa (Capacitor, modo híbrido).
 * Envuelve la web ya deployada (1 frontend → web + APK) y agrega capacidades nativas:
 * principalmente el NotificationListenerService para detectar ingresos/egresos de OTRAS apps.
 */
const config: CapacitorConfig = {
  appId: "com.vitali.monetary",
  appName: "Monetary Agent",
  // Modo híbrido: la APK carga el sitio en producción (no copiamos build de Next).
  // Para desarrollo apuntar a la IP local: server.url = "http://192.168.x.x:3000"
  webDir: "www",
  server: {
    url: "https://monetary-agent.vercel.app",
    cleartext: false,
    androidScheme: "https",
    // Ante un error de red al cargar la URL remota, mostrar nuestra pantalla branded
    // (www/error.html) que reintenta sola — en vez del feo error de Chrome.
    errorPath: "error.html",
  },
  android: {
    allowMixedContent: false,
  },
}

export default config
