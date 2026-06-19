# APK nativa Monetary Agent — Build Guide

APK Capacitor en **modo híbrido**: envuelve la web ya deployada (https://monetary-agent.vercel.app)
y agrega un **NotificationListenerService** que detecta ingresos/egresos de OTRAS apps (banco/MP/billeteras).

## Arquitectura
```
App bancaria → notificación → [MonetaryNotificationListenerService] (background, Kotlin)
  → POST /api/ingest/notification  (Authorization: Bearer <deviceToken>)
  → backend parsea monto/tipo/comercio → crea PendingTransaction (status PENDING)
  → usuario confirma en /pending → transacción real
```
**Por qué device token y no la cookie:** el listener corre en background (app cerrada) y NO
tiene la cookie de la WebView. La WebView setea un token (vía `NotificationListener.setToken`)
que el service guarda en SharedPreferences y usa en cada POST.

## Estado
- [x] `capacitor.config.ts` (híbrido, appId `com.vitali.monetary`)
- [x] `native/MonetaryNotificationListenerService.kt` — captura + parseo de paquetes + POST
- [x] `native/NotificationListenerPlugin.kt` — puente JS↔nativo (setToken/setEnabled/isGranted/openSettings)
- [x] `native/AndroidManifest-additions.xml` — registro del service
- [x] Backend `/api/ingest/notification` (parsea monto AR + tipo + dedup 10min → PendingTransaction) — DEPLOYADO
- [x] **BUILD OK**: `cap add android` + código nativo en **Java** + gradle `assembleDebug` → **`app-debug.apk` (3.75 MB)** en `android/app/build/outputs/apk/debug/`. BUILD SUCCESSFUL 4m9s.
- [x] **Backend OK + VERIFICADO e2e**: `/api/devices/token` (emite, tabla `DeviceToken`) + `/api/ingest/notification` acepta Bearer o sesión. Probado: $4.599 débito→EXPENSE, $50.000 acreditación→INCOME, token inválido→401.
- [ ] **PENDIENTE web**: pantalla en /settings para activar "Acceso a notificaciones" (si `Capacitor.isNativePlatform()`): pedir token a `/api/devices/token`, `NotificationListener.setToken`, `openSettings`, `setEnabled`, elegir apps. (Requiere agregar `@capacitor/core` al web app — no-op en web.)
- [ ] **PENDIENTE**: firma release (keystore) + test en device real.

## Pasos de build (local)
```bash
# Toolchain: Java 21 ✅, Android SDK en ~/AppData/Local/Android/Sdk
export ANDROID_HOME="$HOME/AppData/Local/Android/Sdk"   # o ANDROID_SDK_ROOT
cd hub/projects/monetary-agent-app
npm install
npx cap add android                      # genera android/
# Copiar los .kt a android/app/src/main/java/com/vitali/monetary/
mkdir -p android/app/src/main/java/com/vitali/monetary
cp native/*.kt android/app/src/main/java/com/vitali/monetary/
# Pegar el bloque <service> de native/AndroidManifest-additions.xml dentro de <application>
# en android/app/src/main/AndroidManifest.xml
npx cap sync android
cd android && ./gradlew assembleDebug    # APK en app/build/outputs/apk/debug/app-debug.apk
```

## Firma / release (futuro)
- Generar keystore, configurar `signingConfigs` en `android/app/build.gradle`, `./gradlew assembleRelease`.
- O CI: GitHub Actions / Codemagic (ver patrón del proyecto Fichero).

## Seguridad (cuida al usuario)
- El listener NO crea transacciones: solo PendingTransaction que el usuario confirma.
- El usuario elige qué apps escuchar y puede revocar el "Acceso a notificaciones" cuando quiera.
- El device token debe poder revocarse (tabla DeviceToken con `revokedAt`).
